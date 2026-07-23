package com.arturo254.opentune.lyricsify

import timber.log.Timber
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object Lyricsify {
    private const val TAG = "Lyricsify"

    private val client = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 12000
            connectTimeoutMillis = 8000
        }
    }
    private const val BASE_URL = "https://www.lyricsify.com"

    private val LRC_LINE_REGEX = "\\[\\d\\d:\\d\\d\\.\\d{2,3}\\].*".toRegex()
    private val WORD_TIME_REGEX = "<\\d\\d:\\d\\d\\.\\d{2,3}>".toRegex()
    private val SONG_URL_REGEX = "/lyrics/[^/\"'\\s]+/[^/\"'\\s]+/?$".toRegex()
    private val BR_TAG_REGEX = "(?i)<br\\s*/?>".toRegex()
    private val BLOCK_CLOSE_TAG_REGEX = "(?i)</(p|div)>".toRegex()
    private val HTML_TAG_REGEX = "<[^>]+>".toRegex()

    private suspend fun fetch(url: String): String {
        val response: HttpResponse = client.get(url) {
            headers {
                append("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                append("Accept", "text/html,application/xhtml+xml")
                append("Accept-Language", "en-US,en;q=0.9")
            }
        }
        val body = response.bodyAsText()
        Timber.tag(TAG).d("GET $url -> ${response.status.value}, ${body.length} chars")
        if (!response.status.isSuccess()) {
            throw Exception("HTTP ${response.status.value} fetching $url")
        }
        return body
    }

    private fun normalize(text: String) =
        text.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun scoreMatch(query: String, candidate: String): Int {
        val queryTokens = normalize(query).split(" ").filter { it.isNotBlank() }.toSet()
        val candidateTokens = normalize(candidate).split(" ").filter { it.isNotBlank() }.toSet()
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return 0
        return queryTokens.intersect(candidateTokens).size
    }

    private fun extractCandidates(html: String, query: String): List<Pair<String, Int>> {
        val document = Jsoup.parse(html)
        val links = document.select("a[href]")
        Timber.tag(TAG).d("Search page has ${links.size} links total")
        val result = links
            .map { it.attr("abs:href") to it.text() }
            .filter { (href, _) -> SONG_URL_REGEX.containsMatchIn(href) }
            .distinctBy { it.first }
            .map { (href, text) -> href to scoreMatch(query, text) }
        Timber.tag(TAG).d("Search page has ${result.size} song-link candidates: ${result.take(5)}")
        return result
    }

    private suspend fun findSongUrl(title: String, artist: String): String? {
        val query = "$artist $title"
        Timber.tag(TAG).d("Searching Lyricsify for: $query")

        val byQ = runCatching {
            extractCandidates(fetch("$BASE_URL/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"), query)
        }.onFailure {
            Timber.tag(TAG).w("search?q= failed: ${it.message}")
        }.getOrDefault(emptyList())

        val best = byQ.maxByOrNull { it.second }
        if (best != null && best.second > 0) {
            Timber.tag(TAG).d("Picked via ?q=: ${best.first} (score ${best.second})")
            return best.first
        }

        val byFields = runCatching {
            val url = "$BASE_URL/search?artist=${java.net.URLEncoder.encode(artist, "UTF-8")}&title=${java.net.URLEncoder.encode(title, "UTF-8")}"
            extractCandidates(fetch(url), query)
        }.onFailure {
            Timber.tag(TAG).w("search?artist&title= failed: ${it.message}")
        }.getOrDefault(emptyList())

        val chosen = byFields.maxByOrNull { it.second }?.first ?: byQ.firstOrNull()?.first
        Timber.tag(TAG).d("Final chosen song URL: $chosen")
        return chosen
    }

    private fun htmlToLines(html: String): List<String> {
        val withBreaks = html
            .replace(BR_TAG_REGEX, "\n")
            .replace(BLOCK_CLOSE_TAG_REGEX, "\n")
        val stripped = HTML_TAG_REGEX.replace(withBreaks, "")
        val decoded = org.jsoup.parser.Parser.unescapeEntities(stripped, false)
        return decoded.lines().map { it.trim() }
    }

    private fun blockFromHtml(html: String): List<String> {
        val lines = htmlToLines(html).filter { it.isNotBlank() }
        return lines.filter { LRC_LINE_REGEX.matches(it) }
    }

    private fun extractLrcContent(html: String): String? {
        val document = Jsoup.parse(html)

        val downloadLink = document.select("a[href]")
            .map { it.attr("abs:href") }
            .firstOrNull { it.endsWith(".lrc", ignoreCase = true) }
        if (downloadLink != null) {
            Timber.tag(TAG).d("Found .lrc download link: $downloadLink")
            return downloadLink
        }

        val blocks = mutableListOf<List<String>>()

        document.select("textarea").forEach { el ->
            val raw = el.data().ifBlank { el.text() }
            val lines = raw.lines().map { it.trim() }.filter { LRC_LINE_REGEX.matches(it) }
            if (lines.size >= 2) blocks += lines
        }

        document.select("div, pre, code").forEach { el ->
            if (LRC_LINE_REGEX.containsMatchIn(el.html())) {
                val lines = blockFromHtml(el.html())
                if (lines.size >= 2) blocks += lines
            }
        }

        if (blocks.isEmpty()) {
            val bodyLines = blockFromHtml(document.body().html())
            if (bodyLines.size >= 2) blocks += bodyLines
        }

        Timber.tag(TAG).d("Found ${blocks.size} LRC-looking blocks on song page, sizes: ${blocks.map { it.size }}")

        if (blocks.isEmpty()) return null

        val enhanced = blocks
            .filter { block -> block.any { WORD_TIME_REGEX.containsMatchIn(it) } }
            .maxByOrNull { it.size }

        val chosen = enhanced ?: blocks.maxByOrNull { it.size }

        Timber.tag(TAG).d(if (enhanced != null) "Using ENHANCED block (${enhanced.size} lines)" else "No enhanced block found, using plain block (${chosen?.size} lines)")

        return chosen?.joinToString("\n")
    }

    suspend fun getLyrics(title: String, artist: String): Result<String> = runCatching {
        val songUrl = findSongUrl(title, artist) ?: throw Exception("Song not found on Lyricsify")
        val songHtml = fetch(songUrl)
        val extracted = extractLrcContent(songHtml) ?: throw Exception("No LRC content found on Lyricsify")
        if (extracted.startsWith("http")) {
            val lrcFile = fetch(extracted)
            if (lrcFile.isBlank()) throw Exception("Empty LRC file from Lyricsify")
            lrcFile
        } else {
            extracted
        }
    }.onFailure {
        Timber.tag(TAG).e("getLyrics failed for '$title' by '$artist': ${it.message}")
    }
}
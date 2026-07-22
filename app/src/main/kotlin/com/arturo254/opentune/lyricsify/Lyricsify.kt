package com.arturo254.opentune.lyricsify

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object Lyricsify {
    private val client = HttpClient()
    private const val BASE_URL = "https://www.lyricsify.com"

    private val LRC_LINE_REGEX = "\\[\\d\\d:\\d\\d\\.\\d{2,3}\\].*".toRegex()

    private suspend fun fetch(url: String): String = client.get(url) {
        headers {
            append("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
        }
    }.bodyAsText()

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

    private suspend fun findSongUrl(title: String, artist: String): String? {
        val query = "$artist $title"
        val html = fetch("$BASE_URL/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
        val document: Document = Jsoup.parse(html)
        val candidates = document.select("a[href]")
            .map { it.attr("abs:href") to it.text() }
            .filter { (href, _) -> href.contains("/lyric/", ignoreCase = true) }
            .distinctBy { it.first }
        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull { (_, text) -> scoreMatch(query, text) }?.first
    }

    private fun extractLrcContent(html: String): String? {
        val document = Jsoup.parse(html)
        val downloadLink = document.select("a[href*=download]")
            .map { it.attr("abs:href") }
            .firstOrNull { it.endsWith(".lrc", ignoreCase = true) }
        if (downloadLink != null) return downloadLink

        val bodyText = document.text()
        val lines = bodyText.lines().filter { LRC_LINE_REGEX.matches(it.trim()) }
        return if (lines.isNotEmpty()) lines.joinToString("\n") else null
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
    }
}

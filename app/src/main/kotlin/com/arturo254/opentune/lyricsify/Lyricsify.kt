package com.arturo254.opentune.lyricsify

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.jsoup.Jsoup
import timber.log.Timber
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

object Lyricsify {
    private const val TAG = "Lyricsify"
    private const val BASE_URL = "https://www.lyricsify.com"
    private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val LRC_LINE_REGEX = "\\[\\d\\d:\\d\\d\\.\\d{2,3}\\].*".toRegex()
    private val WORD_TIME_REGEX = "<\\d\\d:\\d\\d\\.\\d{2,3}>".toRegex()
    private val SONG_URL_REGEX = "/lyrics/[^/\"'\\s]+/[^/\"'\\s]+/?$".toRegex()
    private val BR_TAG_REGEX = "(?i)<br\\s*/?>".toRegex()
    private val BLOCK_CLOSE_TAG_REGEX = "(?i)</(p|div)>".toRegex()
    private val HTML_TAG_REGEX = "<[^>]+>".toRegex()

    private const val MAX_LYRICS_POLL_ATTEMPTS = 12
    private const val LYRICS_POLL_INTERVAL_MS = 700L

    private const val SELECT_ENHANCED_MODE_JS = """
        (function(){
            var radio = document.getElementById('radio_word');
            if (radio) {
                radio.checked = true;
                radio.dispatchEvent(new Event('input', {bubbles:true}));
                radio.dispatchEvent(new Event('change', {bubbles:true}));
                radio.dispatchEvent(new Event('click', {bubbles:true}));
            }
            return true;
        })()
    """

    private const val READ_LYRICS_DISPLAY_JS = """
        (function(){
            var el = document.getElementById('lyrics_display');
            return el ? el.innerText : '';
        })()
    """

    private var appContext: Context? = null
    private var activityRef: WeakReference<Activity>? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val app = context.applicationContext as? Application ?: return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                activityRef = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (activityRef?.get() === activity) activityRef = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchLyricsText(url: String): String? = withContext(Dispatchers.Main) {
        val context = appContext ?: throw Exception("Lyricsify.init(context) was never called")
        val activity = activityRef?.get()
        val host = activity?.window?.decorView as? ViewGroup

        val result = withTimeoutOrNull(25000) {
            suspendCancellableCoroutine<String?> { continuation ->
                val webView = WebView(activity ?: context)
                var resumed = false
                var attempts = 0
                var bestSoFar: String? = null

                fun finishWith(result: String?) {
                    if (resumed) return
                    resumed = true
                    webView.stopLoading()
                    host?.removeView(webView)
                    webView.destroy()
                    if (continuation.isActive) continuation.resume(result)
                }

                fun readLyricsDisplay() {
                    webView.evaluateJavascript(READ_LYRICS_DISPLAY_JS) { raw ->
                        val decoded = runCatching {
                            JSONArray("[$raw]").getString(0)
                        }.getOrDefault("")

                        if (decoded.isNotBlank()) bestSoFar = decoded

                        val isEnhanced = decoded.isNotBlank() && WORD_TIME_REGEX.containsMatchIn(decoded)
                        attempts++

                        if (isEnhanced || attempts >= MAX_LYRICS_POLL_ATTEMPTS) {
                            finishWith(bestSoFar)
                        } else {
                            webView.postDelayed({ readLyricsDisplay() }, LYRICS_POLL_INTERVAL_MS)
                        }
                    }
                }

                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.userAgentString = MOBILE_UA

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        view.postDelayed({
                            view.evaluateJavascript(SELECT_ENHANCED_MODE_JS) {
                                view.postDelayed({ readLyricsDisplay() }, LYRICS_POLL_INTERVAL_MS)
                            }
                        }, 1200)
                    }
                }

                continuation.invokeOnCancellation {
                    webView.stopLoading()
                    host?.removeView(webView)
                    webView.destroy()
                }

                val width = dpToPx(context, 360)
                val height = dpToPx(context, 640)
                webView.alpha = 0f

                if (host != null) {
                    webView.layoutParams = ViewGroup.LayoutParams(width, height)
                    host.addView(webView)
                } else {
                    webView.layout(0, 0, width, height)
                }

                webView.loadUrl(url)
            }
        }

        Timber.tag(TAG).d("Lyrics page fetch $url -> ${result?.length ?: 0} chars")
        result
    }

    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.Main) {
        val context = appContext ?: throw Exception("Lyricsify.init(context) was never called")
        val activity = activityRef?.get()
        val host = activity?.window?.decorView as? ViewGroup

        val html = withTimeoutOrNull(20000) {
            suspendCancellableCoroutine<String> { continuation ->
                val webView = WebView(activity ?: context)
                var resumed = false

                fun finishWith(result: String) {
                    if (resumed) return
                    resumed = true
                    webView.stopLoading()
                    host?.removeView(webView)
                    webView.destroy()
                    if (continuation.isActive) continuation.resume(result)
                }

                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.userAgentString = MOBILE_UA

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        view.postDelayed({
                            view.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                                val decoded = runCatching {
                                    JSONArray("[$raw]").getString(0)
                                }.getOrDefault("")
                                finishWith(decoded)
                            }
                        }, 1800)
                    }
                }

                continuation.invokeOnCancellation {
                    webView.stopLoading()
                    host?.removeView(webView)
                    webView.destroy()
                }

                val width = dpToPx(context, 360)
                val height = dpToPx(context, 640)
                webView.alpha = 0f

                if (host != null) {
                    webView.layoutParams = ViewGroup.LayoutParams(width, height)
                    host.addView(webView)
                } else {
                    webView.layout(0, 0, width, height)
                }

                webView.loadUrl(url)
            }
        }

        Timber.tag(TAG).d("WebView fetch $url -> ${html?.length ?: 0} chars")
        html ?: throw Exception("Timed out loading $url in WebView")
    }

    private fun normalize(text: String) =
        text.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun slugify(text: String): String {
        val stripped = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace("\\p{M}".toRegex(), "")
        return stripped.lowercase()
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
    }

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
            extractCandidates(fetchHtml("$BASE_URL/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"), query)
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
            extractCandidates(fetchHtml(url), query)
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
        val directUrl = "$BASE_URL/lyrics/${slugify(artist)}/${slugify(title)}"

        var songUrl = directUrl
        var extracted = runCatching { fetchLyricsText(directUrl) }.getOrNull()

        if (extracted == null) {
            extracted = runCatching { extractLrcContent(fetchHtml(directUrl)) }.getOrNull()
        }

        if (extracted == null) {
            Timber.tag(TAG).d("Slug URL had no usable content, falling back to search")
            val foundUrl = findSongUrl(title, artist) ?: throw Exception("Song not found on Lyricsify")
            songUrl = foundUrl
            extracted = runCatching { fetchLyricsText(foundUrl) }.getOrNull()
                ?: extractLrcContent(fetchHtml(foundUrl))
                        ?: throw Exception("No LRC content found on Lyricsify")
        }

        Timber.tag(TAG).d("Using song page: $songUrl, isEnhanced=${WORD_TIME_REGEX.containsMatchIn(extracted)}")

        if (extracted.startsWith("http")) {
            val lrcHtml = fetchHtml(extracted)
            val lrcBody = Jsoup.parse(lrcHtml).body().text()
            if (lrcBody.isBlank()) throw Exception("Empty LRC file from Lyricsify")
            lrcBody
        } else {
            extracted
        }
    }.onFailure {
        Timber.tag(TAG).e("getLyrics failed for '$title' by '$artist': ${it.message}")
    }
}
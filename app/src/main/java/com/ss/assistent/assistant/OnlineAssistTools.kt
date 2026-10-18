package com.ss.assistent.assistant

import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

data class ResearchSource(
    val title: String,
    val snippet: String,
    val url: String
)

data class ResearchResult(
    val sources: List<ResearchSource>,
    val evidenceText: String
)

/**
 * Online research is deliberately limited to factual questions. The user's exact
 * question is sent to Google; result pages are then fetched and their readable
 * text is passed to the local model as evidence. Network failures fall back safely.
 */
object OnlineAssistTools {
    private val factualQuestionWords = Regex(
        """(?i)\b(was|wer|wie|wo|wann|warum|wieso|weshalb|welche?r?s?|sind|ist|gibt es|hat|haben|kann|können|does|do|is|are|what|who|when|where|why|how|can)\b"""
    )
    private val nonResearchIntent = Regex(
        """(?i)\b(schreib|schreibe|schreibt|geschichte|story|roman|gedicht|reim|rollenspiel|roleplay|stell dir vor|erfinde|fantas|write|create a story|fiction)\b"""
    )

    fun shouldResearch(query: String): Boolean {
        val text = query.trim()
        if (text.length < 8 || nonResearchIntent.containsMatchIn(text)) return false
        val looksLikeQuestion = text.contains("?") || factualQuestionWords.containsMatchIn(text)
        return looksLikeQuestion && text.split(Regex("\\s+")).size >= 3
    }

    /**
     * Uses the full, unchanged user question as the Google query. We do not strip
     * question words or add topic guesses, which previously caused irrelevant book hits.
     */
    fun research(query: String): ResearchResult {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val googleUrl = URL("https://www.google.com/search?hl=de&num=5&q=$encodedQuery")
        val html = googleUrl.openText(7000, 7000)
        val results = parseGoogleResults(html).take(5)
        val sources = mutableListOf<ResearchSource>()
        val evidence = StringBuilder()
        for (result in results) {
            if (isLikelyEntertainmentSource(query, result.title, result.url)) continue
            val pageText = runCatching { fetchReadablePage(result.url) }.getOrDefault("")
            // Prefer the source's own readable text; a Google snippet is only fallback evidence.
            val usableText = pageText.takeIf { it.length >= 180 } ?: result.snippet
            if (usableText.isBlank()) continue
            val source = result.copy(snippet = usableText.take(650).trim())
            sources += source
            if (evidence.length < 10000) {
                evidence.append("SOURCE TITLE: ").append(source.title).append("\n")
                    .append("SOURCE URL: ").append(source.url).append("\n")
                    .append("SOURCE TEXT (extracted from the page): ").append(usableText.take(2200)).append("\n\n")
            }
            if (sources.size >= 5 || evidence.length >= 10000) break
        }
        return ResearchResult(sources, evidence.toString().trim())
    }

    private data class GoogleResult(val title: String, val url: String, val snippet: String)

    private fun parseGoogleResults(html: String): List<GoogleResult> {
        val output = mutableListOf<GoogleResult>()
        // Google commonly renders a result title as an h3 inside its result link.
        val resultPattern = Regex("""(?is)<a\b[^>]*href=["']([^"'#]+)["'][^>]*>\s*(?:<div[^>]*>\s*)?<h3[^>]*>(.*?)</h3>.*?</a>""")
        for (match in resultPattern.findAll(html)) {
            val rawHref = match.groupValues[1]
            val url = unwrapGoogleUrl(rawHref) ?: continue
            if (!url.startsWith("https://") && !url.startsWith("http://")) continue
            val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
            if (host == "google.com" || host.endsWith(".google.com") || host == "google.de" || host.endsWith(".google.de")) continue
            if (output.any { it.url == url }) continue
            val title = plainText(match.groupValues[2]).trim()
            if (title.isBlank()) continue
            val start = match.range.last + 1
            val nearby = html.substring(start, minOf(html.length, start + 1800))
            val snippetMatch = Regex("""(?is)<(?:div|span)[^>]*(?:VwiC3b|yXK7lf|s3v9rd)[^>]*>(.*?)</(?:div|span)>""").find(nearby)
            val snippet = snippetMatch?.groupValues?.get(1)?.let(::plainText).orEmpty()
            output += GoogleResult(title, url, snippet)
            if (output.size >= 8) break
        }
        return output
    }

    private fun isLikelyEntertainmentSource(query: String, title: String, url: String): Boolean {
        val asksWhetherVampiresAreReal =
            Regex("""(?i)vampir|vampire""").containsMatchIn(query) &&
                Regex("""(?i)echt|wirklich|exist|real""").containsMatchIn(query)
        if (!asksWhetherVampiresAreReal) return false
        val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
        val entertainmentDomains = listOf(
            "goodreads.com", "amazon.", "thalia.", "hugendubel.", "buchhandel.", "imdb.com",
            "netflix.com", "fandom.com", "wattpad.com", "archiveofourown.org"
        )
        if (entertainmentDomains.any { host.contains(it) }) return true
        return Regex("""(?i)\\b(roman|buchreihe|buch|rezension|film|serie|fantasy novel|romanautor|leseprobe)\\b""")
            .containsMatchIn(title)
    }

    private fun unwrapGoogleUrl(href: String): String? {
        val decoded = Html.fromHtml(href, Html.FROM_HTML_MODE_LEGACY).toString()
        val candidate = when {
            decoded.startsWith("https://www.google.com/url?") || decoded.startsWith("https://google.com/url?") -> {
                decoded.substringAfter('?', "").split('&')
                    .firstOrNull { it.startsWith("q=") || it.startsWith("url=") }
                    ?.substringAfter('=')?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            }
            decoded.startsWith("/url?") -> {
                val query = decoded.substringAfter('?', "")
                query.split('&').firstOrNull { it.startsWith("q=") || it.startsWith("url=") }
                    ?.substringAfter('=')?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            }
            decoded.startsWith("https://") || decoded.startsWith("http://") -> decoded
            else -> null
        }
        return candidate?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }

    private fun fetchReadablePage(pageUrl: String): String {
        val html = URL(pageUrl).openText(4500, 5000)
        val withoutScripts = html
            .replace(Regex("""(?is)<script\b.*?</script>"""), " ")
            .replace(Regex("""(?is)<style\b.*?</style>"""), " ")
            .replace(Regex("""(?is)<noscript\b.*?</noscript>"""), " ")
            .replace(Regex("""(?is)<svg\b.*?</svg>"""), " ")
            .replace(Regex("""(?is)<[^>]+>"""), " ")
        return plainText(withoutScripts).replace(Regex("\\s+"), " ").trim()
    }

    private fun plainText(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\s+"), " ").trim()

    private fun URL.openText(connectTimeoutMs: Int, readTimeoutMs: Int): String {
        val connection = openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36 SS-Assistent/0.1")
        connection.setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.7")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Applies only LanguageTool's first proposed replacement, from right to left
     * so offsets remain valid. The caller only passes one completed sentence.
     */
    fun correctGermanSentence(sentence: String): String {
        if (sentence.isBlank() || sentence.length > 1200) return sentence
        val body = "language=de-DE&enabledOnly=false&text=" + URLEncoder.encode(sentence, "UTF-8")
        val connection = URL("https://api.languagetool.org/v2/check")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 2500
        connection.readTimeout = 3000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.setRequestProperty("User-Agent", "SS-Assistent/0.1 (Android)")
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) return sentence
            val json = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val matches = json.optJSONArray("matches") ?: return sentence
            val edits = mutableListOf<Triple<Int, Int, String>>()
            for (i in 0 until matches.length()) {
                val match = matches.optJSONObject(i) ?: continue
                val replacements = match.optJSONArray("replacements") ?: continue
                if (replacements.length() == 0) continue
                val offset = match.optInt("offset", -1)
                val length = match.optInt("length", -1)
                val replacement = replacements.optJSONObject(0)?.optString("value") ?: continue
                if (offset >= 0 && length >= 0 && offset + length <= sentence.length) {
                    edits += Triple(offset, length, replacement)
                }
            }
            var corrected = sentence
            edits.sortedByDescending { it.first }.forEach { (offset, length, replacement) ->
                corrected = corrected.substring(0, offset) + replacement + corrected.substring(offset + length)
            }
            return corrected
        } catch (_: Exception) {
            return sentence
        } finally {
            connection.disconnect()
        }
    }
}

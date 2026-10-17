package com.ss.assistent.assistant

import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
 * Optional online helpers used by normal chat. Only the current user query and
 * completed generated sentences are sent; requests use short timeouts.
 */
object OnlineAssistTools {
    private val factualQuestionWords = Regex(
        """(?i)\b(was|wer|wie|wo|wann|warum|wieso|weshalb|welche?r?s?|sind|ist|gibt es|hat|haben|kann|können|does|do|is|are|what|who|when|where|why|how|can)\b"""
    )
    private val nonResearchIntent = Regex(
        """(?i)\b(schreib|schreibe|schreibt|geschichte|story|roman|gedicht|reim|rollenspiel|roleplay|stell dir vor|erfinde|fantas|write|create a story|fiction)\b"""
    )
    private val stopWords = setOf(
        "was", "wer", "wie", "wo", "wann", "warum", "wieso", "weshalb", "sind", "ist",
        "echt", "wirklich", "gibt", "es", "hat", "haben", "kann", "können", "der", "die",
        "das", "ein", "eine", "einer", "einem", "einen", "und", "oder", "aber", "von",
        "mit", "im", "in", "am", "an", "zu", "auf", "für", "the", "are", "is", "do",
        "does", "what", "who", "when", "where", "why", "how", "can", "real", "really"
    )

    fun shouldResearch(query: String): Boolean {
        val text = query.trim()
        if (text.length < 8 || nonResearchIntent.containsMatchIn(text)) return false
        val looksLikeQuestion = text.contains("?") || factualQuestionWords.containsMatchIn(text)
        return looksLikeQuestion && text.split(Regex("\\s+")).size >= 3
    }

    fun research(query: String): ResearchResult {
        val terms = query.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in stopWords }
            .joinToString(" ")
            .ifBlank { query.trim() }
        val searchTerms = if (Regex("(?i)vampir|vampire").containsMatchIn(terms)) "$terms folklore Mythologie -Roman -Buch -Film" else terms
        val encoded = URLEncoder.encode(searchTerms, "UTF-8")
        val endpoint = URL(
            "https://de.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&srlimit=5&utf8=1"
        )
        val json = endpoint.openJson()
        val results = json.optJSONObject("query")?.optJSONArray("search")
        val sources = mutableListOf<ResearchSource>()
        val evidence = StringBuilder()
        if (results != null) {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val title = item.optString("title", "").trim()
                if (title.isBlank()) continue
                val snippet = Html.fromHtml(
                    item.optString("snippet", ""),
                    Html.FROM_HTML_MODE_LEGACY
                ).toString().replace(Regex("\\s+"), " ").trim()
                val pageUrl = "https://de.wikipedia.org/wiki/" +
                    URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")
                sources += ResearchSource(title, snippet, pageUrl)
                if (evidence.length < 6500) {
                    evidence.append("Source: ").append(title).append("\n")
                        .append(snippet).append("\nURL: ").append(pageUrl).append("\n\n")
                }
            }
        }
        return ResearchResult(sources, evidence.toString().trim())
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

    private fun URL.openJson(): JSONObject {
        val connection = openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3500
        connection.readTimeout = 4500
        connection.setRequestProperty("User-Agent", "SS-Assistent/0.1 (Android)")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Wikipedia returned HTTP $code")
            return JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}

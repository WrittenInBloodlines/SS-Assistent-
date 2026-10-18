package com.ss.assistent.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.Html
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class WebSource(val title: String, val snippet: String, val url: String)
private data class SpellingIssue(
    val message: String,
    val offset: Int,
    val length: Int,
    val suggestions: List<String>
)

/**
 * Small, optional online tools. Requests run off the UI thread and have short timeouts.
 * Source search is deliberately presented as evidence to inspect, not as an automatic truth verdict.
 */
@Composable
fun WebToolsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var spellingText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready. Online checks run only when you tap a button.") }
    var busy by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("sources") }
    val sources = remember { mutableStateListOf<WebSource>() }
    val issues = remember { mutableStateListOf<SpellingIssue>() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 28.dp, start = 20.dp, end = 20.dp, bottom = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, enabled = !busy) { Text("Back") }
                Column(Modifier.weight(1f)) {
                    Text("Web & language tools", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Optional online checks", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(13.dp)) {
                    Text(if (busy) "Working…" else "Status", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(status, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { mode = "sources" }, enabled = !busy) { Text("Sources") }
                OutlinedButton(onClick = { mode = "spelling" }, enabled = !busy) { Text("Spelling") }
            }
            Spacer(Modifier.height(8.dp))

            if (mode == "sources") {
                Text("Search Wikipedia for source material about a claim or topic.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(300) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    label = { Text("Claim or topic") },
                    placeholder = { Text("e.g. Are bats mammals?") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val text = query.trim()
                        if (text.isNotEmpty() && !busy) {
                            busy = true
                            sources.clear()
                            status = "Searching Wikipedia for relevant pages…"
                            scope.launch {
                                try {
                                    val found = withContext(Dispatchers.IO) { searchWikipedia(text) }
                                    sources.addAll(found)
                                    status = if (found.isEmpty()) {
                                        "No relevant pages found. Try a shorter or more specific search."
                                    } else {
                                        "Found ${found.size} source page(s). Open them and compare the passages with the claim."
                                    }
                                } catch (error: Exception) {
                                    status = "Source search failed: ${error.message ?: "network error"}. Check your connection and retry."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy && query.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Searching…" else "Search sources") }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Important: these are relevant sources, not an automatic truth verdict. Wikipedia coverage can be incomplete.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(sources) { source ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(13.dp)) {
                                Text(source.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(source.snippet, fontSize = 13.sp, lineHeight = 18.sp)
                                Spacer(Modifier.height(6.dp))
                                OutlinedButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                                }) { Text("Open source") }
                            }
                        }
                    }
                }
            } else {
                Text(
                    "German spelling, grammar and punctuation suggestions. The text is sent to LanguageTool's public service only when you tap Check.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = spellingText,
                    onValueChange = { spellingText = it.take(3000) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    label = { Text("Text to check") },
                    placeholder = { Text("Paste a sentence here…") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val text = spellingText.trim()
                        if (text.isNotEmpty() && !busy) {
                            busy = true
                            issues.clear()
                            status = "Sending text to LanguageTool and checking spelling…"
                            scope.launch {
                                try {
                                    val found = withContext(Dispatchers.IO) { checkGermanSpelling(text) }
                                    issues.addAll(found)
                                    status = if (found.isEmpty()) {
                                        "No issues were flagged. This does not guarantee that every sentence is correct."
                                    } else {
                                        "Found ${found.size} possible issue(s). Review the suggestions below."
                                    }
                                } catch (error: Exception) {
                                    status = "Spelling check failed: ${error.message ?: "network error"}. Check your connection and retry."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy && spellingText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Checking…" else "Check spelling") }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(issues) { issue ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(issue.message, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                val original = spellingText.substring(
                                    issue.offset.coerceIn(0, spellingText.length),
                                    (issue.offset + issue.length).coerceIn(0, spellingText.length)
                                )
                                Text("Text: “$original”", fontSize = 12.sp)
                                if (issue.suggestions.isNotEmpty()) {
                                    Text("Suggestions: ${issue.suggestions.joinToString(", ")}", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Privacy: only the text you enter here is sent for spelling analysis. Do not paste passwords or private conversations.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

private fun searchWikipedia(query: String): List<WebSource> {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val url = URL("https://de.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&srlimit=5")
    val json = url.openJsonConnection("GET")
    val results = json.getJSONObject("query").getJSONArray("search")
    return (0 until results.length()).map { index ->
        val item = results.getJSONObject(index)
        val title = item.optString("title", "Untitled")
        val snippet = Html.fromHtml(item.optString("snippet", ""), Html.FROM_HTML_MODE_LEGACY).toString()
        val page = "https://de.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")
        WebSource(title, snippet, page)
    }
}

private fun checkGermanSpelling(text: String): List<SpellingIssue> {
    val body = "language=de-DE&enabledOnly=false&text=" + URLEncoder.encode(text, "UTF-8")
    val connection = URL("https://api.languagetool.org/v2/check").openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 4500
    connection.readTimeout = 6000
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
    connection.setRequestProperty("User-Agent", "SS-Assistent/0.1")
    try {
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        if (code !in 200..299) throw IllegalStateException("service returned HTTP $code")
        val json = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        val matches = json.optJSONArray("matches") ?: return emptyList()
        return (0 until matches.length()).map { index ->
            val match = matches.getJSONObject(index)
            val replacements = match.optJSONArray("replacements")
            val suggestions = if (replacements == null) emptyList() else
                (0 until minOf(3, replacements.length())).map { replacements.getJSONObject(it).optString("value") }
            SpellingIssue(
                message = match.optJSONObject("message")?.toString() ?: match.optString("message", "Possible language issue"),
                offset = match.optInt("offset", 0),
                length = match.optInt("length", 0),
                suggestions = suggestions
            )
        }
    } finally {
        connection.disconnect()
    }
}

private fun URL.openJsonConnection(method: String): JSONObject {
    val connection = openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = 4500
    connection.readTimeout = 6000
    connection.setRequestProperty("User-Agent", "SS-Assistent/0.1 (Android)")
    try {
        val code = connection.responseCode
        if (code !in 200..299) throw IllegalStateException("service returned HTTP $code")
        return JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    } finally {
        connection.disconnect()
    }
}

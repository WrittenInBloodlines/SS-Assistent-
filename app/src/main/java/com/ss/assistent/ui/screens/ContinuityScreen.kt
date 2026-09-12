package com.ss.assistent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.ss.assistent.continuity.ContinuityAnalyzer
import com.ss.assistent.continuity.ContinuityRepository
import com.ss.assistent.continuity.ContinuityWarning
import com.ss.assistent.continuity.WarningState
import com.ss.assistent.continuity.WarningType

@Composable
fun ContinuityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ContinuityRepository(context) }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready to check your story.") }
    var loreFacts by remember { mutableStateOf(repository.getLoreFacts()) }
    var secrets by remember { mutableStateOf(repository.getSecrets()) }
    var warnings by remember { mutableStateOf(repository.getWarnings(includeClosed = false)) }
    var subject by remember { mutableStateOf("") }
    var attribute by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var secretSubject by remember { mutableStateOf("") }
    var secretText by remember { mutableStateOf("") }
    var knownBy by remember { mutableStateOf("") }
    var changeWarning by remember { mutableStateOf<ContinuityWarning?>(null) }
    var newCanonValue by remember { mutableStateOf("") }

    fun refresh() {
        loreFacts = repository.getLoreFacts()
        secrets = repository.getSecrets()
        warnings = repository.getWarnings(includeClosed = false)
    }

    changeWarning?.let { warning ->
        val fact = loreFacts.firstOrNull { warning.details.contains(it.subject) && warning.details.contains(it.attribute) }
        AlertDialog(
            onDismissRequest = { changeWarning = null },
            title = { Text("Change canon?") },
            text = {
                Column {
                    Text("This changes the stored canon. It will not happen silently.", fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newCanonValue,
                        onValueChange = { newCanonValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New canon value") }
                    )
                    if (fact != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Current: ${fact.value}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (fact != null && newCanonValue.isNotBlank()) {
                        repository.replaceLoreFact(fact.id, newCanonValue)
                        repository.setWarningState(warning.id, WarningState.RESOLVED)
                        changeWarning = null
                        newCanonValue = ""
                        refresh()
                        status = "Canon changed explicitly."
                    }
                }) { Text("Change") }
            },
            dismissButton = { TextButton(onClick = { changeWarning = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Story Continuity", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("Canon, secrets, and continuity checks", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Continuity checker", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Paste a scene or chapter here. Checks run locally and produce warnings only. Canon is never overwritten automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 7,
                            label = { Text("Story draft") }
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            val found = ContinuityAnalyzer.analyze(draft, loreFacts, secrets)
                            repository.addWarnings(found)
                            warnings = repository.getWarnings(includeClosed = false)
                            status = if (found.isEmpty()) "No continuity warnings found." else "${found.size} warning${if (found.size == 1) "" else "s"} found."
                        }, enabled = draft.isNotBlank()) { Text("Analyze") }
                        Spacer(Modifier.height(6.dp))
                        Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Canon facts", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("Store facts such as Ciro → eye color → dark brown. New text that contradicts a fact creates a review instead of silently changing it.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(subject, { subject = it }, Modifier.fillMaxWidth(), label = { Text("Subject") })
                        Spacer(Modifier.height(7.dp))
                        OutlinedTextField(attribute, { attribute = it }, Modifier.fillMaxWidth(), label = { Text("Attribute") })
                        Spacer(Modifier.height(7.dp))
                        OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), label = { Text("Canon value") })
                        Spacer(Modifier.height(9.dp))
                        Button(onClick = {
                            if (repository.addLoreFact(subject, attribute, value) != null) {
                                subject = ""; attribute = ""; value = ""; refresh(); status = "Canon fact added."
                            } else status = "That canon fact could not be added."
                        }, enabled = subject.isNotBlank() && attribute.isNotBlank() && value.isNotBlank()) { Text("Add canon fact") }
                        if (loreFacts.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            loreFacts.forEach { fact ->
                                Text("${fact.subject} • ${fact.attribute}: ${fact.value}", fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp))
                            }
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Secrets / hidden information", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("The assistant may know a secret while characters do not. A scene should preserve uncertainty until the story explicitly reveals it.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(secretSubject, { secretSubject = it }, Modifier.fillMaxWidth(), label = { Text("Subject") })
                        Spacer(Modifier.height(7.dp))
                        OutlinedTextField(secretText, { secretText = it }, Modifier.fillMaxWidth(), label = { Text("Hidden information") })
                        Spacer(Modifier.height(7.dp))
                        OutlinedTextField(knownBy, { knownBy = it }, Modifier.fillMaxWidth(), label = { Text("Known by (comma separated)") })
                        Spacer(Modifier.height(9.dp))
                        Button(onClick = {
                            if (repository.addSecret(secretSubject, secretText, knownBy.split(",")) != null) {
                                secretSubject = ""; secretText = ""; knownBy = ""; refresh(); status = "Secret stored as hidden canon."
                            } else status = "That secret could not be added."
                        }, enabled = secretSubject.isNotBlank() && secretText.isNotBlank()) { Text("Add secret") }
                        if (secrets.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            secrets.forEach { secret ->
                                Text("${secret.subject}: hidden • known by ${secret.knownBy.ifEmpty { listOf("nobody specified") }.joinToString()}", fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp))
                            }
                        }
                    }
                }
            }

            item {
                Text("Review queue", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            if (warnings.isEmpty()) {
                item {
                    Text("No open warnings.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                items(warnings, key = { it.id }) { warning ->
                    WarningCard(
                        warning = warning,
                        onIgnore = { repository.setWarningState(warning.id, WarningState.IGNORED); refresh() },
                        onEdit = {
                            draft = if (draft.isBlank()) warning.suggestion else "$draft\n\n[Continuity edit suggestion] ${warning.suggestion}"
                            status = "Draft updated with an edit suggestion."
                        },
                        onChange = {
                            newCanonValue = ""
                            changeWarning = warning
                        },
                        onKeepHidden = { repository.setWarningState(warning.id, WarningState.RESOLVED); refresh(); status = "Secret kept hidden." },
                        onReveal = { repository.setWarningState(warning.id, WarningState.RESOLVED); refresh(); status = "Reveal explicitly accepted." }
                    )
                }
            }
        }
    }
}

@Composable
private fun WarningCard(
    warning: ContinuityWarning,
    onIgnore: () -> Unit,
    onEdit: () -> Unit,
    onChange: () -> Unit,
    onKeepHidden: () -> Unit,
    onReveal: () -> Unit
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(17.dp)) {
            Text(warning.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.height(6.dp))
            Text(warning.details, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(7.dp))
            Text("Suggested fix: ${warning.suggestion}", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                when (warning.type) {
                    WarningType.PLOT_HOLE -> {
                        TextButton(onClick = onIgnore) { Text("Ignore") }
                        TextButton(onClick = onEdit) { Text("Edit") }
                    }
                    WarningType.LORE_CONFLICT -> {
                        TextButton(onClick = onIgnore) { Text("Ignore") }
                        TextButton(onClick = onChange) { Text("Change") }
                    }
                    WarningType.SECRET_LEAK -> {
                        TextButton(onClick = onKeepHidden) { Text("Keep hidden") }
                        TextButton(onClick = onReveal) { Text("Reveal") }
                    }
                }
            }
        }
    }
}

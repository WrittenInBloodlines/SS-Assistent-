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
import com.ss.assistent.continuity.SecretFact
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
    var canonHistory by remember { mutableStateOf(repository.getCanonHistory()) }
    var subject by remember { mutableStateOf("") }
    var attribute by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var secretSubject by remember { mutableStateOf("") }
    var secretText by remember { mutableStateOf("") }
    var knownBy by remember { mutableStateOf("") }
    var changeWarning by remember { mutableStateOf<ContinuityWarning?>(null) }
    var editSecret by remember { mutableStateOf<SecretFact?>(null) }
    var newCanonValue by remember { mutableStateOf("") }
    var editSecretSubject by remember { mutableStateOf("") }
    var editSecretText by remember { mutableStateOf("") }
    var editKnownBy by remember { mutableStateOf("") }

    fun refresh() {
        loreFacts = repository.getLoreFacts()
        secrets = repository.getSecrets()
        warnings = repository.getWarnings(includeClosed = false)
        canonHistory = repository.getCanonHistory()
    }

    changeWarning?.let { warning ->
        val fact = warning.relatedLoreFactId?.let { id -> loreFacts.firstOrNull { it.id == id } }
        AlertDialog(
            onDismissRequest = { changeWarning = null },
            title = { Text("Change canon?") },
            text = {
                Column {
                    Text("This changes the stored canon. It will not happen silently.", fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = newCanonValue, onValueChange = { newCanonValue = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New canon value") })
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
                        status = "Canon changed explicitly and added to history."
                    }
                }) { Text("Change") }
            },
            dismissButton = { TextButton(onClick = { changeWarning = null }) { Text("Cancel") } }
        )
    }

    editSecret?.let { secret ->
        AlertDialog(
            onDismissRequest = { editSecret = null },
            title = { Text("Edit hidden information") },
            text = {
                Column {
                    Text("This edits the stored secret itself. It remains hidden unless you explicitly choose Reveal.", fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(editSecretSubject, { editSecretSubject = it }, Modifier.fillMaxWidth(), label = { Text("Subject") })
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(editSecretText, { editSecretText = it }, Modifier.fillMaxWidth(), label = { Text("Hidden information") })
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(editKnownBy, { editKnownBy = it }, Modifier.fillMaxWidth(), label = { Text("Known by (comma separated)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (repository.replaceSecret(secret.id, editSecretSubject, editSecretText, editKnownBy.split(",")) != null) {
                        editSecret = null
                        refresh()
                        status = "Hidden information updated."
                    }
                }, enabled = editSecretSubject.isNotBlank() && editSecretText.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editSecret = null }) { Text("Cancel") } }
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
                        OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(), minLines = 7, label = { Text("Story draft") })
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
                        Text("Structured facts are linked by ID, so a warning never has to guess which canon entry it refers to.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
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
                            loreFacts.forEach { fact -> Text("${fact.subject} • ${fact.attribute}: ${fact.value}", fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Secrets / hidden information", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("Secrets stay separate from normal canon. Known-by information is stored with each secret and is not treated as global character knowledge.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
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
                                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Text("${secret.subject}: hidden", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Known by: ${secret.knownBy.ifEmpty { listOf("nobody specified") }.joinToString()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = {
                                        editSecret = secret
                                        editSecretSubject = secret.subject
                                        editSecretText = secret.secret
                                        editKnownBy = secret.knownBy.joinToString(", ")
                                    }) { Text("Edit secret") }
                                }
                            }
                        }
                    }
                }
            }

            if (canonHistory.isNotEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Canon change history", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.height(5.dp))
                            Text("Every intentional canon replacement keeps the previous value. Nothing is silently erased.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                            Spacer(Modifier.height(10.dp))
                            canonHistory.take(20).forEach { change ->
                                Column(Modifier.padding(vertical = 5.dp)) {
                                    Text("${change.subject} • ${change.attribute}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("${change.oldValue} → ${change.newValue}", fontSize = 12.sp)
                                    Text(change.displayTime(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            item { Text("Review queue", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            if (warnings.isEmpty()) {
                item { Text("No open warnings.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            } else {
                items(warnings, key = { it.id }) { warning ->
                    WarningCard(
                        warning = warning,
                        onIgnore = { repository.setWarningState(warning.id, WarningState.IGNORED); refresh() },
                        onEdit = {
                            if (warning.type == WarningType.SECRET_LEAK) {
                                val secret = warning.relatedSecretId?.let { id -> secrets.firstOrNull { it.id == id } }
                                if (secret != null) {
                                    editSecret = secret
                                    editSecretSubject = secret.subject
                                    editSecretText = secret.secret
                                    editKnownBy = secret.knownBy.joinToString(", ")
                                } else status = "The referenced secret no longer exists."
                            } else {
                                draft = if (draft.isBlank()) warning.suggestion else "$draft\n\n[Continuity edit suggestion] ${warning.suggestion}"
                                status = "Draft updated with an edit suggestion."
                            }
                        },
                        onChange = { newCanonValue = ""; changeWarning = warning },
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
            when (warning.type) {
                WarningType.PLOT_HOLE -> ActionRows("Edit" to onEdit, "Ignore" to onIgnore)
                WarningType.LORE_CONFLICT -> ActionRows("Change" to onChange, "Ignore" to onIgnore)
                WarningType.SECRET_LEAK -> {
                    ActionRows("Keep hidden" to onKeepHidden, "Reveal" to onReveal)
                    ActionRows("Edit" to onEdit, "Ignore" to onIgnore)
                }
            }
        }
    }
}

@Composable
private fun ActionRows(primary: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = primary.second, modifier = Modifier.weight(1f)) { Text(primary.first) }
        TextButton(onClick = secondary.second, modifier = Modifier.weight(1f)) { Text(secondary.first) }
    }
}

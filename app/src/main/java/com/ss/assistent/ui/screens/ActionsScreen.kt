package com.ss.assistent.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ss.assistent.actions.ActionHistoryRepository
import com.ss.assistent.actions.ActionPermissionManager
import com.ss.assistent.actions.ActionPlanner
import com.ss.assistent.actions.PlannedAction

@Composable
fun ActionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val planner = remember { ActionPlanner() }
    val permissions = remember { ActionPermissionManager(context) }
    val historyRepository = remember { ActionHistoryRepository(context) }
    var request by remember { mutableStateOf("") }
    var planned by remember { mutableStateOf<List<PlannedAction>>(emptyList()) }
    var history by remember { mutableStateOf(historyRepository.load()) }
    var status by remember { mutableStateOf("Planning only. No Android action is executed from this screen.") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 28.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Text("Actions", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Plan device actions before execution is implemented.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Action planner", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = request,
                        onValueChange = { request = it.take(1000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("What should the assistant plan?") },
                        placeholder = { Text("Example: open WhatsApp") },
                        minLines = 2
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            planned = planner.plan(request)
                            status = if (planned.isEmpty()) "No supported action pattern was detected." else "${planned.size} action proposal${if (planned.size == 1) "" else "s"} created."
                        },
                        enabled = request.isNotBlank()
                    ) { Text("Plan action") }
                    Spacer(Modifier.height(8.dp))
                    Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
        if (planned.isNotEmpty()) {
            item { Text("Proposals", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            items(planned, key = { it.id }) { action ->
                ActionProposalCard(action, permissions.isAllowed(action.capability)) { result ->
                    historyRepository.record(action, result)
                    history = historyRepository.load()
                    status = result
                }
            }
        }
        item {
            Text("Capability controls", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("These local switches are additional gates. They do not grant Android permissions and do not execute actions by themselves.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }
        items(permissions.allCapabilities(), key = { it.capability }) { capability ->
            val label = capability.capability.replace('_', ' ').replaceFirstChar { it.uppercase() }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.SemiBold)
                        Text(if (capability.allowed) "Allowed for future execution" else "Blocked", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Switch(checked = capability.allowed, onCheckedChange = { permissions.setAllowed(capability.capability, it) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                Text("Activity history", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) OutlinedButton(onClick = { historyRepository.clear(); history = emptyList() }) { Text("Clear") }
            }
        }
        if (history.isEmpty()) {
            item { Text("No planned actions have been recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        } else {
            items(history, key = { it.id }) { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(entry.action.title, fontWeight = FontWeight.SemiBold)
                        Text(entry.action.type.title + " • " + entry.action.risk.title + " risk", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(entry.result, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(entry.formattedTimestamp(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionProposalCard(action: PlannedAction, allowed: Boolean, onRecord: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(action.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(action.description, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text("Capability: ${action.capability}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Text("Risk: ${action.risk.title} • Confirmation required: ${action.requiresConfirmation}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Text(if (allowed) "Capability is allowed, but execution is not implemented yet." else "Capability is blocked by your local control.", color = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onRecord(if (allowed) "Proposal recorded. Execution is intentionally not implemented yet." else "Proposal blocked by local capability control.") }) { Text("Record decision") }
        }
    }
}

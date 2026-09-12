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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ss.assistent.assistant.ChatMessage
import com.ss.assistent.assistant.LlamaAssistantRuntime
import com.ss.assistent.assistant.RuntimeResult
import com.ss.assistent.chat.ConversationRepository
import com.ss.assistent.memory.MemoryRepository
import com.ss.assistent.model.ModelRepository
import com.ss.assistent.settings.AssistantPreferences
import kotlinx.coroutines.launch

@Composable
fun AssistantScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val modelRepository = remember { ModelRepository(context) }
    val conversationRepository = remember { ConversationRepository(context) }
    val memoryRepository = remember { MemoryRepository(context) }
    val preferences = remember { AssistantPreferences(context) }
    val runtime = remember { LlamaAssistantRuntime(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(conversationRepository.loadMessages()) } }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready for local conversation.") }
    var busy by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val activeModel = remember { modelRepository.getModels().firstOrNull { it.isActive } }

    DisposableEffect(Unit) {
        onDispose { runtime.unload() }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear conversation?") },
            text = { Text("This removes the saved conversation from this device. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    messages.clear()
                    conversationRepository.clear()
                    showClearDialog = false
                    status = "Conversation cleared."
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Assistant", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(activeModel?.name ?: "No active model", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            if (messages.isNotEmpty()) {
                OutlinedButton(onClick = { if (!busy) showClearDialog = true }, enabled = !busy) { Text("Clear") }
            }
        }

        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Style: ${preferences.responseStyle.title}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Local memory: ${memoryRepository.getAll().size} saved", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (messages.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Local assistant", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Conversations are saved locally. Explicit memories can be reviewed and deleted from Memory.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { message -> MessageBubble(message) }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                placeholder = { Text("Message your assistant...") },
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = {
                        val text = input.trim()
                        if (text.isEmpty() || busy) return@Button
                        val model = activeModel
                        if (model == null) {
                            status = "No active model. Add a GGUF model in Models."
                            return@Button
                        }

                        messages.add(ChatMessage("user", text))
                        conversationRepository.saveMessages(messages)
                        input = ""
                        busy = true
                        status = "Checking model..."

                        scope.launch {
                            when (val loadResult = runtime.load(model)) {
                                is RuntimeResult.Error -> {
                                    status = loadResult.message
                                    busy = false
                                }
                                is RuntimeResult.Success -> {
                                    status = "Generating locally..."
                                    val memories = memoryRepository.getAll()
                                    val memoryText = if (memories.isEmpty()) "No saved memories are available." else memories.joinToString("\n") { "- ${it.category.title}: ${it.text}" }
                                    val system = ChatMessage(
                                        "system",
                                        "You are SS Assistent, a local Android device assistant. ${preferences.responseStyle.instruction}\n" +
                                            "Use these user-approved memories only when relevant:\n$memoryText\n" +
                                            "Never claim to have performed a device action unless the app actually reports that action as completed."
                                    )
                                    when (val result = runtime.generate(listOf(system) + messages.toList())) {
                                        is RuntimeResult.Success -> {
                                            messages.add(ChatMessage("assistant", result.text))
                                            conversationRepository.saveMessages(messages)
                                            status = "Ready"
                                        }
                                        is RuntimeResult.Error -> status = result.message
                                    }
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = input.isNotBlank() && !busy
                ) { Text(if (busy) "Working..." else "Send") }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(if (isUser) "You" else "Assistant", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(message.content, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

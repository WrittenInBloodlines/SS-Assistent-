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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.ss.assistent.model.ModelRepository
import kotlinx.coroutines.launch

@Composable
fun AssistantScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { ModelRepository(context) }
    val runtime = remember { LlamaAssistantRuntime() }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Select a local model before starting a chat.") }
    var busy by remember { mutableStateOf(false) }

    val activeModel = remember { repository.getModels().firstOrNull { it.isActive } }

    DisposableEffect(Unit) {
        onDispose { runtime.unload() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 28.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Assistant", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    activeModel?.name ?: "No active model",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (messages.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Local assistant", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Your conversations stay on the device when you use an imported local GGUF model.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
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

                        messages.add(ChatMessage("user", text))
                        input = ""
                        busy = true
                        status = "Preparing local inference..."

                        scope.launch {
                            val model = activeModel
                            if (model == null) {
                                status = "No active model. Add a GGUF model in Models."
                                busy = false
                                return@launch
                            }

                            when (val loadResult = runtime.load(model)) {
                                is RuntimeResult.Error -> {
                                    status = loadResult.message
                                    busy = false
                                }
                                is RuntimeResult.Success -> {
                                    status = "Generating locally..."
                                    when (val result = runtime.generate(messages.toList())) {
                                        is RuntimeResult.Success -> {
                                            messages.add(ChatMessage("assistant", result.text))
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
                ) {
                    Text(if (busy) "Working..." else "Send")
                }
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
        colors = CardDefaults.cardColors(
            containerColor = if (isUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(
                if (isUser) "You" else "Assistant",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(message.content, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

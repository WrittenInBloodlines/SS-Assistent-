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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ss.assistent.assistant.ChatMessage
import com.ss.assistent.assistant.LlamaAssistantRuntime
import com.ss.assistent.assistant.PromptContext
import com.ss.assistent.assistant.RuntimeResult
import com.ss.assistent.chat.ConversationRepository
import com.ss.assistent.memory.MemoryCategory
import com.ss.assistent.memory.MemoryIntentParser
import com.ss.assistent.memory.MemoryRepository
import com.ss.assistent.memory.MemorySensitivity
import com.ss.assistent.memory.MemorySensitivityClassifier
import com.ss.assistent.memory.MemorySensitivityResult
import com.ss.assistent.memory.SealedMemoryReplacementResolution
import com.ss.assistent.memory.SealedMemoryReplacementResolver
import com.ss.assistent.model.ModelInfo
import com.ss.assistent.model.ModelRepository
import com.ss.assistent.settings.AssistantPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val modelRepository = remember { ModelRepository(context) }
    val conversationRepository = remember { ConversationRepository(context) }
    val memoryRepository = remember { MemoryRepository(context) }
    val preferences = remember { AssistantPreferences(context) }
    val runtime = remember { LlamaAssistantRuntime(context.applicationContext) }
    val replacementResolver = remember { SealedMemoryReplacementResolver(memoryRepository) }
    val scope = rememberCoroutineScope()
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply { addAll(conversationRepository.loadMessages()) }
    }

    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready for local conversation.") }
    var busy by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var activeModel by remember { mutableStateOf<ModelInfo?>(null) }
    var memoryTarget by remember { mutableStateOf<String?>(null) }
    var exactMemoryTarget by remember { mutableStateOf<String?>(null) }
    var sensitiveMemoryTarget by remember { mutableStateOf<String?>(null) }
    var sensitiveMemoryResult by remember { mutableStateOf<MemorySensitivityResult?>(null) }
    var memoryCategory by remember { mutableStateOf(MemoryCategory.FACT) }
    var memoryMenuExpanded by remember { mutableStateOf(false) }
    var exactMemoryMenuExpanded by remember { mutableStateOf(false) }
    var replacementResolution by remember { mutableStateOf<SealedMemoryReplacementResolution.Ready?>(null) }

    fun refreshModel() {
        activeModel = modelRepository.getModels().firstOrNull { it.isActive }
    }

    LaunchedEffect(Unit) { refreshModel() }

    val latestGenerationJob by rememberUpdatedState(generationJob)
    DisposableEffect(Unit) {
        onDispose {
            latestGenerationJob?.cancel()
            runtime.unload()
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear conversation?") },
            text = { Text("This removes the saved conversation from this device. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    if (!busy) {
                        messages.clear()
                        conversationRepository.clear()
                        showClearDialog = false
                        status = "Conversation cleared."
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    sensitiveMemoryTarget?.let { target ->
        val sensitivity = sensitiveMemoryResult
        AlertDialog(
            onDismissRequest = {
                sensitiveMemoryTarget = null
                sensitiveMemoryResult = null
            },
            title = { Text("Sensitive memory") },
            text = {
                Column {
                    Text(
                        sensitivity?.reason ?: "This information may be sensitive.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(target, modifier = Modifier.padding(12.dp), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (sensitivity?.shouldBlockPersistence == true) {
                        Text(
                            "SS Assistent will not save this kind of information as long-term memory.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    } else {
                        Text(
                            "Saving requires your explicit confirmation. Nothing will be stored unless you choose Save.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (sensitivity?.shouldBlockPersistence != true) {
                    TextButton(onClick = {
                        val saved = memoryRepository.add(memoryCategory, target)
                        status = if (saved != null) "Sensitive memory saved after confirmation." else "That memory already exists."
                        sensitiveMemoryTarget = null
                        sensitiveMemoryResult = null
                    }) { Text("Save") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    sensitiveMemoryTarget = null
                    sensitiveMemoryResult = null
                    status = if (sensitivity?.shouldBlockPersistence == true) "Sensitive memory was not saved." else "Sensitive memory save cancelled."
                }) { Text("Cancel") }
            }
        )
    }

    memoryTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { memoryTarget = null },
            title = { Text("Save to memory") },
            text = {
                Column {
                    Text("Choose how this message should be stored.", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Normal memory", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("May be normalized and shortened for normal memory limits.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Exact / sealed memory", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("Preserves the supplied text exactly and does not normalize or shorten it.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Memory category", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(5.dp))
                    ExposedDropdownMenuBox(
                        expanded = memoryMenuExpanded,
                        onExpandedChange = { memoryMenuExpanded = !memoryMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = memoryCategory.title,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memoryMenuExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = memoryMenuExpanded,
                            onDismissRequest = { memoryMenuExpanded = false }
                        ) {
                            MemoryCategory.values().forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.title) },
                                    onClick = {
                                        memoryCategory = category
                                        memoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(target, modifier = Modifier.padding(12.dp), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val saved = memoryRepository.add(memoryCategory, target)
                        status = if (saved != null) "Memory saved." else "That memory already exists."
                        memoryTarget = null
                    }) { Text("Save normal") }
                    TextButton(onClick = {
                        val sensitivity = MemorySensitivityClassifier.classify(target)
                        if (sensitivity.shouldBlockPersistence) {
                            memoryTarget = null
                            sensitiveMemoryTarget = target
                            sensitiveMemoryResult = sensitivity
                        } else if (sensitivity.requiresExplicitChoice) {
                            memoryTarget = null
                            sensitiveMemoryTarget = target
                            sensitiveMemoryResult = sensitivity
                        } else {
                            val saved = memoryRepository.addExact(memoryCategory, target)
                            status = when {
                                saved != null -> "Sealed memory saved exactly."
                                target.length > 6000 -> "This exact memory is too long to seal."
                                else -> "That exact memory already exists."
                            }
                            memoryTarget = null
                        }
                    }) { Text("Save exact") }
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryTarget = null }) { Text("Cancel") }
            }
        )
    }

    exactMemoryTarget?.let { target ->
        val sensitivity = MemorySensitivityClassifier.classify(target)
        AlertDialog(
            onDismissRequest = { exactMemoryTarget = null },
            title = { Text("Confirm exact memory") },
            text = {
                Column {
                    Text(
                        "SS Assistent detected an explicit request to preserve the following text exactly as supplied.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(target, modifier = Modifier.padding(12.dp), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "No trimming, whitespace normalization, shortening, or rewriting will be applied.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    if (sensitivity.requiresExplicitChoice) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (sensitivity.shouldBlockPersistence) "This type of sensitive information cannot be stored as long-term memory." else sensitivity.reason,
                            color = if (sensitivity.shouldBlockPersistence) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Memory category", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(5.dp))
                    ExposedDropdownMenuBox(
                        expanded = exactMemoryMenuExpanded,
                        onExpandedChange = { exactMemoryMenuExpanded = !exactMemoryMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = memoryCategory.title,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exactMemoryMenuExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = exactMemoryMenuExpanded,
                            onDismissRequest = { exactMemoryMenuExpanded = false }
                        ) {
                            MemoryCategory.values().forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.title) },
                                    onClick = {
                                        memoryCategory = category
                                        exactMemoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!sensitivity.shouldBlockPersistence) {
                    TextButton(onClick = {
                        val saved = memoryRepository.addExact(memoryCategory, target)
                        status = when {
                            saved != null -> "Sealed memory saved exactly."
                            target.length > 6000 -> "This exact memory is too long to seal."
                            else -> "That exact memory already exists."
                        }
                        exactMemoryTarget = null
                    }) { Text(if (sensitivity.requiresExplicitChoice) "Save anyway" else "Save exact") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    exactMemoryTarget = null
                    status = if (sensitivity.shouldBlockPersistence) "Sensitive memory was blocked." else "Exact memory save cancelled."
                }) { Text("Cancel") }
            }
        )
    }

    replacementResolution?.let { resolution ->
        val replacementSensitivity = MemorySensitivityClassifier.classify(resolution.replacementText)
        AlertDialog(
            onDismissRequest = { replacementResolution = null },
            title = { Text("Replace sealed memory?") },
            text = {
                Column {
                    Text("This changes one existing sealed memory. Nothing is written until you confirm.", fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Current", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(resolution.memory.text, modifier = Modifier.padding(12.dp), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Replacement", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(resolution.replacementText, modifier = Modifier.padding(12.dp), fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            replacementSensitivity.shouldBlockPersistence -> "This replacement contains information that cannot be stored as long-term memory."
                            replacementSensitivity.requiresExplicitChoice -> replacementSensitivity.reason
                            else -> "The replacement remains sealed and will be preserved exactly as supplied."
                        },
                        color = if (replacementSensitivity.shouldBlockPersistence) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                if (!replacementSensitivity.shouldBlockPersistence) {
                    TextButton(onClick = {
                        val applied = replacementResolver.apply(resolution)
                        status = if (applied != null) "Sealed memory replaced and history preserved." else "Sealed memory replacement failed."
                        replacementResolution = null
                    }) { Text(if (replacementSensitivity.requiresExplicitChoice) "Replace anyway" else "Replace") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    replacementResolution = null
                    status = if (replacementSensitivity.shouldBlockPersistence) "Replacement blocked." else "Replacement cancelled."
                }) { Text("Cancel") }
            }
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
                    Text("Generation: ${preferences.maxTokens} tokens • T %.2f".format(preferences.temperature), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("Local memory: ${memoryRepository.getAll().size} saved", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        if (activeModel == null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("No active model", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("Add a GGUF model and set it active in Models before starting a local conversation.", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        } else if (messages.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Local assistant", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Conversations are saved locally. Use Remember on a user message when you explicitly want something stored as long-term memory.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { message -> MessageBubble(message, onRemember = {
                val sensitivity = MemorySensitivityClassifier.classify(it)
                memoryCategory = MemoryCategory.FACT
                if (sensitivity.requiresExplicitChoice) {
                    sensitiveMemoryTarget = it
                    sensitiveMemoryResult = sensitivity
                    status = if (sensitivity.shouldBlockPersistence) "Sensitive memory blocked." else "Awaiting sensitive-memory confirmation."
                } else {
                    memoryTarget = it
                }
            }) }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && activeModel != null,
                placeholder = { Text("Message your assistant...") },
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (busy) {
                    OutlinedButton(onClick = {
                        generationJob?.cancel()
                        generationJob = null
                        busy = false
                        status = "Generation stopped."
                    }) { Text("Stop") }
                    Spacer(Modifier.width(8.dp))
                }
                Button(
                    onClick = {
                        val rawText = input
                        val text = rawText.trim()
                        val model = activeModel
                        if (text.isEmpty() || busy || model == null) return@Button

                        val exactRequest = MemoryIntentParser.parse(rawText)
                        if (exactRequest != null) {
                            val sensitivity = MemorySensitivityClassifier.classify(exactRequest.text)
                            if (sensitivity.shouldBlockPersistence) {
                                sensitiveMemoryTarget = exactRequest.text
                                sensitiveMemoryResult = sensitivity
                                status = "Sensitive memory blocked."
                            } else {
                                exactMemoryTarget = exactRequest.text
                                memoryCategory = MemoryCategory.FACT
                                exactMemoryMenuExpanded = false
                                status = if (sensitivity.requiresExplicitChoice) "Sensitive exact-memory confirmation required." else "Awaiting exact-memory confirmation."
                            }
                            input = ""
                            return@Button
                        }

                        val replacementRequest = MemoryIntentParser.parseSealedReplacement(rawText)
                        if (replacementRequest != null) {
                            input = ""
                            when (val resolution = replacementResolver.resolve(replacementRequest)) {
                                is SealedMemoryReplacementResolution.Ready -> {
                                    val sensitivity = MemorySensitivityClassifier.classify(resolution.replacementText)
                                    if (sensitivity.shouldBlockPersistence) {
                                        status = "Replacement blocked because the new value is too sensitive to store as long-term memory."
                                    } else {
                                        replacementResolution = resolution
                                        status = if (sensitivity.requiresExplicitChoice) "Sensitive replacement confirmation required." else "Awaiting sealed-memory replacement confirmation."
                                    }
                                }
                                is SealedMemoryReplacementResolution.NotFound -> status = "No sealed memory matched that exact previous text."
                                is SealedMemoryReplacementResolution.Ambiguous -> status = "Replacement refused: ${resolution.matchCount} sealed memories match that exact text."
                                is SealedMemoryReplacementResolution.InvalidReplacement -> status = "Replacement is invalid or exceeds ${resolution.maxCharacters} characters."
                            }
                            return@Button
                        }

                        messages.add(ChatMessage("user", text))
                        conversationRepository.saveMessages(messages)
                        input = ""
                        busy = true
                        status = "Checking model..."

                        generationJob = scope.launch {
                            var generatedAssistantText = ""
                            var generationFailed = false
                            try {
                                when (val loadResult = runtime.load(model)) {
                                    is RuntimeResult.Error -> {
                                        generationFailed = true
                                        status = loadResult.message
                                    }
                                    is RuntimeResult.Success -> {
                                        status = "Generating locally..."
                                        val styleInstruction = when {
                                            preferences.responseStyle == com.ss.assistent.settings.ResponseStyle.CUSTOM && preferences.customStyleInstruction.isNotBlank() ->
                                                "${preferences.responseStyle.instruction} Custom instruction: ${preferences.customStyleInstruction}"
                                            else -> preferences.responseStyle.instruction
                                        }
                                        val system = ChatMessage(
                                            "system",
                                            PromptContext.buildSystemPrompt(
                                                styleInstruction = styleInstruction,
                                                memories = memoryRepository.getAll(),
                                                query = text
                                            )
                                        )
                                        val conversationContext = PromptContext.recentConversation(messages)
                                        var assistantIndex = -1
                                        val generationSettings = preferences.generationSettings()

                                        runtime.generateStream(
                                            messages = listOf(system) + conversationContext,
                                            settings = generationSettings
                                        ).collect { chunk ->
                                            when (chunk) {
                                                is RuntimeResult.Success -> {
                                                    generatedAssistantText += chunk.text
                                                    if (assistantIndex == -1) {
                                                        assistantIndex = messages.size
                                                        messages.add(ChatMessage("assistant", generatedAssistantText))
                                                    } else {
                                                        messages[assistantIndex] = ChatMessage("assistant", generatedAssistantText)
                                                    }
                                                }
                                                is RuntimeResult.Error -> {
                                                    generationFailed = true
                                                    status = chunk.message
                                                }
                                            }
                                        }

                                        if (!generationFailed && generatedAssistantText.isBlank()) {
                                            generationFailed = true
                                            status = "The model produced no response."
                                        }
                                        if (generatedAssistantText.isNotBlank()) conversationRepository.saveMessages(messages)
                                        if (!generationFailed) status = "Ready"
                                    }
                                }
                            } finally {
                                if (generationFailed && generatedAssistantText.isNotBlank()) conversationRepository.saveMessages(messages)
                                busy = false
                                generationJob = null
                            }
                        }
                    },
                    enabled = input.isNotBlank() && !busy && activeModel != null
                ) { Text("Send") }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onRemember: (String) -> Unit) {
    val isUser = message.role == "user"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (isUser) "You" else "Assistant", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (isUser) {
                    TextButton(onClick = { onRemember(message.content) }) { Text("Remember") }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(message.content, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

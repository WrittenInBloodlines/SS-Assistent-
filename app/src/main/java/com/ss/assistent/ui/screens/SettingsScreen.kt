package com.ss.assistent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ss.assistent.settings.AssistantPreferences
import com.ss.assistent.settings.GenerationSettings
import com.ss.assistent.settings.ResponseStyle

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { AssistantPreferences(context) }
    var selectedStyle by remember { mutableStateOf(preferences.responseStyle) }
    var customInstruction by remember { mutableStateOf(preferences.customStyleInstruction) }
    var maxTokens by remember { mutableIntStateOf(preferences.maxTokens) }
    var temperature by remember { mutableFloatStateOf(preferences.temperature) }
    var topK by remember { mutableIntStateOf(preferences.topK) }
    var topP by remember { mutableFloatStateOf(preferences.topP) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun saveGenerationSettings() {
        preferences.maxTokens = maxTokens
        preferences.temperature = temperature
        preferences.topK = topK
        preferences.topP = topP
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset generation settings?") },
            text = { Text("This restores the default local sampling settings and clears your custom response instruction.") },
            confirmButton = {
                TextButton(onClick = {
                    val defaults = GenerationSettings.DEFAULT
                    selectedStyle = ResponseStyle.NORMAL
                    customInstruction = ""
                    maxTokens = defaults.maxTokens
                    temperature = defaults.temperature
                    topK = defaults.topK
                    topP = defaults.topP
                    preferences.responseStyle = ResponseStyle.NORMAL
                    preferences.customStyleInstruction = ""
                    saveGenerationSettings()
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Assistant behavior", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }

        item {
            SettingsSection(
                title = "Response style",
                description = "Choose the assistant's normal communication style."
            ) {
                ResponseStyle.values().forEach { style ->
                    StyleRow(style, selectedStyle == style) {
                        selectedStyle = style
                        preferences.responseStyle = style
                    }
                }
                if (selectedStyle == ResponseStyle.CUSTOM) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customInstruction,
                        onValueChange = {
                            customInstruction = it
                            preferences.customStyleInstruction = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Custom instruction") },
                        placeholder = { Text("Example: Use clear headings and explain technical terms.") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        item {
            SettingsSection(
                title = "Generation",
                description = "Fine-tune how the local GGUF model samples its next tokens. Changes apply to new generations."
            ) {
                SettingSlider(
                    title = "Maximum response length",
                    valueLabel = "$maxTokens tokens",
                    value = maxTokens.toFloat(),
                    range = 64f..1024f,
                    steps = 15,
                    onValueChange = { maxTokens = it.toInt() }
                )
                SettingSlider(
                    title = "Temperature",
                    valueLabel = "%.2f".format(temperature),
                    value = temperature,
                    range = 0.1f..1.5f,
                    steps = 13,
                    onValueChange = { temperature = it }
                )
                SettingSlider(
                    title = "Top-K",
                    valueLabel = topK.toString(),
                    value = topK.toFloat(),
                    range = 1f..100f,
                    steps = 18,
                    onValueChange = { topK = it.toInt() }
                )
                SettingSlider(
                    title = "Top-P",
                    valueLabel = "%.2f".format(topP),
                    value = topP,
                    range = 0.1f..1.0f,
                    steps = 17,
                    onValueChange = { topP = it }
                )
                Spacer(Modifier.height(6.dp))
                Button(onClick = { saveGenerationSettings() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save generation settings")
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Current local profile", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Style: ${selectedStyle.title}", fontSize = 13.sp)
                    Text("Length: $maxTokens tokens", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("Temperature: %.2f  •  Top-K: %d  •  Top-P: %.2f".format(temperature, topK, topP), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("Everything here is stored locally. No cloud service is required for these settings.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }

        item {
            OutlinedButton(onClick = { showResetDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset generation settings")
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, description: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(20.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(valueLabel, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun StyleRow(style: ResponseStyle, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(style.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (selected) Text("Selected", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(style.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

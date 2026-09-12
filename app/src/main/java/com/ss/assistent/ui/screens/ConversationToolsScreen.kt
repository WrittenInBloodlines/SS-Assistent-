package com.ss.assistent.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ss.assistent.chat.ConversationGenerationProfile
import com.ss.assistent.chat.ConversationRepository
import com.ss.assistent.settings.GenerationSettings
import com.ss.assistent.settings.ResponseStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationToolsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ConversationRepository(context) }
    var title by remember { mutableStateOf(repository.title) }
    var profile by remember { mutableStateOf(repository.profile()) }
    var styleMenuExpanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Conversation settings are stored locally.") }

    fun saveProfile(next: ConversationGenerationProfile) {
        repository.saveProfile(next)
        profile = next
        status = "Conversation profile saved."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Column(Modifier.weight(1f)) {
                Text("Conversation", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Manage the current local conversation", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }

        Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(18.dp)) {
                Text("Conversation title", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("${title.length}/120") }
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    repository.title = title
                    title = repository.title
                    status = "Conversation title saved."
                }) { Text("Save title") }
            }
        }

        Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(18.dp)) {
                Text("Per-conversation response profile", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(5.dp))
                Text("These settings belong to the current conversation and override the global assistant defaults.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(12.dp))
                Text("Response style", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(5.dp))
                ExposedDropdownMenuBox(
                    expanded = styleMenuExpanded,
                    onExpandedChange = { styleMenuExpanded = !styleMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = profile.responseStyle?.title ?: "Use global style",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleMenuExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = styleMenuExpanded,
                        onDismissRequest = { styleMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Use global style") },
                            onClick = {
                                saveProfile(profile.copy(responseStyle = null))
                                styleMenuExpanded = false
                            }
                        )
                        ResponseStyle.values().forEach { style ->
                            DropdownMenuItem(
                                text = { Text(style.title) },
                                onClick = {
                                    saveProfile(profile.copy(responseStyle = style))
                                    styleMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                if (profile.responseStyle == ResponseStyle.CUSTOM) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = profile.customStyleInstruction,
                        onValueChange = { saveProfile(profile.copy(customStyleInstruction = it.take(1000))) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        label = { Text("Custom style instruction") },
                        supportingText = { Text("${profile.customStyleInstruction.length}/1000") }
                    )
                }
                Spacer(Modifier.height(14.dp))
                ProfileSlider(
                    label = "Maximum response length",
                    valueLabel = "${profile.generation.maxTokens} tokens",
                    value = profile.generation.maxTokens.toFloat(),
                    range = 64f..1024f,
                    steps = 14,
                    onValueChange = { saveProfile(profile.copy(generation = profile.generation.copy(maxTokens = it.toInt()))) }
                )
                ProfileSlider(
                    label = "Temperature",
                    valueLabel = "%.2f".format(profile.generation.temperature),
                    value = profile.generation.temperature,
                    range = 0.1f..1.5f,
                    steps = 13,
                    onValueChange = { saveProfile(profile.copy(generation = profile.generation.copy(temperature = it))) }
                )
                ProfileSlider(
                    label = "Top-K",
                    valueLabel = profile.generation.topK.toString(),
                    value = profile.generation.topK.toFloat(),
                    range = 1f..100f,
                    steps = 19,
                    onValueChange = { saveProfile(profile.copy(generation = profile.generation.copy(topK = it.toInt()))) }
                )
                ProfileSlider(
                    label = "Top-P",
                    valueLabel = "%.2f".format(profile.generation.topP),
                    value = profile.generation.topP,
                    range = 0.1f..1.0f,
                    steps = 17,
                    onValueChange = { saveProfile(profile.copy(generation = profile.generation.copy(topP = it))) }
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    repository.clearProfile()
                    profile = ConversationGenerationProfile(generation = GenerationSettings.DEFAULT)
                    status = "Per-conversation profile reset to global defaults."
                }) { Text("Reset conversation profile") }
            }
        }

        Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp)) {
                Text("Export", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(5.dp))
                Text("Create a plain-text export of the current conversation and share it through Android's share sheet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, repository.title)
                        putExtra(Intent.EXTRA_TEXT, repository.exportText())
                    }
                    context.startActivity(Intent.createChooser(share, "Export conversation"))
                    status = "Export sheet opened."
                }) { Text("Export conversation") }
            }
        }

        Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
    }
}

@Composable
private fun ProfileSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}

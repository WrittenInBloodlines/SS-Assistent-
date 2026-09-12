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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ss.assistent.memory.MemoryCategory
import com.ss.assistent.memory.MemoryEntry
import com.ss.assistent.memory.MemoryRepository

@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { MemoryRepository(context) }
    val entries = remember { mutableStateListOf<MemoryEntry>().apply { addAll(repository.getAll()) } }
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MemoryCategory.FACT) }
    var menuOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Memory", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Review and control what the assistant remembers.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Add a memory", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Example: Prefers dark interfaces") }, minLines = 2, maxLines = 4)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { menuOpen = true }) { Text(category.title) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MemoryCategory.values().forEach { option ->
                                DropdownMenuItem(text = { Text(option.title) }, onClick = { category = option; menuOpen = false })
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = {
                            val value = text.trim()
                            if (value.isNotEmpty()) {
                                entries.add(repository.add(category, value))
                                text = ""
                            }
                        }, enabled = text.isNotBlank()) { Text("Save") }
                    }
                }
            }
        }
        item { Text("Stored memories", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp)) }
        if (entries.isEmpty()) {
            item { Text("No memories saved yet. Memories are explicit and user-controlled.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        } else {
            items(entries, key = { it.id }) { entry ->
                MemoryCard(entry) {
                    repository.delete(entry.id)
                    entries.remove(entry)
                }
            }
        }
    }
}

@Composable
private fun MemoryCard(entry: MemoryEntry, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(entry.category.title, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
            Spacer(Modifier.height(5.dp))
            Text(entry.text, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

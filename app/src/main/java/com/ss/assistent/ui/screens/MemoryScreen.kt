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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ss.assistent.memory.MemoryLock
import com.ss.assistent.memory.MemoryRepository

@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { MemoryRepository(context) }
    val entries = remember { mutableStateListOf<MemoryEntry>().apply { addAll(repository.getAll()) } }
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MemoryCategory.FACT) }
    var menuOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryEntry?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var categoryToClear by remember { mutableStateOf<MemoryCategory?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val filteredEntries = remember(entries.toList(), search) {
        val query = search.trim()
        if (query.isEmpty()) entries.toList()
        else entries.filter {
            it.text.contains(query, ignoreCase = true) ||
                it.category.title.contains(query, ignoreCase = true)
        }
    }

    if (editing != null) {
        MemoryEditDialog(
            entry = editing!!,
            onDismiss = { editing = null },
            onSave = { newCategory, newText ->
                val entry = editing!!
                val updated = if (entry.lock == MemoryLock.SEALED) {
                    repository.updateSealed(entry.id, newCategory, newText, explicitOverride = true)
                } else {
                    repository.update(entry.id, newCategory, newText)
                }
                if (updated == null) {
                    notice = "Nothing changed or an identical memory already exists."
                } else {
                    val index = entries.indexOfFirst { it.id == updated.id }
                    if (index >= 0) entries[index] = updated
                    notice = if (updated.lock == MemoryLock.SEALED) "Sealed memory updated exactly as entered." else "Memory updated."
                }
                editing = null
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Forget all memories?") },
            text = { Text("Every saved memory will be removed from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    repository.clear()
                    entries.clear()
                    showClearDialog = false
                    notice = "All memories forgotten."
                }) { Text("Forget all") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    categoryToClear?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToClear = null },
            title = { Text("Forget ${category.title.lowercase()}?") },
            text = { Text("This removes all ${category.title.lowercase()} memories. Sealed memories are not protected from an explicit Forget action.") },
            confirmButton = {
                TextButton(onClick = {
                    repository.clearCategory(category)
                    entries.removeAll { it.category == category }
                    categoryToClear = null
                    notice = "${category.title} memories forgotten."
                }) { Text("Forget category") }
            },
            dismissButton = { TextButton(onClick = { categoryToClear = null }) { Text("Cancel") } }
        )
    }

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
                    Text("Memory rules", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Normal memories may be cleaned up for storage. Sealed memories preserve the exact text you explicitly asked to save. A sealed memory is not shortened or normalized, and it only changes through an explicit replacement.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${repository.count(MemoryLock.SEALED)} sealed • ${repository.count(MemoryLock.EDITABLE)} editable", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Add a memory", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Example: Prefers dark interfaces") },
                        minLines = 2,
                        maxLines = 4
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { menuOpen = true }) { Text(category.title) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MemoryCategory.values().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.title) },
                                    onClick = { category = option; menuOpen = false }
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = {
                            val entry = repository.add(category, text)
                            if (entry == null) {
                                notice = if (text.isBlank()) "Enter a memory first." else "That memory already exists."
                            } else {
                                entries.add(entry)
                                text = ""
                                notice = "Memory saved."
                            }
                        }, enabled = text.isNotBlank()) { Text("Save") }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text("Exact/sealed capture will be available when the assistant recognizes an explicit request to preserve text exactly.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search memories...") },
                singleLine = true
            )
        }

        notice?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))) {
                    Text(message, modifier = Modifier.padding(14.dp), fontSize = 13.sp)
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Stored memories (${filteredEntries.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { showClearDialog = true }) { Text("Forget all") }
                    }
                }
                if (entries.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Forget by category", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MemoryCategory.values().forEach { option ->
                            TextButton(
                                onClick = { categoryToClear = option },
                                enabled = entries.any { it.category == option }
                            ) { Text(option.title) }
                        }
                    }
                }
            }
        }

        if (filteredEntries.isEmpty()) {
            item {
                Text(
                    if (entries.isEmpty()) "No memories saved yet. Memories are explicit and user-controlled."
                    else "No memories match your search.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            items(filteredEntries, key = { it.id }) { entry ->
                MemoryCard(
                    entry = entry,
                    onEdit = { editing = entry },
                    onDelete = {
                        repository.delete(entry.id)
                        entries.removeAll { it.id == entry.id }
                        notice = "Memory deleted."
                    }
                )
            }
        }
    }
}

@Composable
private fun MemoryCard(entry: MemoryEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(entry.category.title, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (entry.lock == MemoryLock.SEALED) {
                        Text("SEALED • exact text", color = MaterialTheme.colorScheme.tertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
            Spacer(Modifier.height(5.dp))
            Text(entry.text, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun MemoryEditDialog(
    entry: MemoryEntry,
    onDismiss: () -> Unit,
    onSave: (MemoryCategory, String) -> Unit
) {
    var text by remember(entry.id) { mutableStateOf(entry.text) }
    var category by remember(entry.id) { mutableStateOf(entry.category) }
    var menuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry.lock == MemoryLock.SEALED) "Edit sealed memory" else "Edit memory") },
        text = {
            Column {
                if (entry.lock == MemoryLock.SEALED) {
                    Text(
                        "This memory is sealed. Editing is an explicit replacement and the replacement will also be stored exactly as entered.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(9.dp))
                }
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 2, maxLines = 8)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { menuOpen = true }) { Text(category.title) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MemoryCategory.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.title) },
                            onClick = { category = option; menuOpen = false }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(category, text) }, enabled = text.isNotEmpty()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

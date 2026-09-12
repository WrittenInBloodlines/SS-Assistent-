package com.ss.assistent.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ss.assistent.assistant.DiagnosticResult
import com.ss.assistent.assistant.ModelDiagnostics
import com.ss.assistent.model.ModelInfo
import com.ss.assistent.model.ModelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ModelRepository(context) }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    var models by remember { mutableStateOf(emptyList<ModelInfo>()) }
    var importing by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var diagnostics by remember { mutableStateOf<Map<String, DiagnosticResult>>(emptyMap()) }
    var deleteTarget by remember { mutableStateOf<ModelInfo?>(null) }

    fun refresh() {
        models = repository.getModels()
    }

    fun inspectModels() {
        val current = repository.getModels()
        models = current
        checking = true
        scope.launch(Dispatchers.Default) {
            val results = current.associate { model ->
                model.id to ModelDiagnostics.inspect(context, model)
            }
            withContext(Dispatchers.Main) {
                diagnostics = results
                checking = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        inspectModels()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        error = null
        scope.launch(Dispatchers.IO) {
            runCatching { repository.importModel(uri) }
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        importing = false
                        refresh()
                        inspectModels()
                    }
                }
                .onFailure { throwable ->
                    withContext(Dispatchers.Main) {
                        importing = false
                        error = throwable.message ?: "Could not import the model."
                        refresh()
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Spacer(Modifier.height(4.dp))
        Text("Models", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Manage local GGUF models stored separately from the app.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(18.dp))

        Button(
            onClick = { picker.launch(arrayOf("application/octet-stream", "application/gzip", "*/*")) },
            enabled = !importing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (importing) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
            } else {
                Text("+ Add model")
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { inspectModels() },
            enabled = !checking && !importing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (checking) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
            } else {
                Text("Check models")
            }
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        Spacer(Modifier.height(20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (models.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(20.dp)) {
                            Text("No models yet", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Add a .gguf file from your device. It will be copied into SS Assistent storage and will not be bundled into the APK.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            items(models, key = { it.id }) { model ->
                ModelItem(
                    model = model,
                    diagnostic = diagnostics[model.id],
                    onActivate = {
                        repository.setActive(model.id)
                        refresh()
                    },
                    onDelete = {
                        repository.delete(model.id)
                        diagnostics = diagnostics - model.id
                        refresh()
                    },
                    onConfirmDelete = { deleteTarget = model }
                )
            }
        }
    }

    deleteTarget?.let { model ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete model?") },
            text = { Text("This will remove ${model.fileName} from SS Assistent storage. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    repository.delete(model.id)
                    diagnostics = diagnostics - model.id
                    deleteTarget = null
                    refresh()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ModelItem(
    model: ModelInfo,
    diagnostic: DiagnosticResult?,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(model.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(model.fileName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(
                        "${model.sizeLabel} · ${if (model.isActive) "Active" else "Ready"}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            when (diagnostic) {
                is DiagnosticResult.Ready -> {
                    Text(
                        "${diagnostic.metadata.architecture} · ${diagnostic.modelParameterLabel}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Context: ${diagnostic.contextTokens} tokens · Available memory: ${diagnostic.availableMemoryLabel}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (diagnostic.deviceLowMemory) {
                        Text(
                            "Android currently reports low memory. Generation may be unstable.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is DiagnosticResult.Error -> {
                    Text(
                        diagnostic.message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                null -> Text(
                    "Not checked yet.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!model.isActive) OutlinedButton(onClick = onActivate) { Text("Set active") }
                TextButton(onClick = onConfirmDelete) { Text("Delete") }
            }
        }
    }
}

package com.ss.assistent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ss.assistent.model.ModelRepository
import com.ss.assistent.ui.screens.AssistantScreen
import com.ss.assistent.ui.screens.ContinuityScreen
import com.ss.assistent.ui.screens.ConversationToolsScreen
import com.ss.assistent.ui.screens.MemoryScreen
import com.ss.assistent.ui.screens.ModelsScreen
import com.ss.assistent.ui.screens.SettingsScreen
import com.ss.assistent.ui.theme.SSAssistentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SSAssistentTheme { SSAssistentApp() }
        }
    }
}

private enum class AppScreen { Home, Models, Assistant, Conversation, Memory, Continuity, Settings }

@Composable
private fun SSAssistentApp() {
    var screen by remember { mutableStateOf(AppScreen.Home) }

    when (screen) {
        AppScreen.Home -> HomeScreen(
            onModels = { screen = AppScreen.Models },
            onAssistant = { screen = AppScreen.Assistant },
            onConversation = { screen = AppScreen.Conversation },
            onMemory = { screen = AppScreen.Memory },
            onContinuity = { screen = AppScreen.Continuity },
            onSettings = { screen = AppScreen.Settings }
        )
        AppScreen.Models -> ModelsScreen(onBack = { screen = AppScreen.Home })
        AppScreen.Assistant -> AssistantScreen(onBack = { screen = AppScreen.Home })
        AppScreen.Conversation -> ConversationToolsScreen(onBack = { screen = AppScreen.Home })
        AppScreen.Memory -> MemoryScreen(onBack = { screen = AppScreen.Home })
        AppScreen.Continuity -> ContinuityScreen(onBack = { screen = AppScreen.Home })
        AppScreen.Settings -> SettingsScreen(onBack = { screen = AppScreen.Home })
    }
}

@Composable
private fun HomeScreen(
    onModels: () -> Unit,
    onAssistant: () -> Unit,
    onConversation: () -> Unit,
    onMemory: () -> Unit,
    onContinuity: () -> Unit,
    onSettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val models = remember { ModelRepository(context).getModels() }
    val modelCount = models.size
    val activeModel = models.firstOrNull { it.isActive }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 34.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Header() }
            item { ModelCard(activeModel?.name, modelCount, onModels) }
            item { QuickActions(onModels, onAssistant, onConversation, onMemory, onContinuity, onSettings) }
            item { ActivityCard() }
            item { PermissionCard() }
        }
    }
}

@Composable
private fun Header() {
    Column {
        Text("SS", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(5.dp))
        Text("Assistant", color = MaterialTheme.colorScheme.onBackground, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text("Your personal assistant for your device.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
    }
}

@Composable
private fun ModelCard(modelName: String?, modelCount: Int, onOpenModels: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onOpenModels),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("AI", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(modelName ?: "No model connected", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        if (modelName != null) "Active local model" else if (modelCount > 0) "$modelCount local model${if (modelCount == 1) "" else "s"} available" else "Add a local model",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier.size(9.dp).background(
                        if (modelName != null) MaterialTheme.colorScheme.primary else Color(0xFF8A8495),
                        RoundedCornerShape(50)
                    )
                )
            }
            Spacer(Modifier.height(17.dp))
            Text("Models are stored separately on your device and are never bundled into the APK.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun QuickActions(
    onModels: () -> Unit,
    onAssistant: () -> Unit,
    onConversation: () -> Unit,
    onMemory: () -> Unit,
    onContinuity: () -> Unit,
    onSettings: () -> Unit
) {
    Column {
        SectionTitle("Quick access")
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionCard("✦", "Assistant", Modifier.weight(1f), onAssistant)
            ActionCard("▣", "Models", Modifier.weight(1f), onModels)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionCard("⌁", "Memory", Modifier.weight(1f), onMemory)
            ActionCard("◈", "Story", Modifier.weight(1f), onContinuity)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionCard("▤", "Conversation", Modifier.weight(1f), onConversation)
            ActionCard("⚙", "Settings", Modifier.weight(1f), onSettings)
        }
    }
}

@Composable
private fun ActionCard(icon: String, title: String, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(icon, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
            Spacer(Modifier.height(13.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ActivityCard() {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle("Recent activity")
            Spacer(Modifier.height(14.dp))
            Text("No actions yet", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("When your assistant later opens apps, prepares text, or performs other tasks, they will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun PermissionCard() {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))) {
        Column(Modifier.padding(20.dp)) {
            Text("You stay in control", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(7.dp))
            Text("Sending, deleting, and other important actions will require your confirmation later.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}

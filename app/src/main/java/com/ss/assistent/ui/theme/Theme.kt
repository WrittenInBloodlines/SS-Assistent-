package com.ss.assistent.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SSColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color(0xFF160F2A),
    secondary = SoftPurple,
    background = Night,
    onBackground = TextPrimary,
    surface = Color(0xFF100D18),
    onSurface = TextPrimary,
    surfaceVariant = DeepPurple,
    onSurfaceVariant = TextSecondary
)

@Composable
fun SSAssistentTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    MaterialTheme(
        colorScheme = SSColors,
        content = content
    )
}

package app.fajralarm.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightColors = darkColorScheme(
    primary = Color(0xFFF2B36B),        // first light of dawn
    onPrimary = Color(0xFF2A1700),
    primaryContainer = Color(0xFF3B2A16),
    onPrimaryContainer = Color(0xFFFFDDB6),
    secondary = Color(0xFF9DB8E8),
    onSecondary = Color(0xFF0B1F3E),
    background = Color(0xFF0F1B2D),
    onBackground = Color(0xFFE7ECF5),
    surface = Color(0xFF0F1B2D),
    onSurface = Color(0xFFE7ECF5),
    surfaceVariant = Color(0xFF1C2B44),
    onSurfaceVariant = Color(0xFFB4C0D4),
    surfaceContainer = Color(0xFF16243A),
    surfaceContainerHigh = Color(0xFF1C2B44),
    outline = Color(0xFF4A5B78),
    error = Color(0xFFFFB4AB),
)

/** A single dark, night-sky theme: the app is mostly used in the dark. */
@Composable
fun FajrTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightColors, content = content)
}

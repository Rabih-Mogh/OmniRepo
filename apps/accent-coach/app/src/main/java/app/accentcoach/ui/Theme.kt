package app.accentcoach.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.accentcoach.core.Verdict

private val Colors = lightColorScheme(
    primary = Color(0xFF1F4E5A),            // deep teal, as in the icon
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E6E8),
    onPrimaryContainer = Color(0xFF0B2A31),
    secondary = Color(0xFFF08A5D),          // coral sound wave
    onSecondary = Color(0xFF3A1606),
    secondaryContainer = Color(0xFFFFDCCB),
    onSecondaryContainer = Color(0xFF3A1606),
    background = Color(0xFFF7F4EE),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFF7F4EE),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFE8E3DA),
    onSurfaceVariant = Color(0xFF5B5650),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDFBF7),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1EDE6),
    surfaceContainerHighest = Color(0xFFE8E3DA),
    outline = Color(0xFFB5AEA4),
    outlineVariant = Color(0xFFDDD7CD),
    error = Color(0xFFB3261E),
)

/** How each verdict is coloured wherever phonemes are shown. */
fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.OK -> Color(0xFF2E7D4F)
    Verdict.CLOSE -> Color(0xFFB7791F)
    Verdict.WRONG -> Color(0xFFC8443A)
    Verdict.MISSING -> Color(0xFF9A948C)
    Verdict.EXTRA -> Color(0xFFB7791F)
}

@Composable
fun AccentCoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

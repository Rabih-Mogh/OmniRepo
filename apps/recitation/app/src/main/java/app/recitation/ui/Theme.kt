package app.recitation.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.recitation.R

// Every role is set: anything left out falls back to Material's purple baseline.

/** Daytime: green on near-white. */
private val Green = lightColorScheme(
    primary = Color(0xFF1B6B4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EEDD),
    onPrimaryContainer = Color(0xFF07301F),
    inversePrimary = Color(0xFF8AD6AE),
    secondary = Color(0xFF4E6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D7),
    onSecondaryContainer = Color(0xFF0B1F15),
    tertiary = Color(0xFF3C6471),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC0E9F8),
    onTertiaryContainer = Color(0xFF001F28),
    background = Color(0xFFFBFDF8),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFBFDF8),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF56615A),
    surfaceTint = Color(0xFF1B6B4A),
    inverseSurface = Color(0xFF2E312F),
    inverseOnSurface = Color(0xFFEFF1ED),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF8A958C),
    outlineVariant = Color(0xFFD3DCD4),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBFDF8),
    surfaceDim = Color(0xFFDBDEDA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F7F2),
    surfaceContainer = Color(0xFFEEF3ED),
    surfaceContainerHigh = Color(0xFFE8EEE8),
    surfaceContainerHighest = Color(0xFFE2E9E2),
)

/** Night: gold on deep blue-black. */
private val Gold = darkColorScheme(
    primary = Color(0xFFF2C14E),
    onPrimary = Color(0xFF3A2A00),
    primaryContainer = Color(0xFF4A3A10),
    onPrimaryContainer = Color(0xFFFFE08A),
    inversePrimary = Color(0xFF7A5A12),
    secondary = Color(0xFFD8C7A0),
    onSecondary = Color(0xFF3A2F12),
    secondaryContainer = Color(0xFF3D3520),
    onSecondaryContainer = Color(0xFFFFEBD0),
    tertiary = Color(0xFFE6C27A),
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF52401A),
    onTertiaryContainer = Color(0xFFFFE2A8),
    background = Color(0xFF10151F),
    onBackground = Color(0xFFE9E4D4),
    surface = Color(0xFF10151F),
    onSurface = Color(0xFFE9E4D4),
    surfaceVariant = Color(0xFF2A3040),
    onSurfaceVariant = Color(0xFFA7ADBE),
    surfaceTint = Color(0xFFF2C14E),
    inverseSurface = Color(0xFFE9E4D4),
    inverseOnSurface = Color(0xFF1B2030),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A17),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF6B7285),
    outlineVariant = Color(0xFF30374A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF30374A),
    surfaceDim = Color(0xFF10151F),
    surfaceContainerLowest = Color(0xFF0B0F17),
    surfaceContainerLow = Color(0xFF151B27),
    surfaceContainer = Color(0xFF19202D),
    surfaceContainerHigh = Color(0xFF212938),
    surfaceContainerHighest = Color(0xFF2A3344),
)

/** Amiri Quran (SIL Open Font License): drawn for the Uthmani script and all its small signs. */
val QuranFont = FontFamily(Font(R.font.amiri_quran))

/** Green by day and gold by night, following the phone's dark mode. */
@Composable
fun RecitationTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Gold else Green, content = content)
}

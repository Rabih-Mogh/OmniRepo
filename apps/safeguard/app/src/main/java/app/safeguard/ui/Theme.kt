package app.safeguard.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Every role is set: anything left out falls back to Material's purple baseline.
// Green for what is in place, amber for what is waiting, red for what needs doing.
private val Day = lightColorScheme(
    primary = Color(0xFF145C53),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFE8DF),
    onPrimaryContainer = Color(0xFF00201C),
    inversePrimary = Color(0xFF84D5C7),
    secondary = Color(0xFF4A635E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E1),
    onSecondaryContainer = Color(0xFF06201C),
    tertiary = Color(0xFF8A5A14),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFF4F7F6),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE5E2),
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceTint = Color(0xFF145C53),
    inverseSurface = Color(0xFF2B3231),
    inverseOnSurface = Color(0xFFECF2F0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF4F7F6),
    surfaceDim = Color(0xFFD4DBD9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF2F0),
    surfaceContainer = Color(0xFFE8EDEB),
    surfaceContainerHigh = Color(0xFFE2E8E6),
    surfaceContainerHighest = Color(0xFFDCE3E1),
)

private val Night = darkColorScheme(
    primary = Color(0xFF84D5C7),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF0B4F47),
    onPrimaryContainer = Color(0xFFA0F2E3),
    inversePrimary = Color(0xFF145C53),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E1),
    tertiary = Color(0xFFF5BD6F),
    onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFF643F00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF0C1514),
    onBackground = Color(0xFFDCE4E2),
    surface = Color(0xFF0C1514),
    onSurface = Color(0xFFDCE4E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceTint = Color(0xFF84D5C7),
    inverseSurface = Color(0xFFDCE4E2),
    inverseOnSurface = Color(0xFF2B3231),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF899391),
    outlineVariant = Color(0xFF3F4947),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF323B39),
    surfaceDim = Color(0xFF0C1514),
    surfaceContainerLowest = Color(0xFF070F0E),
    surfaceContainerLow = Color(0xFF141D1C),
    surfaceContainer = Color(0xFF182120),
    surfaceContainerHigh = Color(0xFF222C2A),
    surfaceContainerHighest = Color(0xFF2D3735),
)

/** Light or dark as the phone is set. */
@Composable
fun SafeguardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Night else Day, content = content)
}

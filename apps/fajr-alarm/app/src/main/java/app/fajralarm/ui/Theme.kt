package app.fajralarm.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Every role is set: anything left out falls back to Material's purple baseline,
// which is what made chips, switches and dialogs look off-palette.
private val NightColors = darkColorScheme(
    primary = Color(0xFFFFD27A),            // golden horizon, as in the icon
    onPrimary = Color(0xFF1A2D52),
    primaryContainer = Color(0xFF4D3B1C),
    onPrimaryContainer = Color(0xFFFFE59A),
    inversePrimary = Color(0xFF7A5A12),
    secondary = Color(0xFFC9BCEB),          // violet top of the sky
    onSecondary = Color(0xFF2A2150),
    secondaryContainer = Color(0xFF3A3262),
    onSecondaryContainer = Color(0xFFE6DEFF),
    tertiary = Color(0xFFE6B3C9),           // the pink band before sunrise
    onTertiary = Color(0xFF3A1F2C),
    tertiaryContainer = Color(0xFF55354A),
    onTertiaryContainer = Color(0xFFFFD8E8),
    background = Color(0xFF0E1830),
    onBackground = Color(0xFFE6EBF4),
    surface = Color(0xFF0E1830),
    onSurface = Color(0xFFE6EBF4),
    surfaceVariant = Color(0xFF242C4C),
    onSurfaceVariant = Color(0xFFBCB8D4),
    surfaceTint = Color(0xFFFFD27A),
    inverseSurface = Color(0xFFE6EBF4),
    inverseOnSurface = Color(0xFF1B2638),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A17),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF5E5F84),
    outlineVariant = Color(0xFF2E3558),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2E3558),
    surfaceDim = Color(0xFF0E1830),
    surfaceContainerLowest = Color(0xFF080F20),
    surfaceContainerLow = Color(0xFF131D38),
    surfaceContainer = Color(0xFF17223F),
    surfaceContainerHigh = Color(0xFF1E2948),
    surfaceContainerHighest = Color(0xFF263152),
)

private val base = Typography()

private val FajrTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Normal),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
)

private val FajrShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Night sky fading to dawn, behind the ringing screen. Dark enough for light text. */
val DawnGradient = Brush.verticalGradient(
    0f to Color(0xFF1A2D52),
    0.55f to Color(0xFF4A3E6E),
    1f to Color(0xFF9A6478),
)

/**
 * The launcher icon's sky behind the next-alarm card, without the icon's violet top
 * so [Silhouette] text stays readable from the first line down.
 */
val GoldenMorning = Brush.verticalGradient(
    0f to Color(0xFFE6B3C9),
    0.55f to Color(0xFFFFC98A),
    1f to Color(0xFFFFE59A),
)

/** The icon's mosque-and-minarets navy, used for text on [GoldenMorning]. */
val Silhouette = Color(0xFF1A2D52)

/** A single dark, night-sky theme: the app is mostly used in the dark. */
@Composable
fun FajrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightColors,
        typography = FajrTypography,
        shapes = FajrShapes,
        content = content,
    )
}

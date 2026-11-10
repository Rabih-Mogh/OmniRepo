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
    primary = Color(0xFFF5B96E),            // first light of dawn
    onPrimary = Color(0xFF2B1700),
    primaryContainer = Color(0xFF4A3216),
    onPrimaryContainer = Color(0xFFFFDDB8),
    inversePrimary = Color(0xFF85531B),
    secondary = Color(0xFFA9C3F0),          // pre-dawn sky
    onSecondary = Color(0xFF0E2546),
    secondaryContainer = Color(0xFF243A5E),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = Color(0xFFE6B3C9),           // the pink band before sunrise
    onTertiary = Color(0xFF3A1F2C),
    tertiaryContainer = Color(0xFF55354A),
    onTertiaryContainer = Color(0xFFFFD8E8),
    background = Color(0xFF0A1322),
    onBackground = Color(0xFFE6EBF4),
    surface = Color(0xFF0A1322),
    onSurface = Color(0xFFE6EBF4),
    surfaceVariant = Color(0xFF1E2C43),
    onSurfaceVariant = Color(0xFFB0BCD0),
    surfaceTint = Color(0xFFF5B96E),
    inverseSurface = Color(0xFFE6EBF4),
    inverseOnSurface = Color(0xFF1B2638),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A17),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF56657E),
    outlineVariant = Color(0xFF2A3850),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A3850),
    surfaceDim = Color(0xFF0A1322),
    surfaceContainerLowest = Color(0xFF060D18),
    surfaceContainerLow = Color(0xFF0F1A2C),
    surfaceContainer = Color(0xFF132036),
    surfaceContainerHigh = Color(0xFF1A2840),
    surfaceContainerHighest = Color(0xFF22314B),
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

/** Night sky fading to dawn, behind the next-alarm card and the ringing screen. */
val DawnGradient = Brush.verticalGradient(
    0f to Color(0xFF1A2D52),
    0.55f to Color(0xFF3A3560),
    1f to Color(0xFF8A5A4E),
)

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

package app.recitation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.recitation.R

// Every role is set: anything left out falls back to Material's purple baseline.

/** Daytime: green on white. */
private val Green = lightColorScheme(
    primary = Color(0xFF1B6B4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EEDD),
    onPrimaryContainer = Color(0xFF07301F),
    inversePrimary = Color(0xFF8AD6AE),
    secondary = Color(0xFF4E6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6EADF),
    onSecondaryContainer = Color(0xFF0B1F15),
    tertiary = Color(0xFF3C6471),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC0E9F8),
    onTertiaryContainer = Color(0xFF001F28),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF14201B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14201B),
    surfaceVariant = Color(0xFFDDE6E0),
    onSurfaceVariant = Color(0xFF56615A),
    surfaceTint = Color(0xFF1B6B4A),
    inverseSurface = Color(0xFF2E312F),
    inverseOnSurface = Color(0xFFEFF1ED),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF8A958C),
    outlineVariant = Color(0xFFC9D6CE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDBDEDA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F8F5),
    surfaceContainer = Color(0xFFEEF4F0),
    surfaceContainerHigh = Color(0xFFE7EFEA),
    surfaceContainerHighest = Color(0xFFE0EAE4),
)

/** Night: gold on black, with neutral greys and no tint of any colour in them. */
private val Gold = darkColorScheme(
    primary = Color(0xFFF2C14E),
    onPrimary = Color(0xFF3A2A00),
    primaryContainer = Color(0xFF3A3214),
    onPrimaryContainer = Color(0xFFFFE08A),
    inversePrimary = Color(0xFF7A5A12),
    secondary = Color(0xFFD8C7A0),
    onSecondary = Color(0xFF3A2F12),
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color(0xFFEDE6D3),
    tertiary = Color(0xFFE6C27A),
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF52401A),
    onTertiaryContainer = Color(0xFFFFE2A8),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDE6D3),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEDE6D3),
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFB4B0A6),
    surfaceTint = Color(0xFFF2C14E),
    inverseSurface = Color(0xFFEDE6D3),
    inverseOnSurface = Color(0xFF121212),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A17),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF605C52),
    outlineVariant = Color(0xFF262626),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF262626),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0E0E0E),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1C1C1C),
)

/** Amiri Quran (SIL Open Font License): drawn for the Uthmani script and all its small signs. */
val QuranFont = FontFamily(Font(R.font.amiri_quran))

/**
 * KFGQPC Uthmanic Script HAFS, the King Fahd Complex's own font for the text of the Madinah
 * mushaf (free to use, copy and distribute unchanged; see licenses/UthmanicHafs-KFGQPC.txt).
 */
val MadinahFont = FontFamily(Font(R.font.uthmanic_hafs))

/** Aref Ruqaa (SIL Open Font License): a flowing calligraphic hand, for the surahs' names. */
val CalligraphyFont = FontFamily(Font(R.font.aref_ruqaa_bold))

/**
 * This colour at [strength] over [background], as one solid colour. Arabic letters overlap
 * where they join, so see-through text shows darker patches at every join; text that
 * should look fainter is given a solid, paler colour instead.
 */
fun Color.faded(strength: Float, background: Color): Color = copy(alpha = strength).compositeOver(background)

/** The band across the top of every screen, with a gold rule under it. */
class BannerColors(val background: Color, val content: Color, val rule: Color) {
    /** For the second line on the banner: fainter than [content], and solid. */
    val quiet: Color get() = content.faded(0.8f, background)
}

private val DayBanner = BannerColors(background = Color(0xFF1B6B4A), content = Color(0xFFFFF7E0), rule = Color(0xFFF2C14E))
private val NightBanner = BannerColors(background = Color(0xFF0B0B0B), content = Color(0xFFF2C14E), rule = Color(0xFFF2C14E))

val LocalBanner = staticCompositionLocalOf { DayBanner }

/**
 * The colour the letter being uttered turns. It has to stand out from the ink of the word
 * round it at a glance, so it is neither the ink's colour nor the frame's: a deep amber on
 * the white page, a bright one on the black.
 */
private val DayHighlight = Color(0xFFE07A00)
private val NightHighlight = Color(0xFFFF9D1A)

val LocalHighlight = staticCompositionLocalOf { DayHighlight }

/** Green by day and gold by night, following the phone's dark mode. */
@Composable
fun RecitationTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBanner provides if (dark) NightBanner else DayBanner, LocalHighlight provides if (dark) NightHighlight else DayHighlight) {
        MaterialTheme(colorScheme = if (dark) Gold else Green, content = content)
    }
}

/**
 * The band at the top of a screen: [content] sits on the banner colour, clear of the
 * status bar, with the gold rule beneath.
 */
@Composable
fun Banner(content: @Composable () -> Unit) {
    val banner = LocalBanner.current
    Column(Modifier.fillMaxWidth().background(banner.background)) {
        Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).fillMaxWidth()) {
            CompositionLocalProvider(LocalContentColor provides banner.content, content = content)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(banner.rule))
    }
}

/**
 * The same band at the bottom of a screen, rule on top: where the player and other bars sit,
 * so they wear the theme's colours. [clearOfSystemBar] keeps it above the phone's own
 * navigation bar when nothing else already does.
 */
@Composable
fun BottomBand(clearOfSystemBar: Boolean = true, content: @Composable () -> Unit) {
    val banner = LocalBanner.current
    Column(Modifier.fillMaxWidth().background(banner.background)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(banner.rule))
        Box((if (clearOfSystemBar) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier).fillMaxWidth()) {
            CompositionLocalProvider(LocalContentColor provides banner.content, content = content)
        }
    }
}

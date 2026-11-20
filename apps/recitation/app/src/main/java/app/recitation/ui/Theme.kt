package app.recitation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
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
import app.recitation.core.Family

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

/**
 * The bars across the top and bottom of every screen. They are quiet: they take the page's
 * own colour and are set off from it by a thin gold rule. [content] is the text and the
 * icons on a bar; [rule] is the gold of the rule, and of the few things on a bar that are
 * solid gold (the play button); [onRule] is what is drawn on that gold.
 */
class BannerColors(val background: Color, val content: Color, val rule: Color, val onRule: Color) {
    /** For the second line on the banner: fainter than [content], and solid. */
    val quiet: Color get() = content.faded(0.8f, background)
}

private val DayBanner = BannerColors(background = Color(0xFFFFFFFF), content = Color(0xFF1B6B4A), rule = Color(0xFFF2C14E), onRule = Color(0xFF1B6B4A))
private val NightBanner = BannerColors(background = Color(0xFF000000), content = Color(0xFFF2C14E), rule = Color(0xFFF2C14E), onRule = Color(0xFF000000))

val LocalBanner = staticCompositionLocalOf { DayBanner }

/**
 * The colour the letter being uttered turns. It has to stand out from the ink of the word
 * round it at a glance. It is gold in both themes: a deep gold on the white page, where
 * the pale gold of the rules would be lost, and on the black a gold richer than the cream
 * of the ink.
 */
private val DayHighlight = Color(0xFFC9980B)
private val NightHighlight = Color(0xFFFFC61A)

val LocalHighlight = staticCompositionLocalOf { DayHighlight }

/**
 * The colours of the rules of recitation: one to a family of rules. [ink] is the colour a
 * letter under a rule is written in; [light] the colour it turns while it is being uttered.
 * They follow the usual colours of a tajweed mushaf (green for sound held through the
 * nose, reds for long vowels, blue for the echo, grey for what isn't sounded), except that
 * the long vowel that may be drawn out is purple rather than orange, to stay clear of the
 * gold a letter without a rule is lit in.
 */
class TajweedColors(private val inks: Map<Family, Color>, private val lights: Map<Family, Color>) {
    fun ink(family: Family): Color = inks.getValue(family)
    fun light(family: Family): Color = lights.getValue(family)
}

// Eight families, as in the mushafs printed with the rules in colour: the long vowels run from
// deep red (longest) through orange-red and purple to brown (shortest); the nasal sounds are
// green, the echo light blue, the heavy letters dark blue, and what isn't said is grey.
private val DayTajweed = TajweedColors(
    inks = mapOf(
        Family.NASAL to Color(0xFF1E8E3E), Family.MADD_LAZIM to Color(0xFFB71C1C), Family.MADD_WAJIB to Color(0xFFF4511E),
        Family.MADD_JAIZ to Color(0xFF8E24AA), Family.MADD_TABII to Color(0xFF8D5524), Family.ECHO to Color(0xFF0288D1),
        Family.HEAVY to Color(0xFF283593), Family.QUIET to Color(0xFF9AA0A6),
    ),
    lights = mapOf(
        Family.NASAL to Color(0xFF12A150), Family.MADD_LAZIM to Color(0xFFD32F2F), Family.MADD_WAJIB to Color(0xFFFF7043),
        Family.MADD_JAIZ to Color(0xFFAB47BC), Family.MADD_TABII to Color(0xFFA9682E), Family.ECHO to Color(0xFF039BE5),
        Family.HEAVY to Color(0xFF3F51B5), Family.QUIET to Color(0xFF80868B),
    ),
)
private val NightTajweed = TajweedColors(
    inks = mapOf(
        Family.NASAL to Color(0xFF66D17A), Family.MADD_LAZIM to Color(0xFFFF6E6E), Family.MADD_WAJIB to Color(0xFFFFA270),
        Family.MADD_JAIZ to Color(0xFFCE93D8), Family.MADD_TABII to Color(0xFFC8A27A), Family.ECHO to Color(0xFF4FC3F7),
        Family.HEAVY to Color(0xFF9FA8DA), Family.QUIET to Color(0xFF777777),
    ),
    lights = mapOf(
        Family.NASAL to Color(0xFF8CF5A2), Family.MADD_LAZIM to Color(0xFFFF8A80), Family.MADD_WAJIB to Color(0xFFFFB995),
        Family.MADD_JAIZ to Color(0xFFE6B4F0), Family.MADD_TABII to Color(0xFFDDB892), Family.ECHO to Color(0xFF9ADFFF),
        Family.HEAVY to Color(0xFFBEC5F0), Family.QUIET to Color(0xFF9A9A9A),
    ),
)

val LocalTajweed = staticCompositionLocalOf { DayTajweed }

/** Green by day and gold by night, following the phone's dark mode. */
@Composable
fun RecitationTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBanner provides if (dark) NightBanner else DayBanner,
        LocalHighlight provides if (dark) NightHighlight else DayHighlight,
        LocalTajweed provides if (dark) NightTajweed else DayTajweed,
    ) {
        MaterialTheme(colorScheme = if (dark) Gold else Green, content = content)
    }
}

/**
 * Keeps everything clear of whatever cuts into the screen at its sides: the camera's hole
 * or notch when the phone is held on its side, and the phone's own buttons when they sit
 * there. The app draws from edge to edge, so without this a word at the end of a line can
 * lie under the camera. The page's colour carries on underneath.
 */
@Composable
fun ClearOfTheSides(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) { content() }
}

/**
 * The band at the top of a screen: [content] sits on the banner colour, clear of the
 * status bar, with the gold rule beneath.
 */
@Composable
fun Banner(content: @Composable () -> Unit) {
    val banner = LocalBanner.current
    Column(Modifier.fillMaxWidth().background(banner.background)) {
        // Clear of the status bar, and of a camera cut-out if that reaches lower than the bar.
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).fillMaxWidth()) {
            CompositionLocalProvider(LocalContentColor provides banner.content, content = content)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(banner.rule))
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
        Box(Modifier.fillMaxWidth().height(1.dp).background(banner.rule))
        Box((if (clearOfSystemBar) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier).fillMaxWidth()) {
            CompositionLocalProvider(LocalContentColor provides banner.content, content = content)
        }
    }
}

package app.recitation.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.recitation.R
import app.recitation.core.Family

// Every role is set: anything left out falls back to Material's purple baseline.

/** Daytime: black on white, with one colour, a deep teal. */
private val Day = lightColorScheme(
    primary = Color(0xFF0E5E6F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5EEF2),
    onPrimaryContainer = Color(0xFF05303A),
    inversePrimary = Color(0xFF5EDCCB),
    secondary = Color(0xFF5B6470),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFF3F5),
    onSecondaryContainer = Color(0xFF0B0F14),
    tertiary = Color(0xFF0E5E6F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5EEF2),
    onTertiaryContainer = Color(0xFF05303A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0B0F14),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B0F14),
    surfaceVariant = Color(0xFFE7EBEF),
    onSurfaceVariant = Color(0xFF5B6470),
    surfaceTint = Color(0xFF0E5E6F),
    inverseSurface = Color(0xFF0B0F14),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF8A939E),
    outlineVariant = Color(0xFFE1E5EA),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE7EBEF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F6F8),
    surfaceContainer = Color(0xFFEEF1F4),
    surfaceContainerHigh = Color(0xFFE7EBEF),
    surfaceContainerHighest = Color(0xFFE0E5EA),
)

/** Night: white on black, the one colour a pale mint, and neutral greys. */
private val Night = darkColorScheme(
    primary = Color(0xFF5EDCCB),
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF10332F),
    onPrimaryContainer = Color(0xFFB8F5EC),
    inversePrimary = Color(0xFF0E5E6F),
    secondary = Color(0xFFA7ADB5),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF15181B),
    onSecondaryContainer = Color(0xFFF5F5F5),
    tertiary = Color(0xFF5EDCCB),
    onTertiary = Color(0xFF00201C),
    tertiaryContainer = Color(0xFF10332F),
    onTertiaryContainer = Color(0xFFB8F5EC),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1B1F23),
    onSurfaceVariant = Color(0xFFA7ADB5),
    surfaceTint = Color(0xFF5EDCCB),
    inverseSurface = Color(0xFFF5F5F5),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A17),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF5B636C),
    outlineVariant = Color(0xFF22272C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF22272C),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0D0F11),
    surfaceContainer = Color(0xFF15181B),
    surfaceContainerHigh = Color(0xFF1B1F23),
    surfaceContainerHighest = Color(0xFF22272C),
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
 * The bars across the top and bottom of every screen. They are a colour of their own, so
 * the page with the verses stands apart between them: the app's teal by day, a dark teal
 * by night. [content] is the text and the icons on a bar; [accent] is the colour of the
 * few solid things on a bar (the play button, the tab that is showing), and [onAccent]
 * what is drawn on those.
 */
class BannerColors(val background: Color, val content: Color, val accent: Color, val onAccent: Color) {
    /** For the second line on the banner: fainter than [content], and solid. */
    val quiet: Color get() = content.faded(0.8f, background)
}

private val DayBanner = BannerColors(background = Color(0xFF0E5E6F), content = Color(0xFFFFFFFF), accent = Color(0xFFFFFFFF), onAccent = Color(0xFF0E5E6F))
private val NightBanner = BannerColors(background = Color(0xFF0F2B31), content = Color(0xFFF5F5F5), accent = Color(0xFF5EDCCB), onAccent = Color(0xFF00201C))

val LocalBanner = staticCompositionLocalOf { DayBanner }

/**
 * The colour the letter being uttered turns. It has to stand out from the ink of the word
 * round it at a glance, and from the teal of everything else that is coloured: it is
 * amber, deep on the white page and bright on the black.
 */
private val DayHighlight = Color(0xFFC25E00)
private val NightHighlight = Color(0xFFFFB627)

val LocalHighlight = staticCompositionLocalOf { DayHighlight }

/**
 * The colours of the rules of recitation: one to a family of rules. [ink] is the colour a
 * letter under a rule is written in; [light] the colour it turns while it is being uttered.
 * They follow the usual colours of a tajweed mushaf (green for sound held through the
 * nose, reds for long vowels, blue for the echo, grey for what isn't sounded), except that
 * the long vowel that may be drawn out is purple rather than orange, to stay clear of the
 * amber a letter without a rule is lit in.
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

/** Headings are heavier and a little larger than Material's own, so a screen's title is found at a glance. */
private val Lettering = Typography().run {
    fun TextStyle.title() = copy(fontWeight = FontWeight.Bold, fontSize = fontSize * 1.08f, lineHeight = lineHeight * 1.08f)
    copy(
        displayLarge = displayLarge.title(), displayMedium = displayMedium.title(), displaySmall = displaySmall.title(),
        headlineLarge = headlineLarge.title(), headlineMedium = headlineMedium.title(), headlineSmall = headlineSmall.title(),
        titleLarge = titleLarge.title(), titleMedium = titleMedium.title(), titleSmall = titleSmall.title(),
    )
}

/** Cards, menus and dialogs have well-rounded corners. */
private val Corners = Shapes(RoundedCornerShape(10.dp), RoundedCornerShape(14.dp), RoundedCornerShape(20.dp), RoundedCornerShape(24.dp), RoundedCornerShape(28.dp))

/** Teal on white by day and mint on black by night, following the phone's dark mode. */
@Composable
fun RecitationTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBanner provides if (dark) NightBanner else DayBanner,
        LocalHighlight provides if (dark) NightHighlight else DayHighlight,
        LocalTajweed provides if (dark) NightTajweed else DayTajweed,
    ) {
        MaterialTheme(colorScheme = if (dark) Night else Day, typography = Lettering, shapes = Corners, content = content)
    }
}

/**
 * Keeps everything clear of whatever cuts into the screen at its sides: the camera's hole
 * or notch when the phone is held on its side, and the phone's own buttons when they sit
 * there. The app draws from edge to edge, so without this a word at the end of a line can
 * lie under the camera. Those strips at the sides take the bars' colour, so the bars at
 * the top and bottom run on round the camera instead of stopping short of it.
 */
@Composable
fun ClearOfTheSides(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(LocalBanner.current.background).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/**
 * Whether a screen's top bar has slid away to leave its page the room. Only the bar's own
 * row goes; the strip behind the phone's status bar stays, so the clock and the page never
 * run into each other.
 */
@Stable
class SlidingBar {
    /** How tall the whole bar is, and the strip of it behind the status bar that stays, in px. */
    var height by mutableIntStateOf(0)
        internal set
    var strip by mutableIntStateOf(0)
        internal set

    /** Whether the bar's row is out of sight. */
    var away by mutableStateOf(false)
}

/**
 * The band at the top of a screen: [content] sits on the bars' colour, clear of the status
 * bar. Given a [sliding], its row slides up out of sight when that says so, and back.
 */
@Composable
fun Banner(sliding: SlidingBar? = null, content: @Composable () -> Unit) {
    val banner = LocalBanner.current
    // Clear of the status bar, and of a camera cut-out if that reaches lower than the bar.
    val top = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
    val gone by animateFloatAsState(if (sliding?.away == true) 1f else 0f, tween(220), label = "bar")
    var row by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxWidth().onSizeChanged { sliding?.height = it.height }) {
        // Moved as it is drawn, so nothing is laid out again while it slides.
        Box(Modifier.fillMaxWidth().graphicsLayer { translationY = -gone * row }.background(banner.background).windowInsetsPadding(top)) {
            Box(Modifier.fillMaxWidth().onSizeChanged { row = it.height }) {
                CompositionLocalProvider(LocalContentColor provides banner.content, content = content)
            }
        }
        // What stays when the row has gone. It takes the touches that land on it, so a
        // button that has slid up behind it can't be pressed through the status bar.
        if (sliding != null) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(top).background(banner.background).onSizeChanged { sliding.strip = it.height }.pointerInput(Unit) {})
        }
    }
}

/**
 * How many bars now reach down under the phone's own navigation buttons. While there is
 * one, those buttons are drawn light, to show on the bar; with none they sit on the page
 * and are drawn to show on that (see MainActivity).
 */
object BandsBelow {
    var count by mutableIntStateOf(0)
        private set

    @Composable
    fun One() {
        DisposableEffect(Unit) {
            count++
            onDispose { count-- }
        }
    }
}

/**
 * The same band at the bottom of a screen: where the player and other bars sit, so they
 * wear the bars' colours. [clearOfSystemBar] keeps it above the phone's own navigation bar
 * when nothing else already does.
 */
@Composable
fun BottomBand(clearOfSystemBar: Boolean = true, content: @Composable () -> Unit) {
    val banner = LocalBanner.current
    if (clearOfSystemBar) BandsBelow.One()
    Box((if (clearOfSystemBar) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier).fillMaxWidth().background(banner.background)) {
        CompositionLocalProvider(LocalContentColor provides banner.content, content = content)
    }
}

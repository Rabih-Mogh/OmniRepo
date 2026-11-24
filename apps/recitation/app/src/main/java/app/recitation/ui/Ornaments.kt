package app.recitation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recitation.R
import app.recitation.core.Surah
import app.recitation.data.SurahNames

/** The surah-name calligraphy fetched from Quran.com, once it is on the phone; null before. */
val LocalSurahNames = staticCompositionLocalOf<FontFamily?> { null }

/**
 * A surah's name as a title, in calligraphy: the Thuluth of a mushaf's headings when that
 * font has been fetched, the bundled Ruqaa hand until then. [withWord] puts "surah" before
 * the name. Screen readers get the plain name either way.
 */
@Composable
fun SurahTitle(surah: Surah, size: TextUnit, color: Color, modifier: Modifier = Modifier, withWord: Boolean = false) {
    val thuluth = LocalSurahNames.current
    val plain = if (withWord) stringResource(R.string.surah_title, surah.arabic) else surah.arabic
    if (thuluth != null) {
        // The glyphs are private-use characters, which read left to right: the name first, the word after it.
        Text(
            SurahNames.glyph(surah.number) + if (withWord) SurahNames.SURAH_WORD else "",
            modifier.semantics { contentDescription = plain },
            fontFamily = thuluth, fontSize = size, color = color, maxLines = 1, softWrap = false,
            style = TextStyle(textDirection = TextDirection.Ltr),
        )
    } else {
        Text(
            plain, modifier.semantics { contentDescription = plain },
            fontFamily = CalligraphyFont, fontSize = size * 0.8f, color = color, maxLines = 1, softWrap = false,
        )
    }
}

/**
 * A number inside the eight-point star made of two crossed squares: a verse's number. The
 * star is solid, in [ground] (the page's colour unless told otherwise), so whatever lies
 * behind it doesn't show through between its lines.
 */
@Composable
fun StarNumber(
    number: Int,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    ground: Color = MaterialTheme.colorScheme.background,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val side = this.size.minDimension * 0.62f
            val corner = Offset((this.size.width - side) / 2, (this.size.height - side) / 2)
            val stroke = Stroke(1.3.dp.toPx())
            drawRect(ground, corner, Size(side, side))
            rotate(45f) { drawRect(ground, corner, Size(side, side)) }
            drawRect(color, corner, Size(side, side), style = stroke)
            rotate(45f) { drawRect(color, corner, Size(side, side), style = stroke) }
        }
        Text(stringResource(R.string.number, number), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * A surah's number in the list of surahs: plain, and lighter than a heading, so the
 * surah's name beside it is what the eye goes to. Where the surah has a
 * recitation on the phone ([recitation]) it is in the app's colour, where it has none it
 * is grey. Under it is a mark for where its recitation stands: the bars of sound once one
 * has been listened to the end; the first bars and dots when the listening was left off
 * ([heard] is false); an hourglass while it waits its turn ([queued]); and the picture of
 * the work in its ring while it is being worked on ([working], which [workSays] puts in words).
 */
@Composable
fun SurahNumber(
    number: Int,
    modifier: Modifier = Modifier,
    recitation: Boolean = false,
    heard: Boolean = true,
    queued: Boolean = false,
    working: Picture? = null,
    workSays: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val ink = if (recitation) colors.primary else colors.outline
    val label = when {
        working != null -> workSays
        queued -> stringResource(R.string.queued)
        recitation && !heard -> stringResource(R.string.has_recitation_unheard)
        recitation -> stringResource(R.string.has_recitation)
        else -> null
    }
    Column(if (label != null) modifier.semantics { contentDescription = label } else modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.number, number), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium, fontSize = 20.sp), color = ink)
        when {
            working != null -> WorkIcon(working, null, Modifier.padding(top = 2.dp), size = 20.dp)
            queued -> Icon(AppIcons.Hourglass, null, Modifier.size(14.dp), tint = ink)
            recitation -> Icon(if (heard) AppIcons.Sound else AppIcons.SoundPart, null, Modifier.size(14.dp), tint = ink)
        }
    }
}

/** A hairline as wide as it is given room: one stands above a surah's heading on its title page and one below it. */
@Composable
fun HeadingRule(modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.height(8.dp)) { drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx()) }
}

/**
 * The app's button: an icon and a word in a frame with round ends, in the theme's colour.
 * [filled] is for the one main action on a screen. [onBand] is for buttons sitting on the
 * coloured band, which are drawn in the band's own colours.
 */
@Composable
fun ThemeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = false,
    onBand: Boolean = false,
    enabled: Boolean = true,
) {
    val band = LocalBanner.current
    val accent = if (onBand) band.accent else MaterialTheme.colorScheme.primary
    val shape = CircleShape
    val padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, maxLines = 1)
    }
    if (filled) {
        Button(
            onClick, modifier, enabled = enabled, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = if (onBand) band.onAccent else MaterialTheme.colorScheme.onPrimary,
            ),
        ) { content() }
    } else {
        OutlinedButton(
            onClick, modifier, enabled = enabled, shape = shape, contentPadding = padding,
            border = BorderStroke(1.2.dp, (if (onBand) band.content else accent).copy(alpha = if (enabled) 1f else 0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (onBand) band.content else accent),
        ) { content() }
    }
}

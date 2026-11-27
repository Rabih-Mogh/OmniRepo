package app.fajralarm.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.fajralarm.R
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.WidgetColors
import app.fajralarm.widget.WidgetPalette
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The widgets' three colours, each picked from a palette, and how solid the
 * background is, down to none at all. They start from the colours of the theme in use, so a theme can be
 * taken and one colour of it changed.
 */
@Composable
internal fun WidgetColorsEditor(settings: AlarmSettings, onChange: (WidgetColors) -> Unit) {
    val colors = WidgetPalette.colorsOf(settings)
    val clear = colors.backgroundOpacity == 0
    ColorRow(
        stringResource(R.string.widget_color_background),
        colors.background,
        transparent = clear,
        onTransparent = { onChange(colors.copy(backgroundOpacity = 0)) },
    ) { picked ->
        // A colour picked while there is no background at all brings the background back with it.
        onChange(colors.copy(background = picked, backgroundOpacity = if (clear) SOLID_AGAIN else colors.backgroundOpacity))
    }
    var opacity by remember(colors.backgroundOpacity) { mutableIntStateOf(colors.backgroundOpacity) }
    Text(stringResource(R.string.widget_color_opacity, opacity), style = MaterialTheme.typography.bodyLarge)
    Slider(
        value = opacity.toFloat(),
        onValueChange = { opacity = (it / 5f).roundToInt() * 5 },
        onValueChangeFinished = { onChange(colors.copy(backgroundOpacity = opacity)) },
        valueRange = 0f..100f,
    )
    ColorRow(stringResource(R.string.widget_color_text), colors.text) { onChange(colors.copy(text = it)) }
    ColorRow(stringResource(R.string.widget_color_highlight), colors.highlight) { onChange(colors.copy(highlight = it)) }
}

/**
 * One of the colours: its name and a dot of it; tapping opens the palette. With
 * [onTransparent] the palette also offers no colour at all, and [transparent] says
 * that is what is chosen: the dot is then an empty ring.
 */
@Composable
private fun ColorRow(
    title: String,
    color: Int,
    transparent: Boolean = false,
    onTransparent: (() -> Unit)? = null,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (transparent) Color.Transparent else opaque(color))
                .border(if (transparent) 2.dp else 1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (onTransparent != null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .border(
                                    width = if (transparent) 3.dp else 1.dp,
                                    color = if (transparent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = MaterialTheme.shapes.small,
                                )
                                .selectable(selected = transparent, role = Role.RadioButton) {
                                    onTransparent()
                                    open = false
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.widget_color_transparent), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Palette(color.takeUnless { transparent }) {
                        onPick(it)
                        open = false
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

/** The palette: a row of greys from white to black, then each hue from pale to deep. */
@Composable
private fun Palette(selected: Int?, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PALETTE.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { rgb ->
                    val chosen = rgb == selected
                    val name = "#%06X".format(Locale.US, rgb)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(opaque(rgb))
                            .border(
                                width = if (chosen) 3.dp else 1.dp,
                                color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.extraSmall,
                            )
                            .selectable(selected = chosen, role = Role.RadioButton) { onPick(rgb) }
                            .semantics { contentDescription = name },
                    )
                }
            }
        }
    }
}

/** How solid the background is made when a colour is picked for one that was transparent, in percent. */
private const val SOLID_AGAIN = 90

private fun opaque(rgb: Int) = Color(0xFF000000 or (rgb.toLong() and 0xFFFFFF))

/** Hues round the colour wheel, in degrees. */
private val HUES = listOf(0f, 28f, 48f, 90f, 135f, 170f, 200f, 220f, 250f, 285f, 330f)

/** For each row of colours: how strong and how bright, from pale at the top to deep at the bottom. */
private val SHADES = listOf(0.18f to 1f, 0.42f to 1f, 0.7f to 0.97f, 0.92f to 0.86f, 1f to 0.62f, 1f to 0.36f)

/** The colours on offer, as 0xRRGGBB: greys first, then a row for each shade. */
private val PALETTE: List<List<Int>> =
    listOf(listOf(0xFFFFFF, 0xF2F2F2, 0xD9D9D9, 0xBFBFBF, 0x999999, 0x737373, 0x4D4D4D, 0x333333, 0x1F1F1F, 0x0F0F0F, 0x000000)) +
        SHADES.map { (strength, brightness) ->
            HUES.map { hue -> AndroidColor.HSVToColor(floatArrayOf(hue, strength, brightness)) and 0xFFFFFF }
        }

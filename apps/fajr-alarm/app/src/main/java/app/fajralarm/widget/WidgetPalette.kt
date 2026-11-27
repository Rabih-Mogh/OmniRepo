package app.fajralarm.widget

import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.WidgetColors
import app.fajralarm.core.WidgetTheme

/** A colour for the phone's light mode and one for its dark mode. Every theme but "follow the phone" uses one for both. */
internal class Tone(val light: Int, val dark: Int = light) {
    constructor(light: Long, dark: Long = light) : this(light.toInt(), dark.toInt())
}

/**
 * The colours of one widget theme, kept well apart from the background: a passed time
 * is quieter, but still easy to read. They are all opaque: icons are tinted with them,
 * and a see-through tint would let the icon's own white through. Only the background fades.
 */
internal class WidgetPalette(
    val background: Tone,
    /** How solid the background is, 0 to 255. */
    val backgroundAlpha: Int,
    val text: Tone,
    /** The date, the optional times and the smaller lines. */
    val muted: Tone,
    /** A time that has been and gone. */
    val passed: Tone,
    /** The headline over the table. */
    val accent: Tone,
    /** Behind the current prayer's row. */
    val highlight: Tone = Tone(GOLD),
    /** Text and icons on that row. */
    val onGold: Tone,
    /** A voluntary prayer's icon, and the same once its time has passed. */
    val nafl: Tone,
    val naflPassed: Tone,
) {
    companion object {
        private const val GOLD = 0xFFFFD27A

        /** The app's own night sky and golden horizon. */
        private val NIGHT = WidgetPalette(
            background = Tone(0xFF17223F), backgroundAlpha = 240,
            text = Tone(0xFFF2F5FA), muted = Tone(0xFFCFCBE3), passed = Tone(0xFF9C9EBB),
            accent = Tone(0xFFFFD27A), onGold = Tone(0xFF1A2D52),
            nafl = Tone(0xFFF0BFD4), naflPassed = Tone(0xFFB093B2),
        )

        private val BLACK = WidgetPalette(
            background = Tone(0xFF000000), backgroundAlpha = 255,
            text = Tone(0xFFFFFFFF), muted = Tone(0xFFCACAD2), passed = Tone(0xFF9A9AA2),
            accent = Tone(0xFFFFD27A), onGold = Tone(0xFF1A1A1A),
            nafl = Tone(0xFFF0BFD4), naflPassed = Tone(0xFFA88898),
        )

        private val LIGHT = WidgetPalette(
            background = Tone(0xFFFBF8F1), backgroundAlpha = 245,
            text = Tone(0xFF10182F), muted = Tone(0xFF474D66), passed = Tone(0xFF6F7387),
            accent = Tone(0xFF7A4E00), onGold = Tone(0xFF10182F),
            nafl = Tone(0xFFA83A6E), naflPassed = Tone(0xFFA8668A),
        )

        /** See-through enough for a wallpaper to show, dark enough for white text to read on any of them. */
        private val GLASS = WidgetPalette(
            background = Tone(0xFF000000), backgroundAlpha = 128,
            text = Tone(0xFFFFFFFF), muted = Tone(0xFFF0F0F4), passed = Tone(0xFFC9C9D1),
            accent = Tone(0xFFFFD27A), onGold = Tone(0xFF1A2D52),
            nafl = Tone(0xFFF7CADC), naflPassed = Tone(0xFFD0B4C4),
        )

        /** White with royal blue: made to sit on a bright green and blue wallpaper with white lines. */
        private val FROST = WidgetPalette(
            background = Tone(0xFFFFFFFF), backgroundAlpha = 235,
            text = Tone(0xFF0B2A6F), muted = Tone(0xFF3F5488), passed = Tone(0xFF6878A0),
            accent = Tone(0xFF1B5FE0), highlight = Tone(0xFF1B5FE0), onGold = Tone(0xFFFFFFFF),
            nafl = Tone(0xFFB0306E), naflPassed = Tone(0xFFB67A98),
        )

        /** The same wallpaper's deep blue, with its green for the current prayer. */
        private val COBALT = WidgetPalette(
            background = Tone(0xFF0B2A6F), backgroundAlpha = 235,
            text = Tone(0xFFFFFFFF), muted = Tone(0xFFD5DEF5), passed = Tone(0xFF9FB0DA),
            accent = Tone(0xFF8BE28B), highlight = Tone(0xFF6EDB6E), onGold = Tone(0xFF06204F),
            nafl = Tone(0xFFFFC2DA), naflPassed = Tone(0xFFC29AB6),
        )

        /** [LIGHT] by day and [NIGHT] by night, colour by colour. */
        private val SYSTEM = WidgetPalette(
            background = LIGHT.background or NIGHT.background, backgroundAlpha = 242,
            text = LIGHT.text or NIGHT.text, muted = LIGHT.muted or NIGHT.muted, passed = LIGHT.passed or NIGHT.passed,
            accent = LIGHT.accent or NIGHT.accent, onGold = LIGHT.onGold or NIGHT.onGold,
            nafl = LIGHT.nafl or NIGHT.nafl, naflPassed = LIGHT.naflPassed or NIGHT.naflPassed,
        )

        private infix fun Tone.or(night: Tone) = Tone(light, night.dark)

        fun of(settings: AlarmSettings): WidgetPalette = when (settings.widgetTheme) {
            WidgetTheme.NIGHT -> NIGHT
            WidgetTheme.BLACK -> BLACK
            WidgetTheme.LIGHT -> LIGHT
            WidgetTheme.GLASS -> GLASS
            WidgetTheme.FROST -> FROST
            WidgetTheme.COBALT -> COBALT
            WidgetTheme.SYSTEM -> SYSTEM
            WidgetTheme.CUSTOM -> custom(settings.widgetColors)
        }

        /** The three colours and the background's strength in use now: the ones to start your own from. */
        fun colorsOf(settings: AlarmSettings): WidgetColors {
            if (settings.widgetTheme == WidgetTheme.CUSTOM) return settings.widgetColors
            val theme = of(settings)
            return WidgetColors(
                background = theme.background.light and 0xFFFFFF,
                backgroundOpacity = Math.round(theme.backgroundAlpha * 100 / 255f),
                text = theme.text.light and 0xFFFFFF,
                highlight = theme.highlight.light and 0xFFFFFF,
            )
        }

        /**
         * A theme from the three colours you picked. The quieter shades are the text
         * colour mixed towards the background, and what sits on the highlight is dark
         * or white, whichever reads better on it.
         */
        private fun custom(colors: WidgetColors): WidgetPalette {
            val background = OPAQUE or colors.background
            val text = OPAQUE or colors.text
            val highlight = OPAQUE or colors.highlight
            val nafl = if (luminance(background) > 0.6) 0xFFB0477A.toInt() else 0xFFE6B3C9.toInt()
            return WidgetPalette(
                background = Tone(background),
                backgroundAlpha = colors.backgroundOpacity.coerceIn(0, 100) * 255 / 100,
                text = Tone(text), muted = Tone(mix(text, background, 0.15f)), passed = Tone(mix(text, background, 0.4f)),
                accent = Tone(highlight), highlight = Tone(highlight),
                onGold = Tone(if (luminance(highlight) > 0.45) 0xFF1A2340.toInt() else 0xFFFFFFFF.toInt()),
                nafl = Tone(nafl), naflPassed = Tone(mix(nafl, background, 0.5f)),
            )
        }

        private const val OPAQUE = 0xFF000000.toInt()

        /** [from] moved [towards] the other colour by [share], 0 to 1. */
        private fun mix(from: Int, towards: Int, share: Float): Int {
            fun channel(shift: Int) =
                ((from shr shift and 0xFF) * (1 - share) + (towards shr shift and 0xFF) * share).toInt() shl shift
            return OPAQUE or channel(16) or channel(8) or channel(0)
        }

        /** How bright a colour looks, 0 to 1. */
        private fun luminance(color: Int): Double =
            (0.299 * (color shr 16 and 0xFF) + 0.587 * (color shr 8 and 0xFF) + 0.114 * (color and 0xFF)) / 255
    }
}

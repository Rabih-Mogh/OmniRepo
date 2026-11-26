package app.fajralarm.widget

import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.WidgetColors
import app.fajralarm.core.WidgetTheme

/** A colour for the phone's light mode and one for its dark mode. Every theme but "follow the phone" uses one for both. */
internal class Tone(val light: Int, val dark: Int = light) {
    constructor(light: Long, dark: Long = light) : this(light.toInt(), dark.toInt())
}

/**
 * The colours of one widget theme. They are all opaque: icons are tinted with them,
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
            text = Tone(0xFFE6EBF4), muted = Tone(0xFFBCB8D4), passed = Tone(0xFF727491),
            accent = Tone(0xFFFFD27A), onGold = Tone(0xFF1A2D52),
            nafl = Tone(0xFFE6B3C9), naflPassed = Tone(0xFF89718B),
        )

        private val BLACK = WidgetPalette(
            background = Tone(0xFF000000), backgroundAlpha = 255,
            text = Tone(0xFFF2F2F2), muted = Tone(0xFFB4B4BC), passed = Tone(0xFF6C6C74),
            accent = Tone(0xFFFFD27A), onGold = Tone(0xFF1A1A1A),
            nafl = Tone(0xFFE6B3C9), naflPassed = Tone(0xFF7D6470),
        )

        private val LIGHT = WidgetPalette(
            background = Tone(0xFFFBF8F1), backgroundAlpha = 245,
            text = Tone(0xFF1A2340), muted = Tone(0xFF565C76), passed = Tone(0xFFA3A6B5),
            accent = Tone(0xFF8A5A00), onGold = Tone(0xFF1A2340),
            nafl = Tone(0xFFB0477A), naflPassed = Tone(0xFFD3A3BB),
        )

        /** Mostly see-through, with white text for a wallpaper behind it. */
        private val GLASS = WidgetPalette(
            background = Tone(0xFF000000), backgroundAlpha = 96,
            text = Tone(0xFFFFFFFF), muted = Tone(0xFFE2E2E8), passed = Tone(0xFFA6A6AE),
            accent = Tone(0xFFFFD27A), onGold = Tone(0xFF1A2D52),
            nafl = Tone(0xFFF2C2D6), naflPassed = Tone(0xFFAC94A2),
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
            WidgetTheme.SYSTEM -> SYSTEM
            WidgetTheme.CUSTOM -> custom(settings.widgetColors)
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
                text = Tone(text), muted = Tone(mix(text, background, 0.25f)), passed = Tone(mix(text, background, 0.55f)),
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

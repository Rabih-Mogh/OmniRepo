package app.recitation.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import app.recitation.R
import app.recitation.data.IndoPakFont
import app.recitation.data.Script

/** Whether a character is a sign written on a letter (a vowel sign, a shadda, a small recitation sign) rather than a letter. */
internal fun Char.isMark(): Boolean = Character.getType(this).let {
    it == Character.NON_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
}

/**
 * The fonts the verses are written in, as Android's own drawing code wants them. They are
 * the same files the page's text is laid out with; null for a font that isn't on the phone.
 */
internal object Typefaces {
    private val files = HashMap<String, Typeface?>()

    fun of(context: Context, script: Script): Typeface? = when (script) {
        Script.MADINAH -> runCatching { ResourcesCompat.getFont(context, R.font.uthmanic_hafs) }.getOrNull()
        Script.AMIRI, Script.EVERYDAY -> runCatching { ResourcesCompat.getFont(context, R.font.amiri_quran) }.getOrNull()
        Script.INDOPAK -> IndoPakFont.file(context)?.let { file ->
            synchronized(files) { files.getOrPut(file.path) { runCatching { Typeface.createFromFile(file) }.getOrNull() } }
        }
    }
}

/**
 * Tells where a place in a word's Uthmani spelling is in the spelling that is shown. The
 * rules of recitation and the timings are worked out on the Uthmani spelling; the Madinah
 * mushaf's text is the same word with a few signs written differently (no carrier under a
 * small alif, another sign for the sukun). [exact] says the two have the same letters, so
 * every place can be found; for a script that spells the word with other letters it is
 * false, and only rough places are to be had.
 */
internal class Spelling(private val uthmani: String, shown: String) {
    private val places = IntArray(uthmani.length + 1)
    val exact: Boolean

    init {
        var j = 0
        for ((i, c) in uthmani.withIndex()) {
            places[i] = j
            when {
                j < shown.length && same(c, shown[j]) -> j++
                // The stroke that carries a small alif: the shown text may write the alif on the letter itself.
                c == CARRIER -> {}
                else -> {
                    // The shown text has a sign of its own here; the one looked for comes just after it.
                    val ahead = (j + 1..minOf(j + 2, shown.lastIndex)).firstOrNull { same(c, shown[it]) }
                    when {
                        ahead != null -> j = ahead + 1
                        j >= shown.length -> {}
                        // Another letter stands in this one's place, or another sign in this sign's.
                        !c.isMark() || shown[j].isMark() -> j++
                    }
                }
            }
        }
        places[uthmani.length] = shown.length
        exact = uthmani == shown || letters(uthmani) == letters(shown)
    }

    /** The place in the shown spelling of the place [index] in the Uthmani one. */
    fun at(index: Int): Int = places[index.coerceIn(0, uthmani.length)]

    private companion object {
        const val CARRIER = 'ـ'
        val SUKUNS = setOf('ْ', 'ۡ')
        val YAS = setOf('ي', 'ى', 'ی')
        val ALIFS = setOf('ا', 'ٱ')

        // The Madinah text marks a letter that is never sounded with the sign the Uthmani one uses for a sukun.
        fun same(a: Char, b: Char) = a == b || (a in SUKUNS && b in SUKUNS) || (a in YAS && b in YAS) || (a in ALIFS && b in ALIFS) ||
            (a == '۟' && b == 'ْ')

        /** The word's letters alone, alike spellings of one letter written the same. */
        fun letters(word: String): String = buildString {
            for (c in word) {
                if (c.isMark() || c == CARRIER || c == ' ') continue
                append(if (c in YAS) 'ي' else if (c in ALIFS) 'ا' else c)
            }
        }
    }
}

/**
 * Paints chosen characters of a word again, in another colour, exactly over themselves.
 * Android shapes the characters asked for as part of the whole word, so a letter comes
 * out in the very form it has in the word, and a vowel sign lands on its letter. That lets
 * one letter, or one sign, change colour without the word being laid out again, which
 * would make its letters shift.
 */
internal class Repaint(text: String, typeface: Typeface, sizePx: Float) {
    private val chars = text.toCharArray()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = sizePx
    }
    private val reaches = FloatArray(chars.size + 1) { -1f }

    /** How far the characters before [end] reach leftwards from the word's right edge. */
    fun reach(end: Int): Float {
        val at = end.coerceIn(0, chars.size)
        if (reaches[at] < 0f) reaches[at] = if (at == 0) 0f else paint.getRunAdvance(chars, 0, at, 0, chars.size, true, at)
        return reaches[at]
    }

    /**
     * Paints the characters from [from] up to [to] in [color]. [right] is the word's right
     * edge and [baseline] its baseline, as the page laid it out; [box] is the room the word
     * has. [glow] gives signs a soft light of their own colour round them.
     *
     * Letters are painted as they are. Signs on their own (a vowel sign, a tanween) can't
     * be: Android only sets a sign on its letter when it shapes the two together. So the
     * letter is painted with its signs, and then the letter, with any signs before the ones
     * wanted, is rubbed out again, which leaves the wanted signs alone, where they belong.
     */
    fun draw(canvas: android.graphics.Canvas, from: Int, to: Int, right: Float, baseline: Float, box: RectF, color: Color, glow: Float = 0f) {
        if (to <= from || from < 0 || to > chars.size) return
        paint.color = color.toArgb()
        val signsOnly = (from until to).all { chars[it].isMark() }
        if (!signsOnly) {
            canvas.drawTextRun(chars, from, to - from, 0, chars.size, right - reach(to), baseline, true, paint)
            return
        }
        var letter = from
        while (letter > 0 && chars[letter].isMark()) letter--
        // In some fonts a sign takes room of its own: a small alif is given a stroke to stand on.
        // Then the letter isn't the shape it is without the sign, and can't be rubbed out; what
        // lies across the sign's own room is painted instead.
        if (reach(to) - reach(from) > 1f) {
            canvas.save()
            canvas.clipRect(right - reach(to), box.top, right - reach(from), box.bottom)
            canvas.drawTextRun(chars, letter, to - letter, 0, chars.size, right - reach(to), baseline, true, paint)
            canvas.restore()
            return
        }
        val layer = canvas.saveLayer(box, null)
        fun withSigns() = canvas.drawTextRun(chars, letter, to - letter, 0, chars.size, right - reach(to), baseline, true, paint)
        fun letterAlone() = canvas.drawTextRun(chars, letter, from - letter, 0, chars.size, right - reach(from), baseline, true, paint)
        if (glow > 0f) {
            // The light round the signs: the same shapes, faint, a little to every side.
            val full = paint.alpha
            paint.alpha = full / 6
            for ((dx, dy) in AROUND) {
                canvas.save()
                canvas.translate(dx * glow, dy * glow)
                withSigns()
                canvas.restore()
            }
            paint.alpha = full
        }
        withSigns()
        paint.color = android.graphics.Color.BLACK
        paint.xfermode = RUB_OUT
        if (glow > 0f) {
            for ((dx, dy) in AROUND) {
                canvas.save()
                canvas.translate(dx * glow, dy * glow)
                letterAlone()
                canvas.restore()
            }
        }
        letterAlone()
        paint.xfermode = null
        canvas.restoreToCount(layer)
    }

    private companion object {
        val RUB_OUT = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

        /** Eight directions, for the soft light round a sign. */
        val AROUND = listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f, 0.7f to 0.7f, -0.7f to 0.7f, 0.7f to -0.7f, -0.7f to -0.7f)
    }
}

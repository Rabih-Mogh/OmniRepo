package app.recitation.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.text.TextRunShaper
import android.os.Build
import androidx.annotation.RequiresApi
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
 * Paints chosen characters of a word again, in another colour, exactly over themselves, so
 * that one letter, or one sign, changes colour without the word being laid out again.
 *
 * A letter's shape depends on the whole word: a kaf before a lam, a ya after a fa, and many
 * more, are drawn in a form of their own, and often a little higher or lower. Asking Android
 * to draw a few characters of a word gets them in their plain joined form, which then shows
 * as a second letter on top of the first. So the word is shaped once, whole, exactly as the
 * page draws it, and the very glyphs that came out of that are painted again one by one.
 * Android gives those glyphs out from version 12; before that, the whole word is painted
 * across the room the wanted letters take in it, and signs can't be told from their letter.
 */
internal class Repaint(text: String, typeface: Typeface, sizePx: Float, glyphs: Boolean = Build.VERSION.SDK_INT >= 31) {
    private val chars = text.toCharArray()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = sizePx
    }

    /** For every place in the word, where the letter it falls in, signs and all, begins and ends. */
    private val begins = IntArray(chars.size + 1)
    private val ends = IntArray(chars.size + 1)

    /** How wide the whole word is. */
    private val width = if (chars.isEmpty()) 0f else paint.getRunAdvance(chars, 0, chars.size, 0, chars.size, true, chars.size)
    private val reaches = FloatArray(chars.size + 1) { -1f }
    private val shaped: Shaped?

    /** Whether a sign can be painted apart from the letter it sits on. */
    val signsApart: Boolean get() = shaped != null

    init {
        var begin = 0
        for (i in chars.indices) {
            if (i > 0 && !chars[i].isMark()) begin = i
            begins[i] = begin
        }
        begins[chars.size] = chars.size
        var end = chars.size
        for (i in chars.indices.reversed()) {
            ends[i] = end
            if (!chars[i].isMark()) end = i
        }
        ends[chars.size] = chars.size
        shaped = if (glyphs && Build.VERSION.SDK_INT >= 31 && chars.isNotEmpty()) runCatching { Shaped(chars, paint, typeface, width, begins, ::reach) }.getOrNull() else null
    }

    /**
     * How far the characters before [end] reach leftwards from the word's right edge, as the
     * word is shaped whole. A place between a letter and its signs counts as after them.
     */
    fun reach(end: Int): Float {
        var at = end.coerceIn(0, chars.size)
        if (at < chars.size && begins[at] != at) at = ends[at]
        if (reaches[at] < 0f) {
            reaches[at] = when (at) {
                0 -> 0f
                chars.size -> width
                else -> paint.getRunAdvance(chars, 0, chars.size, 0, chars.size, true, at)
            }
        }
        return reaches[at]
    }

    /**
     * Paints the characters from [from] up to [to] in [color]. [right] is the word's right
     * edge and [baseline] its baseline, as the page laid it out; [box] is the room the word
     * has. [glow] gives what is painted a soft light of its own colour round it.
     */
    fun draw(canvas: android.graphics.Canvas, from: Int, to: Int, right: Float, baseline: Float, box: RectF, color: Color, glow: Float = 0f) {
        if (to <= from || from < 0 || to > chars.size) return
        paint.color = color.toArgb()
        // The room the letters asked for take in the word.
        val bandLeft = right - reach(ends[to - 1])
        val bandRight = right - reach(begins[from])
        val glyphs = shaped
        if (glyphs == null) {
            if ((from until to).all { chars[it].isMark() }) return
            canvas.save()
            canvas.clipRect(bandLeft, box.top, bandRight, box.bottom)
            canvas.drawTextRun(chars, 0, chars.size, 0, chars.size, right - width, baseline, true, paint)
            canvas.restore()
            return
        }
        val wanted = glyphs.of(from, to)
        fun paintAll() {
            for (glyph in wanted) {
                // A glyph that stands for several letters at once is painted only across those asked for.
                val split = glyphs.shared[glyph]
                if (split) {
                    canvas.save()
                    canvas.clipRect(bandLeft, box.top, bandRight, box.bottom)
                }
                glyphs.draw(canvas, glyph, right - width, baseline, paint)
                if (split) canvas.restore()
            }
        }
        if (glow > 0f) {
            // The light: the same shapes, faint, a little to every side.
            val full = paint.alpha
            paint.alpha = full / 6
            for ((dx, dy) in AROUND) {
                canvas.save()
                canvas.translate(dx * glow, dy * glow)
                paintAll()
                canvas.restore()
            }
            paint.alpha = full
        }
        paintAll()
    }

    /**
     * A word shaped whole: its glyphs, where each stands, and which characters each draws.
     *
     * Android tells the glyphs and their places but not their characters, so that is worked
     * out here. Glyphs that take room are letters, and are matched to the letters by where
     * they stand in the word: the room each letter takes is known too. Glyphs that take no
     * room are signs and go with the letter they are set on; among one letter's signs, each
     * is known by the glyph the font draws that sign with, and failing that by its turn.
     */
    @RequiresApi(31)
    private class Shaped(chars: CharArray, paint: Paint, typeface: Typeface, width: Float, begins: IntArray, reach: (Int) -> Float) {
        private val ids: IntArray
        private val places: FloatArray
        private val fonts: Array<Font>
        private val drawnBy: Array<IntArray>

        /** Glyphs that draw more than one letter: a lam and alif written as one, and the like. */
        val shared: BooleanArray

        init {
            val run = TextRunShaper.shapeTextRun(chars, 0, chars.size, 0, chars.size, 0f, 0f, true, paint)
            val count = run.glyphCount()
            ids = IntArray(count) { run.getGlyphId(it) }
            places = FloatArray(2 * count) { if (it % 2 == 0) run.getGlyphX(it / 2) else run.getGlyphY(it / 2) }
            fonts = Array(count) { run.getFont(it) }
            shared = BooleanArray(count)
            val bounds = RectF()
            // Glyphs come left to right; the word is read right to left. A glyph takes room if the
            // font gives it some and it stands clear of the glyph before it: fonts give some signs
            // a width and still set them on their letter.
            val takesRoom = BooleanArray(count)
            var edge = width
            for (glyph in count - 1 downTo 0) {
                val room = fonts[glyph].getGlyphBounds(ids[glyph], paint, bounds)
                if (room > 0.5f && places[2 * glyph] <= edge - room / 2) {
                    takesRoom[glyph] = true
                    edge = places[2 * glyph]
                }
            }
            val bases = (count - 1 downTo 0).filter { takesRoom[it] }
            check(bases.isNotEmpty())
            // The letters, each with its signs, and the room each takes, measured from the left.
            val letters = chars.indices.filter { begins[it] == it }
            val letterLeft = FloatArray(letters.size) { width - reach(if (it + 1 < letters.size) letters[it + 1] else chars.size) }
            val letterRight = FloatArray(letters.size) { width - reach(letters[it]) }
            val baseLeft = FloatArray(bases.size) { places[2 * bases[it]] }
            val baseRight = FloatArray(bases.size) { if (it == 0) width else places[2 * bases[it - 1]] }

            // Letters and the glyphs that take room are gathered into sets that belong together:
            // most often one letter and one glyph, sometimes several letters drawn as one glyph.
            val set = IntArray(letters.size + bases.size) { it }
            fun find(x: Int): Int {
                var r = x
                while (set[r] != r) r = set[r]
                return r
            }
            fun join(a: Int, b: Int) { set[find(a)] = find(b) }
            fun within(middle: Float, left: FloatArray, right: FloatArray): Int {
                var best = 0
                var miss = Float.MAX_VALUE
                for (k in left.indices) {
                    if (right[k] - left[k] <= 0.5f) continue
                    val off = if (middle < left[k]) left[k] - middle else if (middle > right[k]) middle - right[k] else 0f
                    if (off < miss) {
                        miss = off
                        best = k
                    }
                }
                return best
            }
            for (l in letters.indices) {
                if (letterRight[l] - letterLeft[l] > 0.5f) {
                    join(l, letters.size + within((letterLeft[l] + letterRight[l]) / 2, baseLeft, baseRight))
                } else {
                    // A letter that takes no room of its own goes with its neighbour.
                    join(l, if (l > 0) l - 1 else minOf(1, letters.size - 1))
                }
            }
            for (b in bases.indices) {
                if (baseRight[b] - baseLeft[b] > 0.5f) join(letters.size + b, within((baseLeft[b] + baseRight[b]) / 2, letterLeft, letterRight))
            }
            // A sign's glyph comes just before, to the left of, the glyph it is set on.
            val setOfGlyph = IntArray(count)
            var owner = find(letters.size)
            for (glyph in count - 1 downTo 0) {
                val base = bases.indexOf(glyph)
                if (base >= 0) owner = find(letters.size + base)
                setOfGlyph[glyph] = owner
            }

            val drawn = Array(chars.size) { ArrayList<Int>(2) }
            for (root in letters.indices.map(::find).distinct()) {
                val mine = letters.indices.filter { find(it) == root }
                // The set's glyphs in reading order, and its characters.
                val glyphs = (count - 1 downTo 0).filter { setOfGlyph[it] == root }
                if (glyphs.isEmpty()) continue
                val signs = mine.flatMap { l -> (letters[l] + 1 until (if (l + 1 < letters.size) letters[l + 1] else chars.size)).toList() }
                val taken = HashSet<Int>()
                val first = glyphs.firstOrNull { takesRoom[it] }
                // Each sign by the glyph the font draws it with.
                val unknown = ArrayList<Int>()
                for (sign in signs) {
                    val plain = plainGlyph(typeface, paint, chars[sign])
                    val glyph = glyphs.firstOrNull { it != first && it !in taken && ids[it] == plain }
                    if (glyph != null) {
                        taken += glyph
                        drawn[sign] += glyph
                    } else {
                        unknown += sign
                    }
                }
                val loose = glyphs.filter { !takesRoom[it] && it !in taken }
                var roomy = glyphs.filter { takesRoom[it] && it !in taken }
                when {
                    unknown.isEmpty() -> {}
                    // The rest by their turn; where the font drew two signs as one, both light it.
                    loose.size == unknown.size -> for ((k, sign) in unknown.withIndex()) drawn[sign] += loose[k]
                    loose.isNotEmpty() -> for (sign in unknown) drawn[sign] += loose
                    // A sign the font gives room of its own, in a form it has only in this word.
                    roomy.size > mine.size -> {
                        for (sign in unknown) drawn[sign] += roomy.drop(mine.size)
                        roomy = roomy.take(mine.size)
                    }
                }
                // Signs' glyphs no sign answered for stay with the letters.
                val spare = if (unknown.isEmpty()) loose else emptyList()
                if (roomy.size == mine.size) {
                    for ((k, l) in mine.withIndex()) drawn[letters[l]] += roomy[k]
                    if (mine.isNotEmpty()) drawn[letters[mine.last()]] += spare
                } else {
                    for (l in mine) drawn[letters[l]] += roomy + spare
                    if (mine.size > 1) for (glyph in roomy) shared[glyph] = true
                }
            }
            // A sign the font draws in one piece with its letter (an alif with its wavy line, a whole
            // word written as one shape) can't be painted alone: it is painted with its letter.
            for (i in chars.indices) if (drawn[i].isEmpty() && begins[i] != i) drawn[i] += drawn[begins[i]]
            drawnBy = Array(chars.size) { drawn[it].toIntArray() }
        }

        /** The glyphs that draw the characters from [from] up to [to], each once. */
        fun of(from: Int, to: Int): List<Int> {
            val all = ArrayList<Int>()
            for (i in from until to) for (glyph in drawnBy[i]) if (glyph !in all) all += glyph
            return all
        }

        fun draw(canvas: android.graphics.Canvas, glyph: Int, left: Float, baseline: Float, paint: Paint) {
            canvas.save()
            canvas.translate(left, baseline)
            canvas.drawGlyphs(ids, glyph, places, 2 * glyph, 1, fonts[glyph], paint)
            canvas.restore()
        }

        private companion object {
            val plain = HashMap<Pair<Typeface, Char>, Int>()

            /** The glyph a font draws a sign with when it stands alone; -1 if it has none. */
            fun plainGlyph(typeface: Typeface, paint: Paint, sign: Char): Int = synchronized(plain) {
                plain.getOrPut(typeface to sign) {
                    val alone = TextRunShaper.shapeTextRun(charArrayOf(sign), 0, 1, 0, 1, 0f, 0f, true, paint)
                    if (alone.glyphCount() == 1) alone.getGlyphId(0) else -1
                }
            }
        }
    }

    private companion object {
        /** Eight directions, for the soft light round a sign. */
        val AROUND = listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f, 0.7f to 0.7f, -0.7f to 0.7f, 0.7f to -0.7f, -0.7f to -0.7f)
    }
}

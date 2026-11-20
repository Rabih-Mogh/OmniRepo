package app.recitation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import app.recitation.ui.Repaint
import app.recitation.ui.isMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Letters and signs painted over a word must fall exactly on the word's own: a letter
 * painted in the wrong form shows as a second letter on top of the first.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WordPaintTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val w = 420
    private val h = 220
    private val right = 400f
    private val baseline = 130f
    private val box = RectF(0f, 0f, w.toFloat(), h.toFloat())
    private val size = 72f

    private val fonts by lazy { listOf(ResourcesCompat.getFont(context, R.font.uthmanic_hafs)!!, ResourcesCompat.getFont(context, R.font.amiri_quran)!!) }

    // Words whose letters take a form of their own in the word, are drawn several as one, or carry
    // signs the fonts treat specially: each in the Uthmani spelling and in the Madinah mushaf's, as
    // Quran.com gives them. The Madinah font is made for the Madinah spelling, and the app only ever
    // draws that spelling with it: the Uthmani spelling's signs would sit on its letters.
    private val spellings = listOf(
        "\u0643\u064E\u0644\u0651\u064E\u0627\u0653" to "\u0643\u0640\u064E\u0644\u0651\u064E\u0627\u0653",
        "\u0641\u0650\u0649" to "\u0641\u0650\u064A",
        "\u0671\u0644\u0644\u0651\u064E\u0647\u0650" to "\u0671\u0644\u0644\u0651\u064E\u0647\u0650",
        "\u0671\u0644\u0631\u0651\u064E\u062D\u0652\u0645\u064E\u0640\u0670\u0646\u0650" to "\u0671\u0644\u0631\u0651\u064E\u062D\u06E1\u0645\u064E\u0670\u0646\u0650",
        "\u0671\u0644\u0635\u0651\u0650\u0631\u064E\u0670\u0637\u064E" to "\u0671\u0644\u0635\u0651\u0650\u0631\u064E\u0670\u0637\u064E",
        "\u0647\u064F\u062F\u064B\u06ED\u0649" to "\u0647\u064F\u062F\u0657\u0649",
        "\u0644\u064E\u0627" to "\u0644\u064E\u0627",
        "\u0671\u0644\u0652\u0643\u0650\u062A\u064E\u0640\u0670\u0628\u0650" to "\u0671\u0644\u06E1\u0643\u0650\u062A\u064E\u0670\u0628\u0650",
        "\u064A\u064E\u062C\u0652\u0639\u064E\u0644" to "\u064A\u064E\u062C\u06E1\u0639\u064E\u0644",
        "\u062B\u064E\u0645\u064E\u0631\u064E\u0629\u064D\u06E2" to "\u062B\u064E\u0645\u064E\u0631\u064E\u0629\u0656",
        "\u0644\u0650\u0643\u064E\u0644\u0650\u0645\u064E\u0640\u0670\u062A\u0650\u0647\u0650\u06E6" to "\u0644\u0650\u0643\u064E\u0644\u0650\u0645\u064E\u0670\u062A\u0650\u0647\u0650\u06E6",
        "\u0623\u064E\u0646\u0630\u064E\u0631\u0652\u0646\u064E\u0640\u0670\u0643\u064F\u0645\u0652" to "\u0623\u064E\u0646\u0630\u064E\u0631\u06E1\u0646\u064E\u0670\u0643\u064F\u0645\u06E1",
        "\u0630\u0650\u0643\u0652\u0631\u064F" to "\u0630\u0650\u0643\u06E1\u0631\u064F",
        "\u064A\u064F\u0632\u064E\u0643\u0651\u0650\u0649" to "\u064A\u064F\u0632\u064E\u0643\u0651\u0650\u064A",
        "\u0628\u064E\u0639\u0652\u062F\u0650" to "\u0628\u064E\u0639\u06E1\u062F\u0650",
        "\u0671\u0644\u0652\u062D\u064E\u0645\u0652\u062F\u064F" to "\u0671\u0644\u06E1\u062D\u064E\u0645\u06E1\u062F\u064F",
        "\u0644\u0650\u0644\u0651\u064E\u0647\u0650" to "\u0644\u0650\u0644\u0651\u064E\u0647\u0650",
        "\u062E\u064E\u0644\u064E\u0648\u0652\u0627\u06DF" to "\u062E\u064E\u0644\u064E\u0648\u06E1\u0627\u0652",
        "\u0644\u0651\u064E\u0640\u0670\u0643\u0650\u0646\u0651\u064E\u0627\u06E0" to "\u0644\u0651\u064E\u0670\u0643\u0650\u0646\u0651\u064E\u0627\u06E0",
        "\u0631\u064E\u0628\u0651\u0650\u0649" to "\u0631\u064E\u0628\u0651\u0650\u064A",
        "\u064A\u064E\u062E\u0652\u0634\u064E\u0648\u0652\u0646\u064E" to "\u064A\u064E\u062E\u06E1\u0634\u064E\u0648\u06E1\u0646\u064E",
        "\u0671\u0644\u0644\u0651\u064E\u0647\u064F" to "\u0671\u0644\u0644\u0651\u064E\u0647\u064F",
        "\u0625\u0650\u0644\u0651\u064E\u0627" to "\u0625\u0650\u0644\u0651\u064E\u0627",
        "\u0645\u064F\u062A\u064E\u0634\u064E\u0640\u0670\u0628\u0650\u0647\u064E\u0640\u0670\u062A\u064C\u06ED\u0020\u06D6" to "\u0645\u064F\u062A\u064E\u0634\u064E\u0670\u0628\u0650\u0647\u064E\u0670\u062A\u065E\u06D6",
        "\u0639\u0650\u0648\u064E\u062C\u064E\u0627\u06DC" to "\u0639\u0650\u0648\u064E\u062C\u064E\u0627\u06DC",
        "\u0645\u0650\u0646\u06E2" to "\u0645\u0650\u0646\u06E2",
    )

    /** Each font with the words as the app writes them in it. */
    private val written by lazy { listOf(fonts[0] to spellings.map { it.second }, fonts[1] to spellings.map { it.first }) }

    private fun whole(word: String, font: Typeface): Bitmap {
        val chars = word.toCharArray()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = size }
        val width = paint.getRunAdvance(chars, 0, chars.size, 0, chars.size, true, chars.size)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Canvas(it).drawTextRun(chars, 0, chars.size, 0, chars.size, right - width, baseline, true, paint) }
    }

    private fun painted(draw: (Canvas) -> Unit) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }

    private fun ink(bitmap: Bitmap): Int = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }.count { it ushr 24 > 128 }

    /** The share of the word's ink that the two pictures disagree on. */
    private fun apart(a: Bitmap, b: Bitmap): Double {
        val pa = IntArray(w * h).also { a.getPixels(it, 0, w, 0, 0, w, h) }
        val pb = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        return pa.indices.count { abs((pa[it] ushr 24) - (pb[it] ushr 24)) > 128 }.toDouble() / ink(a).coerceAtLeast(1)
    }

    @Test
    fun everyCharacterPaintedOnItsOwnMakesUpTheWordExactly() {
        for ((font, words) in written) for (word in words) {
            val repaint = Repaint(word, font, size)
            assertTrue(repaint.signsApart)
            val parts = painted { canvas -> for (i in word.indices) repaint.draw(canvas, i, i + 1, right, baseline, box, Color.Black) }
            assertTrue("$word comes out ${apart(whole(word, font), parts)} apart", apart(whole(word, font), parts) < 0.02)
        }
    }

    @Test
    fun onOlderAndroidWholeLettersArePaintedFromTheWordItself() {
        for ((font, words) in written) for (word in words) {
            val repaint = Repaint(word, font, size, glyphs = false)
            assertFalse(repaint.signsApart)
            val parts = painted { canvas ->
                var from = 0
                while (from < word.length) {
                    var to = from + 1
                    while (to < word.length && word[to].isMark()) to++
                    repaint.draw(canvas, from, to, right, baseline, box, Color.Black)
                    from = to
                }
            }
            assertTrue("$word comes out ${apart(whole(word, font), parts)} apart", apart(whole(word, font), parts) < 0.1)
            // A sign can't be told from its letter there, so it is left alone rather than painted wrongly.
            val sign = word.indices.first { word[it].isMark() }
            assertEquals(0, ink(painted { repaint.draw(it, sign, sign + 1, right, baseline, box, Color.Black) }))
        }
    }

    @Test
    fun aSignIsPaintedApartFromItsLetter() {
        // "bi-rabbi": the kasra under the b.
        for (font in fonts) {
            val word = "\u0628\u0650\u0631\u064E\u0628\u0651\u0650"
            val repaint = Repaint(word, font, size)
            val letter = painted { repaint.draw(it, 0, 1, right, baseline, box, Color.Black) }
            val sign = painted { repaint.draw(it, 1, 2, right, baseline, box, Color.Black) }
            val both = painted { repaint.draw(it, 0, 2, right, baseline, box, Color.Black) }
            val inks = "letter ${ink(letter)}, sign ${ink(sign)}, both ${ink(both)}"
            assertTrue(inks, ink(sign) > 0)
            assertTrue(inks, ink(letter) > ink(sign))
            assertTrue(inks, abs(ink(letter) + ink(sign) - ink(both)) <= ink(both) / 20)
            // The kasra is under the line the letters stand on, and nothing of the letter comes with it.
            val pixels = IntArray(w * h).also { sign.getPixels(it, 0, w, 0, 0, w, h) }
            assertTrue("the sign reaches up to row ${pixels.indices.first { pixels[it] ushr 24 > 128 } / w}, the line is at $baseline", pixels.indices.none { pixels[it] ushr 24 > 128 && it / w < baseline.toInt() })
        }
    }

    @Test
    fun aSignDrawnInOnePieceWithItsLetterIsPaintedWithIt() {
        // The Madinah font writes "bismi" as one shape, and an alif with its wavy line as one.
        for (word in listOf("\u0628\u0650\u0633\u06E1\u0645\u0650", "\u0645\u064E\u0627\u0653")) {
            val repaint = Repaint(word, fonts[0], size)
            for (sign in word.indices.filter { word[it].isMark() }) {
                assertTrue("sign $sign of $word is left out", ink(painted { repaint.draw(it, sign, sign + 1, right, baseline, box, Color.Black) }) > 0)
            }
        }
    }

    @Test
    fun aPictureOfWhichCharacterEachShapeIsTakenFor() {
        val rows = (spellings.size + 3) / 4
        val sheet = Bitmap.createBitmap(w * 4, h * rows * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(android.graphics.Color.WHITE)
        for ((half, pair) in written.withIndex()) for ((cell, word) in pair.second.withIndex()) {
            canvas.save()
            canvas.translate((cell % 4) * w.toFloat(), (half * rows + cell / 4) * h.toFloat())
            val repaint = Repaint(word, pair.first, size)
            var letters = 0
            var signs = 0
            for (i in word.indices) {
                val color = if (word[i].isMark()) listOf(Color(0xFF2E9E44), Color(0xFFE08A00))[signs++ % 2] else listOf(Color(0xFFC62828), Color(0xFF1565C0))[letters++ % 2]
                repaint.draw(canvas, i, i + 1, right, baseline, box, color)
            }
            canvas.restore()
        }
        File("build/screens").mkdirs()
        FileOutputStream("build/screens/word-paint.png").use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun aPictureOfTheMadinahFontWithEachSpelling() {
        // Plain black, nothing painted over: the Uthmani spelling on top, the Madinah spelling under it.
        val some = spellings.filter { it.first != it.second }.take(12)
        val sheet = Bitmap.createBitmap(w * 4, h * 6, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts[0]; textSize = size }
        for ((cell, pair) in some.withIndex()) for ((k, word) in listOf(pair.first, pair.second).withIndex()) {
            val chars = word.toCharArray()
            val width = paint.getRunAdvance(chars, 0, chars.size, 0, chars.size, true, chars.size)
            paint.color = if (k == 0) android.graphics.Color.rgb(198, 40, 40) else android.graphics.Color.BLACK
            canvas.drawTextRun(chars, 0, chars.size, 0, chars.size, (cell % 4) * w + right - width, (cell / 4 * 2 + k) * h + baseline, true, paint)
        }
        FileOutputStream("build/screens/word-paint-spellings.png").use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

package app.recitation.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Listening again to some verses only: a stretch of the sound, and its timings put back among the rest. */
class SpanTest {

    /** A recording whose sample n has the value n, read 1,000 at a time; a seek lands on the block before the one asked for. */
    private class Counting(override val total: Long) : Samples {
        override var position = 0L
        override fun seekTo(sample: Long) { position = maxOf(0, (sample / 1000 - 1) * 1000) }
        override fun read(): FloatArray? {
            if (position >= total) return null
            val n = minOf(1000, total - position).toInt()
            return FloatArray(n) { (position + it).toFloat() }.also { position += n }
        }
    }

    @Test
    fun `a stretch of a recording ends where it is told to and is timed from its own start`() {
        val span = Span(Counting(20_000), from = 4_500, until = 7_250)
        assertEquals(2_750, span.total)
        val blocks = generateSequence { span.read() }.toList()
        // The first block starts where the seek landed, before the stretch; where that is, is said relative to the stretch.
        assertEquals(3_000f, blocks.first().first())
        assertEquals(7_249f, blocks.last().last())
        assertTrue(span.position >= span.total)
        assertNull(span.read())
    }

    private val timings = Timings(
        listOf(
            listOf(WordTiming(0, intArrayOf(3, 0)), WordTiming(1000, intArrayOf(3, 1000))),
            listOf(WordTiming(2000, intArrayOf(3, 2000)), WordTiming(3000, intArrayOf(3, 3000))),
            listOf(WordTiming(9000, intArrayOf(3, 9000))),
        ),
        0.9f, repeats = listOf(Repeat(0, listOf(WordTiming(1500, intArrayOf(3, 1500)))), Repeat(2, listOf(WordTiming(4000, intArrayOf(3, 4000))))),
    )

    @Test
    fun `verses are heard again from a little before them to a little after`() {
        assertEquals(0..12_000, timings.spanOf(1, 1))
        assertEquals(0..Int.MAX_VALUE, timings.spanOf(0, 2))
        assertNull(timings.spanOf(3, 3))
    }

    @Test
    fun `verses heard again take their place among the rest`() {
        // The second verse again, heard from 1.5 s into the recording: its second word turns out to be said twice.
        val part = Timings(
            listOf(listOf(WordTiming(600, intArrayOf(3, 600)), WordTiming(1700, intArrayOf(2, 1700, 3, 1900)))), 0.95f,
            repeats = listOf(Repeat(1, listOf(WordTiming(4200, intArrayOf(3, 4200))))),
        )
        val merged = timings.with(1, part, 1500)
        assertEquals(listOf(0, 1000, 2100, 3200, 9000), merged.verses.flatten().map { it.startMs })
        assertEquals(listOf(2, 3200, 3, 3400), merged.verses[1][1].letters.toList())
        // What was said again elsewhere stays; what was said again in that verse is replaced by what was found this time.
        assertEquals(listOf(0 to 1500, 3 to 5700), merged.repeats.map { it.first to it.words[0].startMs })
        assertEquals(0.9f, merged.clarity)
    }

    @Test
    fun `verses from the middle of a surah are placed without the opening formulas`() {
        val words = listOf(listOf("قُلْ", "هُوَ"))
        assertTrue(Recital(112, words, withOpening = false).phones.size < Recital(112, words).phones.size)
        assertTrue(Recital(112, words, withOpening = false).phones.none { it.optional && it == Recital(112, words).phones.first() })
    }
}

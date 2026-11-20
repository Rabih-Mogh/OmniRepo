package app.recitation.core

import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlignerTest {

    private val width = SoundColumns.WIDTH

    /** Rows in which [heard] sounds are loud and clear, one per row; null is a pause. */
    private fun rows(heard: List<Sound?>): FloatArray {
        val rows = FloatArray(heard.size * width) { if (it % width == 0) -6f else -9f }
        for ((t, sound) in heard.withIndex()) rows[t * width + (sound?.let { it.ordinal + 1 } ?: 0)] = -0.01f
        return rows
    }

    private fun phones(vararg sounds: Sound) = sounds.map { Phone(it, false, 0) }

    @Test
    fun `finds each sound where it was heard`() {
        val heard = listOf(null, Sound.B, null, null, Sound.I, Sound.S, null, Sound.M, null)
        val starts = Aligner(rows(heard), phones(Sound.B, Sound.I, Sound.S, Sound.M)).align()
        assertEquals(listOf(1, 4, 5, 7), starts!!.toList())
    }

    @Test
    fun `leaves out optional sounds that weren't said`() {
        val heard = listOf(Sound.B, Sound.A, null, Sound.L)
        val sounds = listOf(
            Phone(Sound.HAMZA, true, 0), Phone(Sound.B, false, 0), Phone(Sound.A, false, 0),
            Phone(Sound.N, true, 0), Phone(Sound.L, false, 0), Phone(Sound.I, true, 0),
        )
        assertEquals(listOf(-1, 0, 1, -1, 3, -1), Aligner(rows(heard), sounds).align()!!.toList())
    }

    @Test
    fun `other speech before and after is skipped`() {
        val heard = listOf(Sound.K, Sound.F, Sound.Z, Sound.B, Sound.A, Sound.Z, Sound.K, Sound.Q)
        assertEquals(listOf(3, 4), Aligner(rows(heard), phones(Sound.B, Sound.A)).align()!!.toList())
    }

    @Test
    fun `more sounds than rows can't be aligned`() {
        assertNull(Aligner(rows(listOf(Sound.B, Sound.A)), phones(Sound.B, Sound.A, Sound.B)).align())
    }

    /** The same search done the plain way: every row against every state, with full memory of the way back. */
    private fun bestScore(rows: FloatArray, sounds: List<Phone>): Float {
        val frames = rows.size / width
        val states = 2 * sounds.size + 1
        fun enters(s: Int) = enters(sounds, s)
        var prev = FloatArray(states) { if (it == 0) 0f else -1e30f }
        for (t in 0 until frames) {
            prev = FloatArray(states) { s ->
                val column = if (s % 2 == 1) sounds[s / 2].sound.ordinal + 1 else 0
                enters(s).maxOf { prev[it] } + rows[t * width + column]
            }
        }
        return enters(states - 1).maxOf { prev[it] }
    }

    /** The states the path may have stood on one row before standing on [s]. */
    private fun enters(sounds: List<Phone>, s: Int): List<Int> = buildList {
        add(s)
        if (s >= 1) add(s - 1)
        if (s >= 2 && s % 2 == 1) add(s - 2)
        // Over a run of optional sounds, from the pause before each or the sound before that.
        var k = s / 2 - 1
        while (k >= 0 && sounds[k].optional) {
            add(2 * k)
            if (k > 0) add(2 * k - 1)
            k--
        }
    }

    private fun score(rows: FloatArray, sounds: List<Phone>, path: IntArray): Float {
        var total = 0f
        for ((t, s) in path.withIndex()) total += rows[t * width + if (s % 2 == 1) sounds[s / 2].sound.ordinal + 1 else 0]
        return total
    }

    @Test
    fun `splitting the search in halves gives the same best path as doing it whole`() {
        val random = Random(7)
        repeat(60) {
            val count = random.nextInt(1, 40)
            val sounds = List(count) { Phone(Sound.entries[random.nextInt(6)], random.nextInt(4) == 0, 0) }
            val frames = count + random.nextInt(0, 80)
            val rows = FloatArray(frames * width) { -random.nextFloat() * 8 }
            val best = bestScore(rows, sounds)
            // A limit of 50 cells forces the split all the way down; the default solves it in one go.
            for (direct in listOf(50L, 2_000_000L)) {
                val path = assertNotNull(Aligner(rows, sounds, direct).path())
                val steps = listOf(0) + path.toList() + (2 * count)
                assertTrue(steps.zipWithNext().all { (a, b) -> a in enters(sounds, b) }, "every step is an allowed one")
                assertTrue(abs(best - score(rows, sounds, path)) < 1e-2, "best $best, got ${score(rows, sounds, path)}")
            }
        }
    }

    /** The real recitation the tests use: the frame each kept row came from, the rows, the verses, the published starts. */
    private class Fatihah(val frames: IntArray, val rows: FloatArray, val verses: List<List<String>>, val published: List<Int>)

    private fun fatihah(): Fatihah {
        val bytes = javaClass.getResourceAsStream("/fatihah.rows")!!.use { DataInputStream(it).readBytes() }
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val kept = data.int
        val frames = IntArray(kept)
        val rows = FloatArray(kept * width)
        for (t in 0 until kept) {
            frames[t] = data.int
            for (c in 0 until width) rows[t * width + c] = data.float
        }
        val lines = javaClass.getResourceAsStream("/fatihah.txt")!!.bufferedReader().readLines()
        return Fatihah(
            frames, rows,
            lines.map { line -> line.split(' ').map { it.substringBefore('|') } },
            lines.flatMap { line -> line.split(' ').map { it.substringAfter('|').toInt() } },
        )
    }

    @Test
    fun `places the words as the recording is listened to, a few seconds behind`() {
        val real = fatihah()
        val recital = Recital(1, real.verses)
        val live = LiveAlignment(recital)
        var placedBefore = 0
        var heardBefore = 0
        var timings = live.advance()
        assertEquals(0, timings.verses.size, "nothing placed before anything is heard")
        // The rows arrive forty at a time, about three seconds of recitation.
        for (from in real.frames.indices step 40) {
            val until = minOf(real.frames.size, from + 40)
            live.add(real.frames.copyOfRange(from, until), real.rows.copyOfRange(from * width, until * width))
            timings = live.advance()
            val placed = timings.verses.sumOf { it.size }
            assertTrue(placed >= placedBefore && timings.heardMs >= heardBefore, "it only ever moves forward")
            assertTrue(!timings.complete && timings.matches, "still listening: no verdict on the surah yet")
            placedBefore = placed
            heardBefore = timings.heardMs
        }
        // Most of the surah is placed; the last few seconds wait for what would come after.
        val ours = timings.verses.flatten().map { it.startMs }
        assertTrue(ours.size in 20..real.published.size, "placed ${ours.size} of ${real.published.size} words")
        assertTrue(ours.zipWithNext().all { (a, b) -> b >= a })
        val off = ours.zip(real.published) { a, b -> abs(a - b) }.sorted()
        assertTrue(off[off.size / 2] <= 100, "half the words placed so far within 100 ms, got ${off[off.size / 2]}")
        assertTrue(off[off.size * 9 / 10] <= 300, "nine in ten within 300 ms, got ${off[off.size * 9 / 10]}")
        // Nothing is claimed beyond what has been heard.
        assertTrue(ours.last() <= timings.heardMs)
    }

    @Test
    fun `lines a real recitation up with its published word timings`() {
        // Al-Fatihah recited by Mishari al-`Afasy, as the model heard it. See tools/make_test_recitation.py.
        val real = fatihah()
        val frames = real.frames
        val rows = real.rows
        val verses = real.verses
        val published = real.published

        val recital = Recital(1, verses)
        val aligner = Aligner(rows, recital.phones)
        val timings = recital.timings(assertNotNull(aligner.align()), frames, aligner)

        val ours = timings.verses.flatten().map { it.startMs }
        val off = ours.zip(published) { a, b -> abs(a - b) }.sorted()
        assertTrue(off[off.size / 2] <= 80, "half the words should be within 80 ms, got ${off[off.size / 2]}")
        assertTrue(off[off.size * 9 / 10] <= 250, "nine in ten within 250 ms, got ${off[off.size * 9 / 10]}")
        assertTrue(off.last() <= 500, "none further out than 500 ms, got ${off.last()}")
        assertTrue(timings.clarity > 0.8f && timings.matches, "clarity ${timings.clarity}")

        // The same recording against another surah's words is recognised as a mismatch.
        val wrong = Recital(112, listOf(listOf("قُلْ", "هُوَ", "ٱللَّهُ", "أَحَدٌ"), listOf("ٱللَّهُ", "ٱلصَّمَدُ"), listOf("لَمْ", "يَلِدْ", "وَلَمْ", "يُولَدْ"), listOf("وَلَمْ", "يَكُن", "لَّهُۥ", "كُفُوًا", "أَحَدٌۢ")))
        val wrongAligner = Aligner(rows, wrong.phones)
        assertTrue(!wrong.timings(wrongAligner.align()!!, frames, wrongAligner).matches)
    }
}

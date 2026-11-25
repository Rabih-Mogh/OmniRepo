package app.recitation.core

import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HearingTest {

    private val hop = 320
    private val symbols = SoundColumns.SYMBOLS
    private val a = HeardScript.ALPHABET.indexOf('َ') + 1
    private val i = HeardScript.ALPHABET.indexOf('ِ') + 1

    /**
     * A made-up recording in which every 20 ms frame spells out its own number: twenty
     * runs of sixteen samples, each +1 or -1 for one bit. So a "model" can tell exactly
     * which stretch of the recording it was handed.
     */
    private class Recording(val length: Int, seed: Int) : Samples {
        private val random = Random(seed)
        override val total: Long get() = length.toLong()
        override var position = 0L
            private set

        fun sample(i: Long): Float = if ((i / 320) shr ((i % 320) / 16).toInt() and 1L == 1L) 1f else -1f

        // Like a real decoder, a seek lands on the start of a block at or before what was asked for.
        override fun seekTo(sample: Long) {
            position = sample - sample % 1024
        }

        override fun read(): FloatArray? {
            if (position >= length) return null
            val n = minOf(random.nextInt(700, 5000).toLong(), length - position).toInt()
            return FloatArray(n) { sample(position + it) }.also { position += n }
        }
    }

    /**
     * Reads each frame's number back out of the samples. Frames whose number divides by
     * five are "pauses"; the others are heard as "a" when even and "i" when odd.
     */
    private val model = SpeechModel { samples ->
        val frames = (samples.size - 400) / hop + 1
        val logits = FloatArray(frames * symbols)
        for (f in 0 until frames) {
            var number = 0
            for (bit in 0 until 20) if (samples[f * hop + bit * 16 + 8] > 0) number = number or (1 shl bit)
            val heard = if (number % 5 == 0) 0 else if (number % 2 == 0) a else i
            for (v in 0 until symbols) logits[f * symbols + v] = if (v == heard) 12f else 0f
        }
        logits
    }

    private fun notes() = File.createTempFile("heard", ".rows").apply { delete(); deleteOnExit() }

    private fun check(heard: Heard, length: Int) {
        val frames = (length - 400) / hop + 1
        assertEquals((0 until frames).filter { it % 5 != 0 }, heard.frames.toList(), "every frame that isn't a pause, once, in order")
        for ((row, frame) in heard.frames.withIndex()) {
            val a = heard.rows[row * SoundColumns.WIDTH + 1 + Sound.A.ordinal]
            val i = heard.rows[row * SoundColumns.WIDTH + 1 + Sound.I.ordinal]
            assertEquals(frame % 2 == 0, a > i, "frame $frame carries the scores of its own stretch of sound")
        }
    }

    @Test
    fun `hears every frame of a recording once, whatever its length`() {
        // Under one piece, exactly two pieces, just over, and a ragged length.
        for (length in listOf(52_000, 320_000, 320_500, 757_123)) {
            val heard = Hearing(model).listen(Recording(length, 1), notes())
            check(assertNotNull(heard), length)
        }
    }

    @Test
    fun `carries on where it stopped`() {
        val length = 757_123
        val notes = notes()
        var pieces = 0
        assertNull(Hearing(model).listen(Recording(length, 2), notes, stillWanted = { ++pieces < 2 }))
        val sizeWhenStopped = notes.length()
        // The app was killed half way through writing the next piece.
        notes.appendBytes(ByteArray(37) { 1 })
        val progress = ArrayList<Float>()
        val heard = Hearing(model).listen(Recording(length, 3), notes, onProgress = { progress += it })
        check(assertNotNull(heard), length)
        assertEquals(true, notes.length() > sizeWhenStopped)
        assertEquals(true, progress.first() > 0.3f, "it didn't start from the beginning again")
        assertEquals(1f, progress.last())

        // Asked again when everything has been heard, it only reads its notes back.
        check(assertNotNull(Hearing(SpeechModel { error("nothing left to listen to") }).listen(Recording(length, 4), notes)), length)
    }

    @Test
    fun `goes to where it is asked to listen first, and still hears everything once`() {
        val length = 757_123 // five pieces
        val notes = notes()
        val order = ArrayList<Int>()
        // The recording is being played from its fourth piece on: that one first, then on from there, then the rest.
        val heard = Hearing(model).listen(
            Recording(length, 5), notes, onPiece = { piece, _, _ -> order += piece },
            next = { done -> (3..9).firstOrNull { it !in done } },
        )
        assertEquals(listOf(3, 4, 0, 1, 2), order)
        check(assertNotNull(heard), length)
    }

    @Test
    fun `what was heard out of turn is kept when the listening stops and carries on`() {
        val length = 757_123
        val notes = notes()
        var pieces = 0
        assertNull(Hearing(model).listen(Recording(length, 6), notes, stillWanted = { ++pieces < 2 }, next = { done -> if (2 !in done) 2 else null }))
        // The third piece and the first were heard; they are handed back by their numbers.
        assertEquals(listOf(0, 2), Hearing(model).resume(notes).keys.sorted())
        val later = ArrayList<Int>()
        val heard = Hearing(model).listen(Recording(length, 7), notes, onPiece = { piece, _, _ -> later += piece })
        assertEquals(listOf(1, 3, 4), later)
        check(assertNotNull(heard), length)
    }
}

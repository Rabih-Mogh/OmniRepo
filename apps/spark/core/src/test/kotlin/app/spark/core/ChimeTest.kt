package app.spark.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChimeTest {

    @Test
    fun `every sound is short`() {
        for (chime in Chime.entries) {
            val seconds = chime.samples().size / Chime.RATE.toDouble()
            assertTrue(seconds in 0.05..1.3, "$chime lasts $seconds s")
        }
    }

    @Test
    fun `every sound starts and ends on silence, so it doesn't click`() {
        for (chime in Chime.entries) {
            val samples = chime.samples()
            assertTrue(abs(samples.first().toInt()) < 400, "$chime starts at ${samples.first()}")
            assertTrue(abs(samples.last().toInt()) < 400, "$chime ends at ${samples.last()}")
        }
    }

    @Test
    fun `no sound is louder than the loudest allowed, and none is lost`() {
        for (chime in Chime.entries) {
            val peak = chime.samples().maxOf { abs(it.toInt()) } / Short.MAX_VALUE.toDouble()
            assertTrue(peak <= Chime.LOUDEST + 0.001, "$chime peaks at $peak")
            assertTrue(peak >= 0.3, "$chime peaks at $peak")
        }
    }

    @Test
    fun `a light touch is quieter than a right answer`() {
        fun peak(chime: Chime) = chime.samples().maxOf { abs(it.toInt()) }
        assertTrue(peak(Chime.TAP) < peak(Chime.RIGHT))
        assertTrue(peak(Chime.WRONG) < peak(Chime.RIGHT))
    }

    @Test
    fun `a right answer goes up and a wrong one goes down`() {
        // How often the wave crosses zero says how high it is.
        fun crossings(samples: ShortArray, from: Int, to: Int) =
            (from + 1 until to).count { (samples[it - 1] < 0) != (samples[it] < 0) } / (to - from).toDouble()

        val right = Chime.RIGHT.samples()
        assertTrue(crossings(right, right.size * 2 / 3, right.size) > crossings(right, 0, right.size / 6))
        val wrong = Chime.WRONG.samples()
        assertTrue(crossings(wrong, wrong.size * 2 / 3, wrong.size) < crossings(wrong, 0, wrong.size / 6))
    }

    @Test
    fun `the file is a WAV of one 16-bit channel`() {
        val samples = Chime.FINISH.samples()
        val wav = Chime.FINISH.wav()
        val file = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(wav.size - 8, file.getInt(4))
        assertEquals("WAVEfmt ", String(wav, 8, 8, Charsets.US_ASCII))
        assertEquals(1, file.getShort(20).toInt()) // uncompressed
        assertEquals(1, file.getShort(22).toInt()) // one channel
        assertEquals(Chime.RATE, file.getInt(24))
        assertEquals(Chime.RATE * 2, file.getInt(28))
        assertEquals(16, file.getShort(34).toInt())
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(samples.size * 2, file.getInt(40))
        assertEquals(samples[1000], file.getShort(44 + 2000))
    }
}

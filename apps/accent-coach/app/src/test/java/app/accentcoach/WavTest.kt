package app.accentcoach

import app.accentcoach.speech.SAMPLE_RATE
import app.accentcoach.speech.Wav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class WavTest {

    /** A WAV file of [samples] as a voice engine writes one, the same in every channel. */
    private fun wav(samples: ShortArray, rate: Int, channels: Int = 1, dataSize: Int? = null, extraChunk: Boolean = false): ByteArray {
        val data = samples.size * 2 * channels
        val extra = if (extraChunk) 8 + 3 + 1 else 0
        val buf = ByteBuffer.allocate(44 + extra + data).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + extra + data).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort()).putInt(rate)
            .putInt(rate * 2 * channels).putShort((2 * channels).toShort()).putShort(16)
        // An odd-sized chunk, padded to an even length, that a reader has to step over.
        if (extraChunk) buf.put("LIST".toByteArray()).putInt(3).put(byteArrayOf(1, 2, 3, 0))
        buf.put("data".toByteArray()).putInt(dataSize ?: data)
        for (s in samples) repeat(channels) { buf.putShort(s) }
        return buf.array()
    }

    private fun tone(rate: Int, seconds: Double = 0.5) =
        ShortArray((rate * seconds).toInt()) { (sin(2 * PI * 200 * it / rate) * 16000).toInt().toShort() }

    @Test
    fun audioAtTheRecognisersRateComesThroughAsItIs() {
        val audio = Wav.read(wav(shortArrayOf(0, 16384, -16384, 32767), SAMPLE_RATE))!!
        assertEquals(listOf(0f, 0.5f, -0.5f, 32767 / 32768f), audio.toList())
    }

    @Test
    fun aVoicesHigherRateIsBroughtDownToTheRecognisers() {
        val audio = Wav.read(wav(tone(24_000), 24_000))!!
        assertEquals(SAMPLE_RATE / 2, audio.size)
        // Still the same 200 Hz tone: it crosses zero going up once every 80 samples.
        val risings = (1 until audio.size).count { audio[it - 1] < 0 && audio[it] >= 0 }
        assertEquals(100.0, risings.toDouble(), 1.0)
    }

    @Test
    fun twoChannelsAreMixedIntoOne() {
        val audio = Wav.read(wav(shortArrayOf(8192, -8192), SAMPLE_RATE, channels = 2))!!
        assertEquals(listOf(0.25f, -0.25f), audio.toList())
    }

    @Test
    fun otherChunksAreSteppedOverAndAMissingLengthMeansTheRestOfTheFile() {
        assertEquals(2, Wav.read(wav(shortArrayOf(1, 2), SAMPLE_RATE, extraChunk = true))!!.size)
        assertEquals(3, Wav.read(wav(shortArrayOf(1, 2, 3), SAMPLE_RATE, dataSize = 0))!!.size)
        assertEquals(3, Wav.read(wav(shortArrayOf(1, 2, 3), SAMPLE_RATE, dataSize = -1))!!.size)
    }

    @Test
    fun whatIsNotSuchAFileIsTurnedAway() {
        assertNull(Wav.read(ByteArray(0)))
        assertNull(Wav.read("not a wav file at all, just text".toByteArray()))
        val eightBit = wav(shortArrayOf(1, 2), SAMPLE_RATE).also { it[34] = 8 }
        assertNull(Wav.read(eightBit))
    }
}

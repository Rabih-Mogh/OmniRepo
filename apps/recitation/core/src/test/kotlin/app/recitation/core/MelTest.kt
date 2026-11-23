package app.recitation.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MelTest {
    /** Half a second of made-up sound: two steady tones and one that rises. */
    private fun sound(): FloatArray {
        val n = 8000
        return FloatArray(n) { i ->
            (0.3 * sin(2 * PI * 220 * i / 16000) + 0.2 * sin(2 * PI * 1375 * i / 16000 + 0.5) + 0.1 * sin(2 * PI * (300 + 2000.0 * i / n) * i / 16000)).toFloat()
        }
    }

    @Test
    fun `it is the spectrogram Whisper's own recipe gives`() {
        val mel = Mel.of(sound())
        val frames = Mel.frames(8000)
        assertEquals(50, frames)
        assertEquals(80 * 50, mel.size)
        // The same sound through the recipe written out in numpy: a band and a frame, and the value there.
        val known = listOf(
            Triple(0, 0, 1.09240f), Triple(3, 7, 0.92310f), Triple(10, 25, 0.10905f), Triple(22, 49, 0.20983f), Triple(37, 12, 0.12032f),
            Triple(45, 30, -0.69814f), Triple(60, 40, 0.38610f), Triple(79, 49, -0.52679f), Triple(5, 0, 1.14767f), Triple(30, 48, -0.03629f),
        )
        for ((band, frame, value) in known) {
            val got = mel[band * frames + frame]
            assertTrue(abs(got - value) < 2e-3f, "band $band frame $frame: $got, not $value")
        }
    }

    @Test
    fun `silence is flat, and no sound at all gives nothing`() {
        val mel = Mel.of(FloatArray(1600))
        assertTrue(mel.all { it == mel[0] })
        assertEquals(0, Mel.of(FloatArray(100)).size)
    }
}

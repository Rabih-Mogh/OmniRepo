package app.recitation.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PitchTest {
    /** A voice-like tone: the note and its overtones, each weaker than the last. */
    private fun tone(hz: (Int) -> Double, seconds: Double, loud: Double = 0.2): FloatArray {
        var phase = 0.0
        return FloatArray((seconds * Pitch.SAMPLE_RATE).toInt()) { i ->
            phase += 2 * PI * hz(i) / Pitch.SAMPLE_RATE
            (loud * (sin(phase) + 0.5 * sin(2 * phase) + 0.25 * sin(3 * phase))).toFloat()
        }
    }

    @Test
    fun `finds the note of a voice, low or high`() {
        for (hz in listOf(82.0, 110.0, 147.0, 220.0, 330.0, 440.0, 587.0)) {
            val heard = Pitch.at(tone({ hz }, 0.2))
            assertTrue(abs(Pitch.cents(heard, hz.toFloat())) < 10f, "$hz Hz heard as $heard")
        }
    }

    @Test
    fun `a note with strong overtones is not heard an octave off`() {
        var phase = 0.0
        val sound = FloatArray(4000) { phase += 2 * PI * 130 / Pitch.SAMPLE_RATE; (0.1 * sin(phase) + 0.3 * sin(2 * phase) + 0.2 * sin(3 * phase)).toFloat() }
        assertTrue(abs(Pitch.cents(Pitch.at(sound), 130f)) < 15f, "heard ${Pitch.at(sound)}")
    }

    @Test
    fun `silence and hiss have no note`() {
        assertEquals(0f, Pitch.at(FloatArray(Pitch.FRAME)))
        val random = Random(7)
        assertEquals(0f, Pitch.at(FloatArray(Pitch.FRAME) { (random.nextFloat() - 0.5f) * 0.2f }))
    }

    @Test
    fun `a steady note wavers little and a wobbling one much`() {
        val steady = VoiceTake.of(tone({ 196.0 }, 3.0))
        assertTrue(steady.wavering < 5f, "steady wavers ${steady.wavering}")
        assertTrue(abs(steady.seconds - 3f) < 0.2f, "held ${steady.seconds}")
        assertTrue(abs(Pitch.cents(steady.middle, 196f)) < 10f)
        // Half a semitone either way, five times a second.
        val wobbly = VoiceTake.of(tone({ 196.0 * Math.pow(2.0, 0.5 / 12 * sin(2 * PI * 5 * it / Pitch.SAMPLE_RATE)) }, 3.0))
        assertTrue(wobbly.wavering in 25f..45f, "wobbly wavers ${wobbly.wavering}")
    }

    @Test
    fun `a slide from low to high gives its range`() {
        // From 110 Hz up an octave and a fifth, over four seconds.
        val n = 4 * Pitch.SAMPLE_RATE
        val take = VoiceTake.of(tone({ 110.0 * Math.pow(2.0, 19.0 / 12 * it / n) }, 4.0))
        assertTrue(take.span in 16f..19.5f, "span ${take.span}")
        assertTrue(take.range.first < 125f && take.range.second > 300f, "range ${take.range}")
    }

    @Test
    fun `the longest breath is the longest unbroken sound`() {
        val sound = tone({ 180.0 }, 2.0) + FloatArray(Pitch.SAMPLE_RATE) + tone({ 180.0 }, 5.0) + FloatArray(Pitch.SAMPLE_RATE / 2)
        val take = VoiceTake.of(sound)
        assertTrue(abs(take.longestBreath - 5f) < 0.25f, "longest ${take.longestBreath}")
        assertTrue(abs(take.seconds - 7f) < 0.4f, "voiced ${take.seconds}")
    }

    @Test
    fun `loudness that swells and fades is told from loudness kept even`() {
        val even = VoiceTake.of(tone({ 200.0 }, 2.0))
        val n = 2 * Pitch.SAMPLE_RATE
        var phase = 0.0
        val swelling = VoiceTake.of(FloatArray(n) { phase += 2 * PI * 200 / Pitch.SAMPLE_RATE; ((0.03 + 0.3 * it / n) * sin(phase)).toFloat() })
        assertTrue(even.unevenness < 1f, "even ${even.unevenness}")
        assertTrue(swelling.unevenness > 3f, "swelling ${swelling.unevenness}")
    }
}

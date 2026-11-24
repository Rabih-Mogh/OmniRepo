package app.recitation.core

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stops told from mistakes by the silences. Each case in stops.txt is a real verse as the
 * tajweed model heard an expert recite it: the verse's reference line (R), what was heard
 * (H) and when (T), the silences found in the sound (Q, in seconds), where they fall
 * among the sounds heard (S), and the breaths the recitation is taken to be in with the
 * silences (B) and without them (W): the words in the order recited, then the words that
 * end a breath. They were written by the method as it was measured on those recordings,
 * where it took the right words marked wrong from 41 of 2,175 to 29.
 */
class StopsTest {
    private class Case(val name: String, val lines: List<String>) {
        fun of(key: Char) = lines.first { it.startsWith("$key ") || it == "$key" }.drop(2)
        fun numbers(key: Char) = of(key).split(' ').filter { it.isNotEmpty() }
    }

    private fun cases(): List<Case> = javaClass.getResourceAsStream("/stops.txt")!!.bufferedReader().readText().trim().split("\n\n").map { block ->
        val lines = block.split('\n')
        Case(lines.first().drop(2), lines)
    }

    private fun breaths(expected: Expected) = "${expected.index.joinToString(",")} | ${expected.ends.joinToString(",")}"

    private fun tone(seconds: Double, loud: Double) = FloatArray((seconds * Mel.SAMPLE_RATE).toInt()) { (loud * sin(2 * PI * 220 * it / Mel.SAMPLE_RATE)).toFloat() }

    @Test
    fun `a silence is a stretch that stays well under the loud level`() {
        // Recited, a breath taken in a room that echoes (the sound drops to a fifth), recited again.
        val sound = tone(1.0, 0.5) + tone(0.5, 0.1) + tone(1.0, 0.5)
        assertContentEquals(floatArrayOf(1.0f, 1.5f), Silences.of(sound))
        // A letter held closed for a moment is no silence, nor is a sound that only softens.
        assertEquals(0, Silences.of(tone(1.0, 0.5) + tone(0.2, 0.0) + tone(1.0, 0.5)).size)
        assertEquals(0, Silences.of(tone(1.0, 0.5) + tone(0.5, 0.3) + tone(1.0, 0.5)).size)
        assertEquals(0, Silences.of(FloatArray(100)).size)
    }

    @Test
    fun `a silence is placed among the sounds heard, and the ones before and after all of them are left out`() {
        val times = floatArrayOf(0.5f, 0.7f, 0.9f, 2.0f, 2.2f, 2.4f)
        assertContentEquals(intArrayOf(3), Silences.among(times, floatArrayOf(0f, 0.4f, 1.1f, 1.9f, 2.5f, 3f)))
        assertEquals(0, Silences.among(floatArrayOf(0f), floatArrayOf(0f, 1f)).size)
    }

    @Test
    fun `takes the breaths the measured method takes`() {
        val cases = cases()
        assertTrue(cases.size >= 50)
        val differing = ArrayList<String>()
        var changed = 0
        for (case in cases) {
            val verse = TajweedReference.verse(case.of('R'))
            val times = case.numbers('T').map { it.toFloat() }.toFloatArray()
            val silences = Silences.among(times, case.numbers('Q').map { it.toFloat() }.toFloatArray())
            val wanted = case.numbers('S').map { it.toInt() }
            if (silences.toList() != wanted) differing += "${case.name}: silences at ${silences.toList()}, want $wanted"
            val with = breaths(Expected.recited(verse, case.of('H'), silences = silences))
            val without = breaths(Expected.recited(verse, case.of('H')))
            if (with != case.of('B')) differing += "${case.name}: with the silences $with, want ${case.of('B')}"
            if (without != case.of('W')) differing += "${case.name}: without them $without, want ${case.of('W')}"
            if (case.of('B') != case.of('W')) changed++
        }
        assertEquals(emptyList(), differing.take(5), "${differing.size} differ")
        assertTrue(changed >= 15, "only $changed cases where the silences matter")
    }

    @Test
    fun `a reciter who stops on a tanween and reads it as an alif is not corrected`() {
        // Al-Mulk 2, stopped on «عَمَلًا»: only two sounds differ from reciting on, too few to pass for a stop by themselves.
        val case = cases().first { it.name == "r3_s67 67:2" }
        val verse = TajweedReference.verse(case.of('R'))
        val symbols = case.of('H')
        val heard = HeardScript(symbols, FloatArray(symbols.length) { 0.99f }, case.numbers('T').map { it.toFloat() }.toFloatArray())
        val silences = case.numbers('S').map { it.toInt() }.toIntArray()

        val unaware = Corrections.of(Expected.recited(verse, symbols), heard)
        assertTrue(unaware[7].corrections.isNotEmpty())

        val aware = Corrections.of(Expected.recited(verse, symbols, silences = silences), heard)
        assertTrue(aware[7].stopped)
        assertEquals(emptyList(), aware[7].corrections.map { it.fault })
        assertEquals(emptyList(), aware[8].corrections.map { it.fault })
    }
}

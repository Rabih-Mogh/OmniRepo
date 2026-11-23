package app.recitation.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The corrections against those of the rule engine they were written after (imam.py, with
 * the HisnLabs Tajweed v2 model): real verses as the model heard expert reciters, and the
 * same with mistakes written into what was heard. Each case in corrections.txt has the
 * verse's reference line (R), what was heard (H) with the model's sureness (P) and times
 * (T), and for each word as recited (N): its place in the verse, whether the reciter
 * stopped on it, its faults, and its madds as heard.
 */
class CorrectionsTest {
    private class Case(val name: String, val reference: String, val heard: HeardScript, val words: List<String>)

    private fun cases(): List<Case> {
        val text = javaClass.getResourceAsStream("/corrections.txt")!!.bufferedReader().readText()
        return text.trim().split("\n\n").map { block ->
            val lines = block.split('\n')
            fun of(key: Char) = lines.first { it.startsWith("$key ") }.substring(2)
            val symbols = of('H')
            Case(
                of('C'), of('R'),
                HeardScript(symbols, of('P').split(' ').map { it.toFloat() }.toFloatArray(), of('T').split(' ').map { it.toFloat() }.toFloatArray()),
                lines.filter { it.startsWith("N ") }.map { it.substring(2) },
            )
        }
    }

    private fun told(words: List<WordRecited>): List<String> = words.map { w ->
        "${w.index} ${if (w.stopped) 1 else 0} ${w.corrections.map { it.fault.name }.sorted().joinToString(",")};${w.madds.joinToString(",") { "${it.rule.name}=${it.counts}" }}"
    }

    @Test
    fun `says what the rule engine it follows says`() {
        val cases = cases()
        assertTrue(cases.size > 200)
        val differing = ArrayList<String>()
        for (case in cases) {
            val verse = TajweedReference.verse(case.reference)
            val expected = Expected.recited(verse, case.heard.symbols)
            val got = told(Corrections.of(expected, case.heard))
            if (got != case.words) differing += "${case.name}\n  want ${case.words}\n  got  $got"
        }
        assertEquals(emptyList(), differing.take(5), "${differing.size} of ${cases.size} cases differ")
    }
}

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
    fun `the word reached is known while the verse is still being recited`() {
        // Al-Fatihah's second verse: four words.
        val line = cases().first { it.name.contains(" 1:2 as recited") }.reference
        val whole = Expected.breath(TajweedReference.verse(line), 0, 4)
        assertEquals(-1, whole.reached(""))
        assertEquals(0, whole.reached("ءَلحَم"))
        assertEquals(1, whole.reached("ءَلحَمدُلِللَا"))
        assertEquals(2, whole.reached("ءَلحَمدُلِللَااهِرَب"))
        assertEquals(3, whole.reached("ءَلحَمدُلِللَااهِرَببِلعَاالَ"))
    }

    @Test
    fun `a correction names the rules it is about, which give it its colour`() {
        // A madd by its kind; the nasal rules; the echo. A mistake in the wording has no rule.
        assertEquals(Family.MADD_WAJIB, Correction(Fault.MADD_SHORT, clear = false, madd = Madd.MUTTASIL).family)
        assertEquals(Family.MADD_JAIZ, Correction(Fault.MADD_LONG, clear = false, madd = Madd.ARID).family)
        assertEquals(setOf(Rule.MADD_LAZIM), Correction(Fault.MADD_MISSING, clear = true, madd = Madd.LAZIM).rules)
        assertEquals(Family.MADD_TABII, Correction(Fault.MADD_MISSING, clear = true, madd = Madd.NATURAL).family)
        assertEquals(setOf(Rule.IKHFA, Rule.IKHFA_SHAFAWI), Correction(Fault.IKHFA_CLEAR, clear = false).rules)
        assertEquals(Family.NASAL, Correction(Fault.IDGHAM_MISSED, clear = false, wanted = "ي").family)
        assertEquals(Family.QUIET, Correction(Fault.IDGHAM_MISSED, clear = false, wanted = "ر").family)
        assertEquals(Family.ECHO, Correction(Fault.QALQALAH_MISSING, clear = false, wanted = "ب").family)
        assertEquals(null, Correction(Fault.LETTER, clear = true, wanted = "ق", heard = "ك").family)
        // Every fault is placed, and every family but the heavy letters, which the model doesn't judge, has a fault that leads to it.
        val reached = Fault.entries.flatMap { fault -> Madd.entries.mapNotNull { Correction(fault, clear = false, madd = it, wanted = "ي").family } + listOfNotNull(Correction(fault, clear = false, wanted = "ل").family) }.toSet()
        assertEquals(Family.entries.toSet() - Family.HEAVY, reached)
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

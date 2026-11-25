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

    /** A real verse as the model heard an expert recite it (follow.txt, see [FollowerTest]). */
    private class Said(val name: String, val forms: List<WordForms>, val heard: HeardScript, val silences: FloatArray) {
        /** Where in what was heard each word of the verse begins, and after the last, where it all ends. */
        val begins: IntArray by lazy {
            val whole = Expected.breath(forms, 0, forms.size)
            val at = IntArray(whole.script.length + 1)
            for (c in Edits.between(whole.script, heard.symbols)) {
                for (k in c.from..c.until) at[k] = c.toFrom + if (c.until > c.from) Math.rint((k - c.from).toDouble() * (c.toUntil - c.toFrom) / (c.until - c.from)).toInt() else 0
            }
            IntArray(forms.size + 1) { if (it == forms.size) heard.symbols.length else at[whole.spans[2 * it]] }
        }

        /** How it goes with the sounds [from] until [until] never heard. */
        fun without(from: Int, until: Int): List<WordRecited>? {
            val left = HeardScript(
                heard.symbols.removeRange(from, until),
                heard.sureness.filterIndexed { i, _ -> i !in from until until }.toFloatArray(),
                heard.times.filterIndexed { i, _ -> i !in from until until }.toFloatArray(),
            )
            if (left.symbols.length < 2) return null
            return Corrections.of(Expected.recited(forms, left.symbols, silences = Silences.among(left.times, silences)), left)
        }
    }

    private fun said(): List<Said> = javaClass.getResourceAsStream("/follow.txt")!!.bufferedReader().readText().trim().split("\n\n").map { block ->
        val lines = block.split('\n')
        fun of(key: Char) = lines.first { it.startsWith("$key ") || it == "$key" }.drop(2)
        fun numbers(key: Char) = of(key).split(' ').filter { it.isNotEmpty() }.map { it.toFloat() }.toFloatArray()
        Said(of('C'), TajweedReference.verse(of('R')), HeardScript(of('H'), numbers('P'), numbers('T')), numbers('Q'))
    }

    private fun faults(word: WordRecited) = word.corrections.map { it.fault }

    @Test
    fun `a word that was not said is told as not said, and judged for nothing`() {
        var tried = 0
        var blamed = 0
        for (verse in said()) {
            val whole = Corrections.of(Expected.recited(verse.forms, verse.heard.symbols, silences = Silences.among(verse.heard.times, verse.silences)), verse.heard).associate { it.index to faults(it) }
            // Every word of it in turn, passed over.
            for (k in verse.forms.indices) {
                val words = verse.without(verse.begins[k], verse.begins[k + 1]) ?: continue
                tried++
                val passed = words.single { it.index == k }
                assertEquals(listOf(Fault.WORD_MISSING), faults(passed), "${verse.name} without word $k")
                assertTrue(passed.unsaid && passed.madds.isEmpty() && passed.listened.isEmpty(), "${verse.name} without word $k: something was still listened for in it")
                // And the words that were said aren't corrected for it.
                if (words.any { it.index != k && (faults(it) - whole.getValue(it.index).toSet()).isNotEmpty() }) blamed++
            }
        }
        assertTrue(tried > 150)
        // But where the sounds taken out here took one of a neighbour's with them: a handful.
        assertTrue(blamed * 50 <= tried, "a word that was said was corrected for one that wasn't in $blamed of $tried")
    }

    @Test
    fun `a verse recited only in part has the rest told as not said`() {
        var tried = 0
        var blamed = 0
        for (verse in said()) {
            val whole = Corrections.of(Expected.recited(verse.forms, verse.heard.symbols, silences = Silences.among(verse.heard.times, verse.silences)), verse.heard).associate { it.index to faults(it) }
            for (k in 1 until verse.forms.size) {
                val words = verse.without(verse.begins[k], verse.heard.symbols.length) ?: continue
                tried++
                for (word in words) {
                    if (word.index >= k) assertTrue(faults(word) == listOf(Fault.WORD_MISSING) && word.madds.isEmpty() && word.listened.isEmpty(), "${verse.name}, $k words said: word ${word.index} was told ${faults(word)}")
                    else assertTrue(!word.unsaid, "${verse.name}, $k words said: word ${word.index} was said")
                }
                if (words.any { it.index < k && (faults(it) - whole.getValue(it.index).toSet()).isNotEmpty() }) blamed++
            }
        }
        assertTrue(tried > 150)
        assertTrue(blamed * 50 <= tried, "a word that was said was corrected for those that weren't in $blamed of $tried")
    }

    @Test
    fun `a reader who stops in the middle of a verse is held to how the word sounds at a stop`() {
        // 67:5 as its reciter said it: he stops after its eighth word, «لِّلشَّيَـٰطِينِ», and says it as it sounds at a
        // stop, its madd long. Left there, the verse's last word, which ends on a long madd too, isn't taken for said.
        val verse = said().first { it.name.endsWith(" 67:5") }
        val words = verse.without(verse.begins[8], verse.heard.symbols.length)!!
        assertEquals(List(8) { emptyList<Fault>() } + List(4) { listOf(Fault.WORD_MISSING) }, words.map(::faults))
        assertEquals(listOf(7), words.filter { it.stopped }.map { it.index })
    }
}

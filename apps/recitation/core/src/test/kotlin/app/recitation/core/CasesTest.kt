package app.recitation.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Where a topic is really at work for a reciter, which depends on where that reciter stops. */
class CasesTest {
    private fun verses(vararg verses: String) = verses.map { it.split(' ') }

    /** The reciter stops at the end of every verse, and after the words [also] (counted through the surah from 0). */
    private fun stops(verses: List<List<String>>, vararg also: Int) = Tajweed.stopsAtVerseEnds(verses.map { it.size }).also { for (i in also) it[i] = true }

    private fun told(places: List<CaseAt>) = places.map { "${it.verse}:${it.word} ${it.case.rule} ${it.case.letter}>${it.case.into}" + (if (it.case.tanween) " tanween" else "") + (if (it.case.heavy) " heavy" else "") + (if (it.case.stopped) " stop" else "") }

    @Test
    fun `a nun at the end of a word is under a rule only if the reciter runs on`() {
        val surah = verses("مِن قَبْلِ ذَٰلِكَ", "مِنۢ بَعْدِ مَا", "مَن يَقُولُ", "مِن رَّبِّهِمْ")
        // Reciting each verse in one breath: hidden before ق (a heavy letter), turned before ب, merged into ي and into ر.
        assertEquals(listOf("0:0 IKHFA ن>ق heavy"), told(Cases.places(surah, stops(surah), Topic.IKHFA)))
        assertEquals(listOf("1:0 IQLAB ن>ب"), told(Cases.places(surah, stops(surah), Topic.IQLAB)))
        assertEquals(listOf("2:0 IDGHAM_GHUNNAH ن>ي", "3:0 IDGHAM_BILA_GHUNNAH ن>ر"), told(Cases.places(surah, stops(surah), Topic.IDGHAM).filter { it.word == 0 }))
        // A reciter who takes a breath after «مِن» says its nun clearly: there is no ikhfa there to hear.
        assertEquals(emptyList(), Cases.places(surah, stops(surah, 0), Topic.IKHFA))
        // And one who stops after «مَن» has merged nothing.
        assertEquals(listOf("3:0"), Cases.places(surah, stops(surah, 6), Topic.IDGHAM).filter { it.word == 0 }.map { "${it.verse}:${it.word}" })
    }

    @Test
    fun `a tanween, and a nun inside a word, are told as what they are`() {
        val surah = verses("عَلِيمًا قَدِيرًا", "أَنتُمْ", "سَمِيعٌۢ بَصِيرٌ")
        assertEquals(listOf("0:0 IKHFA >ق tanween heavy", "1:0 IKHFA ن>ت stop"), told(Cases.places(surah, stops(surah), Topic.IKHFA)))
        assertEquals(listOf("2:0 IQLAB >ب tanween"), told(Cases.places(surah, stops(surah), Topic.IQLAB)))
    }

    @Test
    fun `the madd of a stop is there only where the reciter stops`() {
        val surah = verses("ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ", "ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ")
        // Stopping at each verse's end: the long vowel before the last letter of each last word.
        assertEquals(listOf("0:3 MADD_ARID ي>ن stop", "1:1 MADD_ARID ي>م stop"), told(Cases.places(surah, stops(surah), Topic.MADD_ARID)))
        // A reciter who runs the first verse into the second holds nothing at «ٱلْعَـٰلَمِينَ»: there is no madd of a stop to hear there.
        val joined = Tajweed.stopsAtVerseEnds(listOf(4, 2)).also { it[3] = false }
        assertEquals(listOf("1:1"), Cases.places(surah, joined, Topic.MADD_ARID).map { "${it.verse}:${it.word}" })
        // Run on, such a vowel is a natural madd: «نَسْتَعِينُ» recited into the next verse has one, which the page gives no mark.
        val fifth = verses("إِيَّاكَ نَسْتَعِينُ", "ٱهْدِنَا")
        val on = Tajweed.stopsAtVerseEnds(listOf(2, 1)).also { it[1] = false }
        assertTrue(Cases.places(fifth, on, Topic.MADD_NATURAL).any { it.verse == 0 && it.word == 1 && it.case.rule == null && it.case.letter == "ي" })
        assertTrue(Cases.places(fifth, stops(fifth), Topic.MADD_NATURAL).none { it.verse == 0 && it.word == 1 })
        // And one who stops in the middle of a verse has a madd of the stop there too.
        val midway = stops(surah, 1)
        assertTrue(Cases.places(surah, midway, Topic.STOP).map { "${it.verse}:${it.word}" } == listOf("0:1", "0:3", "1:1"))
    }

    @Test
    fun `an echo is slight inside a word and clearer on the word stopped on`() {
        val surah = verses("لَمْ يَلِدْ وَلَمْ يُولَدْ", "قُلْ أَعُوذُ بِرَبِّ ٱلْفَلَقِ")
        assertEquals(listOf("0:1 QALQALAH د>و", "0:3 QALQALAH د> stop", "1:3 QALQALAH ق> stop"), told(Cases.places(surah, stops(surah), Topic.QALQALAH)))
        // Run on into the next verse, «ٱلْفَلَقِ» keeps its vowel: no echo.
        val joined = Tajweed.stopsAtVerseEnds(listOf(4, 4, 1)).also { it[7] = false }
        assertEquals(listOf("0:1", "0:3"), Cases.places(surah + listOf(listOf("مِن")), joined, Topic.QALQALAH).map { "${it.verse}:${it.word}" })
    }

    @Test
    fun `a doubled nun, a madd by its kind, and a letter of a pair`() {
        val surah = verses("إِنَّ ٱلَّذِينَ", "بِمَآ أُنزِلَ", "جَآءَ", "وَلَا ٱلضَّآلِّينَ", "ٱهْدِنَا ٱلصِّرَٰطَ")
        assertEquals(listOf("0:0 GHUNNAH ن>ل"), told(Cases.places(surah, stops(surah), Topic.GHUNNAH)).map { it.substringBefore(" stop") })
        assertEquals(listOf("1:0 MADD_MUNFASIL ا>ء"), told(Cases.places(surah, stops(surah), Topic.MADD_MUNFASIL)))
        assertEquals(listOf("2:0 MADD_MUTTASIL ا>ء stop"), told(Cases.places(surah, stops(surah), Topic.MADD_MUTTASIL)))
        assertEquals(listOf("3:1 MADD_LAZIM ا>ل stop"), told(Cases.places(surah, stops(surah), Topic.MADD_LAZIM)))
        // Stopped after «بِمَآ», its madd is only the natural one: not an example of the munfasil.
        assertEquals(emptyList(), Cases.places(surah, stops(surah, 2), Topic.MADD_MUNFASIL))
        assertEquals(listOf("4:1 null ص>"), told(Cases.places(surah, stops(surah), Topic.LETTERS, "صس")).map { it.substringBefore(" stop") })
    }
}

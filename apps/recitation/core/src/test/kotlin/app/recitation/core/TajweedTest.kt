package app.recitation.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules of recitation found in real verses, written as Quran.com's Uthmani text writes them. */
class TajweedTest {

    /** The rules found in [verse] (words apart by " | "), as "letters=RULE", for a reciter who stops after the words in [stops] and at the end. */
    private fun found(verse: String, vararg stops: Int): List<String> {
        val words = verse.split(" | ")
        val stopping = BooleanArray(words.size) { it in stops || it == words.lastIndex }
        return Tajweed.marks(words, stopping).flatMapIndexed { w, marks -> marks.map { "${words[w].substring(it.from, it.to)}=${it.rule}" } }
    }

    private fun assertFinds(verse: String, vararg expected: String, stops: IntArray = intArrayOf()) {
        val all = found(verse, *stops)
        for (one in expected) assertTrue(one in all, "expected $one among $all")
    }

    @Test
    fun `an n is hidden before most letters`() {
        // 2:4: "unzila" inside a word, "min qablika" across two.
        assertFinds("بِمَا | أُنزِلَ | مِن | قَبْلِكَ", "ن=IKHFA")
        assertEquals(2, found("بِمَا | أُنزِلَ | مِن | قَبْلِكَ").count { it == "ن=IKHFA" })
        // The n of a tanween too: only the tanween sign is marked, not the letter under it.
        assertFinds("مَّرَضٌۭ | فَزَادَهُمُ", "ٌۭ=IKHFA")
    }

    @Test
    fun `an n is merged into y, n, m and w through the nose, across words only`() {
        // 2:8 "man yaqulu": the n and the y it runs into.
        assertFinds("مَن | يَقُولُ | ءَامَنَّا", "ن=IDGHAM_GHUNNAH", "يَ=IDGHAM_GHUNNAH")
        // 2:5 "hudan min": the tanween, and the m takes the nasal hold as a doubled m.
        assertFinds("هُدًۭى | مِّن | رَّبِّهِمْ", "ًۭ=IDGHAM_GHUNNAH", "مِّ=GHUNNAH")
        // Inside one word the n stays clear: "ad-dunya".
        assertFalse(found("ٱلْحَيَوٰةِ | ٱلدُّنْيَا").any { it.startsWith("ن") })
    }

    @Test
    fun `an n is merged without a trace into l and r`() {
        assertFinds("مِّن | رَّبِّهِمْ", "ن=SILENT")
        assertFinds("يَكُن | لَّهُۥ | كُفُوًا | أَحَدٌۢ", "ن=SILENT")
        assertFinds("هُدًۭى | لِّلْمُتَّقِينَ", "ًۭ=SILENT")
    }

    @Test
    fun `an n turns into an m before b`() {
        assertFinds("مِنۢ | بَيْنِ", "نۢ=IQLAB")
        assertFinds("أَلِيمٌۢ | بِمَا | كَانُوا۟", "ٌۢ=IQLAB")
    }

    @Test
    fun `an n stays clear before the throat letters`() {
        // 2:7 "'adhabun 'azim", 1:7 "an'amta", 36:9 "wa min khalfihim".
        assertFalse(found("عَذَابٌ | عَظِيمٌۭ").any { it.startsWith("ٌ=") })
        assertFalse(found("أَنْعَمْتَ | عَلَيْهِمْ").any { it.startsWith("ن") })
        assertFalse(found("وَمِنْ | خَلْفِهِمْ").any { it.startsWith("ن") })
    }

    @Test
    fun `an m is hidden before b and merged into m`() {
        assertFinds("وَمَا | هُم | بِمُؤْمِنِينَ", "م=IKHFA")
        assertFinds("قُلُوبِهِم | مَّرَضٌۭ | فَزَادَهُمُ", "م=IDGHAM_GHUNNAH", "مَّ=GHUNNAH")
    }

    @Test
    fun `a doubled n or m is held through the nose`() {
        assertFinds("إِنَّ | ٱلَّذِينَ", "نَّ=GHUNNAH")
        assertFinds("ثُمَّ | ٱسْتَوَىٰ", "مَّ=GHUNNAH")
    }

    @Test
    fun `stopped q, t, b, j and d echo`() {
        assertFinds("لَمْ | يَلِدْ | وَلَمْ | يُولَدْ", "دْ=QALQALAH")
        assertFinds("مِن | قَبْلِكَ", "بْ=QALQALAH")
        assertFinds("يَجْعَلُونَ | أَصَـٰبِعَهُمْ", "جْ=QALQALAH")
    }

    @Test
    fun `long vowels under the wavy line are drawn out`() {
        // Before a hamza in the same word (muttasil).
        assertFinds("أُو۟لَـٰٓئِكَ | عَلَىٰ", "ـٰٓ=MADD_REQUIRED")
        assertFinds("سَوَا\u0653ءٌ | عَلَيْهِمْ", "ا\u0653=MADD_REQUIRED")
        // Before a doubled letter (lazim), and the letters some surahs open with.
        assertFinds("وَلَا | ٱلضَّا\u0653لِّينَ", "ا\u0653=MADD_LAZIM")
        assertFinds("الٓمٓ", "لٓ=MADD_LAZIM", "مٓ=MADD_LAZIM")
        // An alif and its wavy line written as one character are the same thing.
        assertFinds("وَلَا | ٱلضَّآلِّينَ", "آ=MADD_LAZIM")
    }

    @Test
    fun `letters that are written and not sounded`() {
        // The l of "al-" before a sun letter, the joining alif when run into, an alif after the w of the plural.
        assertFinds("وَمِنَ | ٱلنَّاسِ", "ٱ=SILENT", "ل=SILENT")
        assertFinds("إِنَّ | ٱلَّذِينَ | كَفَرُوا۟ | سَوَا\u0653ءٌ", "ا۟=SILENT")
        // In the name of Allah the two l's are the name's own.
        assertFalse(found("بِسْمِ | ٱللَّهِ").any { it.startsWith("ل") })
        // A joining alif that opens the recitation is sounded.
        assertFalse(found("ٱلْحَمْدُ | لِلَّهِ").any { it.startsWith("ٱ") })
    }

    // ---- what depends on stopping

    @Test
    fun `an n at the end of a word is merged only if the reciter runs on`() {
        val verse = "هُدًۭى | مِّن | رَّبِّهِمْ ۖ | وَأُو۟لَـٰٓئِكَ"
        assertFinds(verse, "ن=SILENT")
        // Stopping on "min": the n is said as it is, and nothing is merged.
        assertFalse(found(verse, 1).any { it == "ن=SILENT" })
        // Stopping before "min": its m is no longer doubled by the word before, so it isn't held.
        assertFalse(found(verse, 0).any { it == "مِّ=GHUNNAH" || it == "ًۭ=IDGHAM_GHUNNAH" })
    }

    @Test
    fun `a long vowel before a hamza in the next word is drawn out only if the reciter runs on`() {
        val verse = "بِمَا\u0653 | أُنزِلَ | إِلَيْكَ"
        assertFinds(verse, "ا\u0653=MADD_ALLOWED")
        assertFalse(found(verse, 0).any { it == "ا\u0653=MADD_ALLOWED" })
        // "ya ayyuha" is written as one word but read as two.
        assertFinds("يَـٰٓأَيُّهَا | ٱلنَّاسُ", "ـٰٓ=MADD_ALLOWED")
        // The long "hi" of a pronoun before a hamza.
        assertFinds("حُكْمِهِۦٓ | أَحَدًۭا", "ۦٓ=MADD_ALLOWED")
    }

    @Test
    fun `stopping on a word draws out the long vowel before its last letter`() {
        // 1:2 to 1:4, stopping at each verse's end.
        assertFinds("رَبِّ | ٱلْعَـٰلَمِينَ", "ي=MADD_ALLOWED")
        assertFinds("ٱلرَّحْمَـٰنِ | ٱلرَّحِيمِ", "ي=MADD_ALLOWED")
        assertFinds("هُمُ | ٱلْمُفْلِحُونَ", "و=MADD_ALLOWED")
        assertFinds("ذَٰلِكَ | ٱلْكِتَـٰبُ", "ـٰ=MADD_ALLOWED")
        // A soft w or y too: "khawf", "al-bayt".
        assertFinds("مِّنْ | خَوْفٍۭ", "وْ=MADD_ALLOWED")
        // Running on, the same vowels are ordinary.
        assertFalse(found("ٱلرَّحْمَـٰنِ | ٱلرَّحِيمِ | مَـٰلِكِ").any { it.endsWith("MADD_ALLOWED") })
    }

    @Test
    fun `stopping on q, t, b, j or d makes it echo, whatever vowel it had`() {
        assertFinds("هُوَ | ٱللَّهُ | أَحَدٌ", "دٌ=QALQALAH")
        assertFinds("بِرَبِّ | ٱلْفَلَقِ", "قِ=QALQALAH")
        assertFalse(found("بِرَبِّ | ٱلْفَلَقِ | مِن").any { it.endsWith("QALQALAH") })
        // An -an at a stop becomes a long a instead: no echo.
        assertFalse(found("مَّا | لَهُم | عَذَابًۭا").any { it.endsWith("QALQALAH") })
    }

    @Test
    fun `a joining alif is silent when run into and sounded after a stop`() {
        val verse = "ٱلرَّحْمَـٰنِ | ٱلرَّحِيمِ"
        assertEquals(1, found(verse).count { it == "ٱ=SILENT" })
        assertEquals(0, found(verse, 0).count { it == "ٱ=SILENT" })
        // "ana": its alif is said only at a stop.
        assertFinds("إِنَّمَا | أَنَا۠ | رَسُولُ", "ا۠=SILENT")
        assertFalse(found("إِنَّمَا | أَنَا۠ | رَسُولُ", 1).any { it == "ا۠=SILENT" })
    }

    @Test
    fun `without a recording the reciter is taken to stop at the end of each verse`() {
        assertEquals(listOf(false, false, true, false, true), Tajweed.stopsAtVerseEnds(listOf(3, 2)).toList())
    }
}

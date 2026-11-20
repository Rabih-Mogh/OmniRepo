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
        assertFinds("مِّن | رَّبِّهِمْ", "ن=IDGHAM_BILA_GHUNNAH")
        assertFinds("يَكُن | لَّهُۥ | كُفُوًا | أَحَدٌۢ", "ن=IDGHAM_BILA_GHUNNAH")
        assertFinds("هُدًۭى | لِّلْمُتَّقِينَ", "ًۭ=IDGHAM_BILA_GHUNNAH")
    }

    @Test
    fun `an n turns into an m before b`() {
        assertFinds("مِنۢ | بَيْنِ", "نۢ=IQLAB")
        assertFinds("أَلِيمٌۢ | بِمَا | كَانُوا۟", "ٌۢ=IQLAB")
    }

    @Test
    fun `a tanween before a joining alif is said clear`() {
        // 19:7 "bi-ghulamin ismuhu" is read "ghulamini-smuhu".
        assertFalse(found("بِغُلَـٰمٍ | ٱسْمُهُۥ | يَحْيَىٰ").any { it.startsWith("ٍ=") })
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
        assertFinds("وَمَا | هُم | بِمُؤْمِنِينَ", "م=IKHFA_SHAFAWI")
        assertFinds("قُلُوبِهِم | مَّرَضٌۭ | فَزَادَهُمُ", "م=IDGHAM_SHAFAWI", "مَّ=GHUNNAH")
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
        assertFinds("أُو۟لَـٰٓئِكَ | عَلَىٰ", "ـٰٓ=MADD_MUTTASIL")
        assertFinds("سَوَا\u0653ءٌ | عَلَيْهِمْ", "ا\u0653=MADD_MUTTASIL")
        // Before a doubled letter (lazim), and the letters some surahs open with.
        assertFinds("وَلَا | ٱلضَّا\u0653لِّينَ", "ا\u0653=MADD_LAZIM")
        assertFinds("الٓمٓ", "لٓ=MADD_LAZIM", "مٓ=MADD_LAZIM")
        // An alif and its wavy line written as one character are the same thing.
        assertFinds("وَلَا | ٱلضَّآلِّينَ", "آ=MADD_LAZIM")
    }

    @Test
    fun `letters that are written and not sounded`() {
        // The l of "al-" before a sun letter, the joining alif when run into, an alif after the w of the plural.
        assertFinds("وَمِنَ | ٱلنَّاسِ", "ٱ=HAMZAT_WASL", "ل=LAM_SHAMSIYYAH")
        assertFinds("إِنَّ | ٱلَّذِينَ | كَفَرُوا۟ | سَوَا\u0653ءٌ", "ا۟=SILENT")
        // In the name of Allah the two l's are the name's own.
        assertFalse(found("بِسْمِ | ٱللَّهِ").any { it.startsWith("ل") })
        // "al-lahab" begins like the name and is not it.
        assertFinds("ذَاتَ | ٱللَّهَبِ", "ل=LAM_SHAMSIYYAH")
        // A joining alif that opens the recitation is sounded.
        assertFalse(found("ٱلْحَمْدُ | لِلَّهِ").any { it.startsWith("ٱ") })
    }

    // ---- what depends on stopping

    @Test
    fun `an n at the end of a word is merged only if the reciter runs on`() {
        val verse = "هُدًۭى | مِّن | رَّبِّهِمْ ۖ | وَأُو۟لَـٰٓئِكَ"
        assertFinds(verse, "ن=IDGHAM_BILA_GHUNNAH")
        // Stopping on "min": the n is said as it is, and nothing is merged.
        assertFalse(found(verse, 1).any { it == "ن=IDGHAM_BILA_GHUNNAH" })
        // Stopping before "min": its m is no longer doubled by the word before, so it isn't held.
        assertFalse(found(verse, 0).any { it == "مِّ=GHUNNAH" || it == "ًۭ=IDGHAM_GHUNNAH" })
    }

    @Test
    fun `a long vowel before a hamza in the next word is drawn out only if the reciter runs on`() {
        val verse = "بِمَا\u0653 | أُنزِلَ | إِلَيْكَ"
        assertFinds(verse, "ا\u0653=MADD_MUNFASIL")
        assertFalse(found(verse, 0).any { it == "ا\u0653=MADD_MUNFASIL" })
        // "ya ayyuha" is written as one word but read as two.
        assertFinds("يَـٰٓأَيُّهَا | ٱلنَّاسُ", "ـٰٓ=MADD_MUNFASIL")
        // The long "hi" of a pronoun before a hamza.
        assertFinds("حُكْمِهِۦٓ | أَحَدًۭا", "ۦٓ=MADD_SILAH_KUBRA")
    }

    @Test
    fun `stopping on a word draws out the long vowel before its last letter`() {
        // 1:2 to 1:4, stopping at each verse's end.
        assertFinds("رَبِّ | ٱلْعَـٰلَمِينَ", "ي=MADD_ARID")
        assertFinds("ٱلرَّحْمَـٰنِ | ٱلرَّحِيمِ", "ي=MADD_ARID")
        assertFinds("هُمُ | ٱلْمُفْلِحُونَ", "و=MADD_ARID")
        assertFinds("ذَٰلِكَ | ٱلْكِتَـٰبُ", "ـٰ=MADD_ARID")
        // A soft w or y too: "khawf", "al-bayt".
        assertFinds("مِّنْ | خَوْفٍۭ", "وْ=MADD_LIN")
        // Running on, the same vowels are ordinary.
        assertFalse(found("ٱلرَّحْمَـٰنِ | ٱلرَّحِيمِ | مَـٰلِكِ").any { it.endsWith("MADD_ARID") })
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
        assertEquals(1, found(verse).count { it == "ٱ=HAMZAT_WASL" })
        assertEquals(0, found(verse, 0).count { it == "ٱ=HAMZAT_WASL" })
        // "ana": its alif is said only at a stop.
        assertFinds("إِنَّمَا | أَنَا۠ | رَسُولُ", "ا۠=SILENT")
        assertFalse(found("إِنَّمَا | أَنَا۠ | رَسُولُ", 1).any { it == "ا۠=SILENT" })
    }

    @Test
    fun `without a recording the reciter is taken to stop at the end of each verse`() {
        assertEquals(listOf(false, false, true, false, true), Tajweed.stopsAtVerseEnds(listOf(3, 2)).toList())
    }

    // ---- the finer names

    @Test
    fun `letters merged into the next are named by how near the two are`() {
        // The same letter; letters from one place; letters from close by.
        assertFinds("ٱضْرِب | بِّعَصَاكَ", "ب=IDGHAM_MUTAMATHILAYN")
        assertFinds("قَد | تَّبَيَّنَ", "د=IDGHAM_MUTAJANISAYN")
        assertFinds("ٱرْكَب | مَّعَنَا", "ب=IDGHAM_MUTAJANISAYN")
        assertFinds("وَقُل | رَّبِّ", "ل=IDGHAM_MUTAQARIBAYN")
        assertFinds("أَلَمْ | نَخْلُقكُّم", "ق=IDGHAM_MUTAQARIBAYN")
    }

    @Test
    fun `ordinary long vowels written with a small letter`() {
        // A small alif, alone on its letter or on a stroke; the pronoun's long "hu", only when run on.
        assertFinds("ذَٰلِكَ | ٱلْكِتَـٰبُ | لَا", "ٰ=MADD_TABII", "ـٰ=MADD_TABII")
        assertFinds("إِنَّهُۥ | كَانَ", "ۥ=MADD_SILAH_SUGHRA")
        assertFalse(found("وَمَا | لَهُۥ").any { it.endsWith("MADD_SILAH_SUGHRA") })
        // On a letter that is under a rule of its own, the small alif keeps its colour: "as-sirat", "wahidatan".
        assertFinds("ٱهْدِنَا | ٱلصِّرَٰطَ | ٱلْمُسْتَقِيمَ", "رَ=TAFKHIM_RA", "ٰ=MADD_TABII")
        assertFinds("صَيْحَةًۭ | وَٰحِدَةًۭ | فَإِذَا", "وَ=IDGHAM_GHUNNAH", "ٰ=MADD_TABII")
        // A long i written as a small y above the line.
        assertFinds("وَيَقْتُلُونَ | ٱلنَّبِيِّـۧنَ | بِغَيْرِ", "ـۧ=MADD_TABII")
        assertFinds("وَيَقْتُلُونَ | ٱلنَّبِيِّـۧنَ", "ـۧ=MADD_ARID")
        // Stopping on "-an" makes a long a of it.
        assertFinds("وَكَانَ | ٱللَّهُ | عَلِيمًا", "ًا=MADD_IWAD")
        assertFalse(found("عَلِيمًا | حَكِيمًۭا").any { it == "ًا=MADD_IWAD" })
    }

    @Test
    fun `an r is heavy with a or u, light with i, and otherwise as what comes before it`() {
        fun heavy(verse: String, vararg stops: Int) = found(verse, *stops).filter { it.endsWith("TAFKHIM_RA") }.map { it.substringBefore('=') }
        assertEquals(listOf("رَ"), heavy("رَبِّ | ٱلْعَـٰلَمِينَ"))
        assertEquals(listOf("رُ"), heavy("كَفَرُوا۟ | سَوَا\u0653ءٌ"))
        assertEquals(emptyList(), heavy("رِجَالٌۭ | لَّا"))
        // Without a vowel: after a or u heavy, after i light, unless a raised letter follows.
        assertEquals(listOf("رْ"), heavy("مَرْيَمَ | ٱبْنَتَ"))
        assertEquals(emptyList(), heavy("فِرْعَوْنَ | وَمَلَإِي۟هِۦ"))
        assertEquals(listOf("رْ"), heavy("فِى | قِرْطَاسٍۢ | فَلَمَسُوهُ"))
        // After a joining alif the i is not the word's own.
        assertEquals(listOf("رْ"), heavy("ٱرْجِعُوٓا۟ | إِلَىٰٓ"))
        // Stopped on, whatever vowel it had: after a, u or a long a heavy; after i or a y light.
        assertEquals(listOf("رِ"), heavy("لَيْلَةِ | ٱلْقَدْرِ"))
        assertEquals(listOf("رِ"), heavy("مِنَ | ٱلنَّارِ"))
        assertEquals(emptyList(), heavy("عَلَىٰ | كُلِّ | شَىْءٍۢ | قَدِيرٌۭ"))
        assertEquals(emptyList(), heavy("صَاحِبُ | ٱلذِّكْرِ"))
        assertEquals(emptyList(), heavy("ٱلْقَدْرِ | خَيْرٌۭ | مِّنْ", 1))
    }

    @Test
    fun `the l of the name of Allah is heavy after a or u and light after i`() {
        fun heavy(verse: String, vararg stops: Int) = found(verse, *stops).count { it.endsWith("TAFKHIM_LAM") }
        assertEquals(1, heavy("قُلْ | هُوَ | ٱللَّهُ | أَحَدٌ"))
        assertEquals(1, heavy("ٱللَّهُ | ٱلصَّمَدُ"))
        assertEquals(0, heavy("بِسْمِ | ٱللَّهِ"))
        assertEquals(0, heavy("ٱلْحَمْدُ | لِلَّهِ"))
        assertEquals(1, heavy("وَٱللَّهُ | مُحِيطٌۢ"))
        assertEquals(0, heavy("ءَامَنَّا | بِٱللَّهِ"))
        assertEquals(1, heavy("قَالُوا۟ | ٱللَّهُمَّ"))
        assertEquals(0, heavy("قُلِ | ٱللَّهُمَّ"))
    }
}

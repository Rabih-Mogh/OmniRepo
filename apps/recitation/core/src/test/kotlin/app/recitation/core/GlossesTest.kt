package app.recitation.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** Arabic word meanings, which come a verse at a time, put under the words they explain. */
class GlossesTest {
    /** Al-Fatihah's words in everyday spelling, and its meanings as QuranEnc.com gives them. */
    private val fatihah = listOf(
        "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ", "الرَّحْمَٰنِ الرَّحِيمِ", "مَالِكِ يَوْمِ الدِّينِ",
        "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ", "اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ", "صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ",
    ).map { it.split(' ') }
    private val told = listOf(
        "بِسْمِ اللهِ: أَيْ: أَبْتَدِئُ قِرَاءَتِي مُسْتَعِينًا بِاسْمِ اللهِ.\r\nالرَّحْمنِ: الَّذِي وَسِعَتْ رَحْمَتُهُ جَمِيعَ الْخَلْقِ.\r\nالرَّحِيمِ: الَّذِي يَرْحَمُ المُؤمِنِينَ.",
        "رَبِّ: الرَّبُّ: المُرَبِّي لِخَلْقِهِ بِنِعَمِهِ.\nالْعَالَمِينَ: كُلِّ مَنْ سِوَى اللهِ تَعَالَى.",
        "",
        "يَوْمِ الدِّينِ: يَوْمِ الجَزَاءِ وَالحِسَابِ.",
        "إِيَّاكَ نَعْبُدُ: لَا نَعْبُدُ إِلَّا أَنْتَ.\nوإِيَّاكَ نَسْتَعِينُ: لَا نَسْتَعِينُ فِي قَضَاءِ حَوَائِجِنَا إِلَّا بِكَ.",
        "الصِّرَاطَ المُستَقِيمَ: الطَّرِيقَ الَّذِي لَا عِوَجَ فِيهِ؛ وَهُوَ الإِسْلَامُ.",
        "غَيرِ المَغضُوبِ عَلَيهِمْ: الْيَهُودِ، وَمَنْ شَابَهَهُمْ فِي تَرْكِ الْعَمَلِ بِالْعِلْمِ.\nالضَّالِّينَ: النَّصَارَى، وَمَنْ شَابَهَهُمْ فِي الْعَمَلِ بِغَيْرِ عِلْمٍ.",
    )

    @Test
    fun `a verse's lines are the words explained and what they mean`() {
        // The words end at the first colon: what they mean may have colons of its own.
        assertEquals(
            listOf("رَبِّ" to "الرَّبُّ: المُرَبِّي لِخَلْقِهِ بِنِعَمِهِ.", "الْعَالَمِينَ" to "كُلِّ مَنْ سِوَى اللهِ تَعَالَى."),
            Glosses.entries(told[1]),
        )
        assertEquals(3, Glosses.entries(told[0]).size)
        assertEquals(emptyList(), Glosses.entries(""))
        // A line that is broken in two carries on.
        assertEquals(listOf("سِنَةٌ" to "نُعَاسٌ خفيف"), Glosses.entries("سِنَةٌ: نُعَاسٌ\nخفيف"))
    }

    @Test
    fun `each meaning stands under the word it explains`() {
        val placed = Glosses.place(fatihah, told)
        assertEquals(fatihah.map { it.size }, placed.map { it.size })
        // The first verse: a meaning for its first two words together, one for each of the others.
        assertEquals("أَيْ: أَبْتَدِئُ قِرَاءَتِي مُسْتَعِينًا بِاسْمِ اللهِ.", placed[0][0])
        assertEquals(Glosses.CARRIES_ON, placed[0][1])
        assertEquals("الَّذِي وَسِعَتْ رَحْمَتُهُ جَمِيعَ الْخَلْقِ.", placed[0][2])
        assertEquals("الَّذِي يَرْحَمُ المُؤمِنِينَ.", placed[0][3])
        // The second: the words every reader knows have nothing; a word is found whatever stands before it.
        assertEquals(listOf("", "", "الرَّبُّ: المُرَبِّي لِخَلْقِهِ بِنِعَمِهِ.", "كُلِّ مَنْ سِوَى اللهِ تَعَالَى."), placed[1])
        // A verse with nothing to explain has nothing.
        assertEquals(listOf("", ""), placed[2])
        assertEquals(listOf("", "يَوْمِ الجَزَاءِ وَالحِسَابِ.", Glosses.CARRIES_ON), placed[3])
        // The same word twice in a verse: each line takes its own, in order.
        assertEquals(listOf("لَا نَعْبُدُ إِلَّا أَنْتَ.", Glosses.CARRIES_ON, "لَا نَسْتَعِينُ فِي قَضَاءِ حَوَائِجِنَا إِلَّا بِكَ.", Glosses.CARRIES_ON), placed[4])
        // Three words of nine, written a little differently in the line (no sukun, another hamza): found by their letters.
        assertEquals(
            listOf("", "", "", "", "الْيَهُودِ، وَمَنْ شَابَهَهُمْ فِي تَرْكِ الْعَمَلِ بِالْعِلْمِ.", Glosses.CARRIES_ON, Glosses.CARRIES_ON, "", "النَّصَارَى، وَمَنْ شَابَهَهُمْ فِي الْعَمَلِ بِغَيْرِ عِلْمٍ."),
            placed[6],
        )
    }

    @Test
    fun `a line that stands a verse off is found in the verse beside it, and one found nowhere is kept whole`() {
        // Surah 103 as the source has it: the meaning of the first verse's word stands under the second verse.
        val asr = listOf("وَالْعَصْرِ", "إِنَّ الْإِنْسَانَ لَفِي خُسْرٍ").map { it.split(' ') }
        val placed = Glosses.place(asr, listOf("", "وَالْعَصْرِ: أُقْسِمُ بِالدَّهْرِ.\nخُسْرٍ: هَلَكَةٍ، وَنُقْصَانٍ.\nكلمة أخرى: معناها."))
        assertEquals(listOf("أُقْسِمُ بِالدَّهْرِ."), placed[0])
        // What isn't in the verse or beside it is not dropped: it stands whole under the verse's last word, after that word's own.
        assertEquals(listOf("", "", "", "هَلَكَةٍ، وَنُقْصَانٍ.\nكلمة أخرى: معناها."), placed[1])
        // A word attached to another in the verse (a ف before it) is still found; a word that is only alike is not.
        val one = Glosses.place(listOf(listOf("فَسَوَّاهُنَّ", "سَبْعَ")), listOf("سَوَّاهُنَّ: أَتَمَّ خَلْقَهُنَّ.\nسَمْعَ: غير موجودة."))
        assertEquals("أَتَمَّ خَلْقَهُنَّ.", one[0][0])
        assertEquals("سَمْعَ: غير موجودة.", one[0][1])
    }

    @Test
    fun `a word is matched by its letters alone`() {
        assertEquals("الرحمن", Glosses.bare("الرَّحْمَٰنِ"))
        assertEquals("الرحمن", Glosses.bare("ٱلرَّحْمَـٰنِ"))
        assertEquals("الم", Glosses.bare("آلم"))
        assertEquals("بالعروهالوثقي", Glosses.bare("بِالْعُرْوَةِ الْوُثْقَىَ"))
    }
}

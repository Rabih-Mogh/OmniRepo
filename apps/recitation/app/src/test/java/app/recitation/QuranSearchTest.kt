package app.recitation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recitation.data.QuranStore
import app.recitation.data.VerseRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Searching the verses kept on the phone, in Arabic and in the translation. */
@RunWith(AndroidJUnit4::class)
class QuranSearchTest {

    /** Three verses of Al-Fatihah as the app keeps them: each word in its four spellings, with its meaning. */
    private val kept = """
        [{"w":[["ٱلْحَمْدُ","All praises","ٱلۡحَمۡدُ","اَلۡحَمۡدُ","الْحَمْدُ"],["لِلَّهِ","(be) to Allah","لِلَّهِ","لِلّٰهِ","لِلَّهِ"],["رَبِّ","the Lord","رَبِّ","رَبِّ","رَبِّ"],["ٱلْعَـٰلَمِينَ","of the universe","ٱلۡعَٰلَمِينَ","الۡعٰلَمِيۡنَ","الْعَالَمِينَ"]],"t":"[All] praise is [due] to Allah, Lord of the worlds -"},
         {"w":[["ٱلرَّحْمَـٰنِ","The Most Gracious","ٱلرَّحۡمَٰنِ","الرَّحۡمٰنِ","الرَّحْمَٰنِ"],["ٱلرَّحِيمِ","the Most Merciful","ٱلرَّحِيمِ","الرَّحِيۡمِ","الرَّحِيمِ"]],"t":"The Entirely Merciful, the Especially Merciful,"},
         {"w":[["مَـٰلِكِ","(The) Master","مَٰلِكِ","مٰلِكِ","مَالِكِ"],["يَوْمِ","(of the) Day","يَوۡمِ","يَوۡمِ","يَوْمِ"],["ٱلدِّينِ","(of) [the] Judgment","ٱلدِّينِ","الدِّيۡنِ","الدِّينِ"]],"t":"Sovereign of the Day of Recompense."}]
    """.trimIndent()

    private fun search(query: String): List<VerseRef> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, "quran").apply { mkdirs() }.resolve("113.v2.json").writeText(kept)
        return runBlocking { QuranStore(context).search(query) }.map { it.at }
    }

    @Test
    fun wordsAreFoundInTheTranslation() {
        assertEquals(listOf(VerseRef(113, 1)), search("lord of the worlds"))
        assertEquals(listOf(VerseRef(113, 2)), search("MERCIFUL"))
    }

    @Test
    fun arabicIsFoundHoweverItIsSpeltOrVowelled() {
        // Typed plainly, as on a keyboard, though the mushaf writes it without the alif.
        assertEquals(listOf(VerseRef(113, 1)), search("العالمين"))
        assertEquals(listOf(VerseRef(113, 1)), search("رب العالمين"))
        assertEquals(listOf(VerseRef(113, 3)), search("مَالِكِ يَوْمِ"))
    }

    @Test
    fun tooLittleToSearchForFindsNothing() {
        assertEquals(emptyList<VerseRef>(), search("of"))
    }
}

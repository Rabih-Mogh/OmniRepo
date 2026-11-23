package app.recitation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recitation.data.QuranStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/**
 * Fetching a surah's words, from a small server on this machine standing in for Quran.com.
 * Al-Kawthar is asked for: three verses.
 */
@RunWith(AndroidJUnit4::class)
class QuranFetchTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var status = 200
    private var answer = ""
    private var asked = 0
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked++
                return MockResponse().setResponseCode(status).setBody(answer)
            }
        }
        start()
    }
    private val store get() = QuranStore(context, server.url("/").toString().trimEnd('/'))
    private val kept get() = File(context.filesDir, "quran/108.v2.json")

    @After
    fun stop() = server.shutdown()

    /** Quran.com's answer with the first [verses] verses of the surah, each of one word and the sign that ends a verse. */
    private fun verses(verses: Int) = List(verses) { v ->
        """{"words":[{"char_type_name":"word","text_uthmani":"كلمة$v","translation":{"text":"word $v"}},{"char_type_name":"end","text_uthmani":"۝"}],"translations":[{"text":"Verse $v<sup>1</sup>"}]}"""
    }.joinToString(",", """{"verses":[""", """],"pagination":{"next_page":null}}""")

    @Test
    fun aWholeSurahIsKeptAndNotAskedForAgain() {
        answer = verses(3)
        val fetched = runBlocking { store.surah(108) }
        assertEquals(listOf("كلمة0", "كلمة1", "كلمة2"), fetched.map { it.words.single().arabic })
        assertEquals("Verse 0", fetched[0].translation)
        runBlocking { store.surah(108) }
        assertEquals(1, asked)
    }

    @Test
    fun partOfASurahIsNotKept() {
        answer = verses(2)
        assertThrows(IOException::class.java) { runBlocking { store.surah(108) } }
        assertFalse(kept.exists())
        // The next asking gets all of it.
        answer = verses(3)
        assertEquals(3, runBlocking { store.surah(108) }.size)
    }

    @Test
    fun aServerInTroubleOrAPageThatIsNotTheAnswerCanBeTriedAgain() {
        status = 503
        assertThrows(IOException::class.java) { runBlocking { store.surah(108) } }
        status = 200
        answer = "<html>Sign in to this Wi-Fi</html>"
        assertThrows(IOException::class.java) { runBlocking { store.surah(108) } }
        assertFalse(kept.exists())
    }

    @Test
    fun wordsKeptThatCannotBeReadAreFetchedAgain() {
        kept.parentFile!!.mkdirs()
        kept.writeText("""[{"w":[["كلمة","wo""") // cut off half way
        answer = verses(3)
        assertEquals(3, runBlocking { store.surah(108) }.size)
        assertTrue(kept.readText().endsWith("]"))
    }
}

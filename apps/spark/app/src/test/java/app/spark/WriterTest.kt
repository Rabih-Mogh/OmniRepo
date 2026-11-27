package app.spark

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.spark.core.Kind
import app.spark.core.Watch
import app.spark.data.Gemini
import app.spark.data.Library
import app.spark.data.News
import app.spark.data.Store
import app.spark.data.Writer
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.LocalDate

/**
 * A pack written on the phone, with a server on this machine standing in for Google and
 * for the news feeds. What the real service answers is for a phone with a real key to say.
 */
@RunWith(AndroidJUnit4::class)
class WriterTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val day = "2026-11-26"

    // The good pack the other tests read, cut into the two answers a model is asked for.
    private val pack = JSONObject(javaClass.getResource("/pack.json")!!.readText())
    private fun reel(id: String) = pack.getJSONArray("reels").let { all ->
        // A copy, so that a test can spoil one without spoiling the pack.
        JSONObject((0 until all.length()).map(all::getJSONObject).single { it.getString("id") == "$day-$id" }.toString())
    }
    private fun reels(vararg reels: JSONObject) = JSONObject().put("reels", JSONArray(reels.toList()))
    private val lessons get() = JSONObject().put("reels", JSONArray(listOf(reel("birthday")))).put("lessons", pack.getJSONArray("lessons"))

    // What the server does: the models it lists, and what it answers to each kind of request.
    private var models = listOf("gemini-3.8-flash", "gemini-4-hydrogen")
    private var understandsNewWay = true
    private var refuses: Pair<Int, String>? = null
    private var gone = setOf<String>()
    private var reelsAnswer: (again: Boolean) -> String = { reels(reel("chip"), reel("doors")).toString() }
    private val asked = ArrayList<RecordedRequest>()
    private val bodies = ArrayList<String>()

    private fun error(status: Int, code: String, message: String) = MockResponse().setResponseCode(status).setBody("""{"error": {"code": "$code", "message": "$message"}}""")

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path == "/feed.xml") {
                    return MockResponse().setBody(
                        """<rss><channel><item><title>A new chip</title><description>It does one thing well.</description><link>https://example.com/chip</link></item></channel></rss>""",
                    )
                }
                asked += request
                val body = request.body.readUtf8()
                bodies += body
                refuses?.let { (status, code) -> return error(status, code, "Refused for the test.") }
                if (path.startsWith("/v1beta/models?")) {
                    return MockResponse().setBody(JSONObject().put("models", JSONArray(models.map { JSONObject().put("name", "models/$it") })).toString())
                }
                val json = JSONObject(body)
                val new = path == "/v1beta/interactions"
                if (new && !understandsNewWay) return error(400, "invalid_request", "Unknown field.")
                val model = if (new) json.getString("model") else path.substringAfter("/models/").substringBefore(":")
                if (model in gone) return error(404, "not_found", "No such model.")
                val text = if (new) json.getString("input") else json.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
                val answer = if ("Write today's reels" in text) reelsAnswer("# What is wrong with it" in text) else lessons.toString()
                // Wrapped the way a model likes to wrap it.
                val wrapped = "```json\n$answer\n```"
                return MockResponse().setBody(
                    if (new) {
                        """{"status": "completed", "steps": [{"type": "thought", "content": [{"type": "text", "text": "Thinking."}]}, {"type": "model_output", "content": [{"type": "text", "text": ${JSONObject.quote(wrapped)}}]}]}"""
                    } else {
                        """{"candidates": [{"content": {"parts": [{"text": "Thinking.", "thought": true}, {"text": ${JSONObject.quote(wrapped)}}]}, "finishReason": "STOP"}]}"""
                    },
                )
            }
        }
        server.start()
        Gemini.base = server.url("/v1beta/").toString()
        News.feeds = listOf(News.Feed("Example News", server.url("/feed.xml").toString()))
        Library.base = null
        Library.load(context)
        Store.today = { LocalDate.parse(day).toEpochDay() }
        Store.load(context)
        Writer.today = { LocalDate.parse(day) }
        Writer.load(context)
    }

    @After
    fun stop() = server.shutdown()

    /** Gives a key and waits until Google has been asked about it. */
    private fun key(key: String) {
        Writer.key(context, key)
        val until = System.currentTimeMillis() + 10_000
        while (Writer.state.value in listOf(Writer.State.Idle, Writer.State.Checking) && key.isNotEmpty() && System.currentTimeMillis() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun write(quiet: Boolean = false, now: Long = 0) = runBlocking { Writer.write(context, quiet, now) }
    private val shelf get() = Library.shelf.value
    private val written get() = bodies.filter { "Write today's" in it }

    @Test
    fun `a key is kept out of backups, tried on Google, and can be taken back`() {
        assertNull(Writer.key.value)
        key(" my-secret\n")
        assertEquals(Writer.State.Works, Writer.state.value)
        assertEquals("my-secret", Writer.key.value)
        assertEquals("my-secret", File(context.noBackupFilesDir, "gemini.key").readText())
        // It goes in a header, never in an address, and the feed being full, nothing is written yet.
        assertEquals("my-secret", asked.single().getHeader("x-goog-api-key"))
        assertFalse("my-secret" in asked.single().path.orEmpty())
        assertEquals(40, shelf.reels.size)

        Writer.load(context)
        assertEquals("my-secret", Writer.key.value)
        key("")
        assertNull(Writer.key.value)
        assertFalse(File(context.noBackupFilesDir, "gemini.key").exists())
    }

    @Test
    fun `a key Google turns down is said to be wrong`() {
        refuses = 401 to "authentication"
        key("wrong")
        assertEquals(Writer.State.Failed(Writer.Why.KEY, "Refused for the test."), Writer.state.value)
    }

    @Test
    fun `the pack is asked for in two answers, checked, and put on the shelf`() {
        Store.wish("game theory")
        key("k")
        write()
        assertEquals(Writer.State.Done(reels = 3, lessons = 1), Writer.state.value)
        assertTrue(Writer.written(context))
        assertEquals(43, shelf.reels.size)
        assertEquals(9, shelf.lessons.size)
        assertEquals(Kind.NEWS, shelf.reels.getValue("$day-chip").kind)
        assertEquals("$day-pairs", shelf.reels.getValue("$day-birthday").lesson)

        // Each request carried the rules, examples from the starter pack, the reader, and the day's story.
        assertEquals(2, written.size)
        val (reels, lessons) = written.map { JSONObject(it) }
        assertEquals("gemini-3.8-flash", reels.getString("model"))
        assertFalse(reels.getBoolean("store"))
        val system = reels.getString("system_instruction")
        assertTrue("# The rules" in system && "one screen" in system)
        assertTrue("# Examples" in system && "2026-11-25-" in system)
        assertFalse("A scheduled run" in system)
        val ask = reels.getString("input")
        assertTrue("- game theory" in ask)
        assertTrue("publisher: Example News | title: A new chip | summary: It does one thing well. | url: https://example.com/chip" in ask)
        // The lessons are asked for knowing the hooks just written, so they aren't written twice.
        assertTrue("- A new chip does one thing well." in lessons.getString("input"))
        assertTrue("\"mistakes\"" in lessons.getString("system_instruction"))

        // Kept for the next time the app opens, and not written twice in a day.
        Library.load(context)
        assertEquals(43, shelf.reels.size)
        write()
        assertEquals(2, written.size)
    }

    @Test
    fun `an answer that breaks a rule is sent back once with what was wrong`() {
        val long = reel("doors").put("hook", "x".repeat(91))
        reelsAnswer = { again -> if (again) reels(reel("chip"), reel("doors")).toString() else reels(reel("chip"), long).toString() }
        key("k")
        write()
        assertEquals(Writer.State.Done(reels = 3, lessons = 1), Writer.state.value)
        assertEquals(3, written.size)
        val again = JSONObject(written[1]).getString("input")
        assertTrue("# Your earlier answer" in again)
        assertTrue("- $day-doors: \"hook\" is 91 characters; the most is 90" in again)
    }

    @Test
    fun `what still breaks a rule is left out, and so is news with a link nobody gave`() {
        val invented = reel("chip").put("id", "$day-rumour").put("source", JSONObject().put("title", "Example News").put("url", "https://example.com/made-up"))
        val sourced = reel("doors").put("source", JSONObject().put("title", "Somewhere").put("url", "https://example.com/somewhere"))
        reelsAnswer = { reels(reel("chip"), invented, sourced, reel("doors").put("id", "$day-long").put("hook", "x".repeat(91))).toString() }
        key("k")
        write()
        // Asked again, it answered the same: the good ones are kept, and a puzzle carries no source.
        assertEquals(3, written.size)
        assertTrue("- $day-rumour: a news reel's \"source\" must be one of the stories given" in JSONObject(written[1]).getString("input"))
        assertEquals(Writer.State.Done(reels = 3, lessons = 1), Writer.state.value)
        assertFalse("$day-rumour" in shelf.reels)
        assertFalse("$day-long" in shelf.reels)
        assertNull(shelf.reels.getValue("$day-doors").source)
    }

    @Test
    fun `a model that is gone is passed over, and the older way of asking is used when the new one isn't understood`() {
        gone = setOf("gemini-3.8-flash")
        understandsNewWay = false
        key("k")
        write()
        assertEquals(Writer.State.Done(reels = 3, lessons = 1), Writer.state.value)
        val paths = asked.map { it.path.orEmpty() }
        assertTrue("/v1beta/models/gemini-4-hydrogen:generateContent" in paths)
        assertTrue(asked.all { it.getHeader("x-goog-api-key") == "k" && "k=" !in it.path.orEmpty() && "key=" !in it.path.orEmpty() })
        // Once a model has answered, the rest of the pack is asked of it.
        assertEquals(1, paths.count { "gemini-3.8-flash" in it })
    }

    @Test
    fun `an allowance used up and a phone offline are told apart`() {
        key("k")
        refuses = 429 to "quota_exceeded"
        write()
        assertEquals(Writer.State.Failed(Writer.Why.QUOTA, "Refused for the test."), Writer.state.value)
        assertFalse(Writer.written(context))

        refuses = null
        server.shutdown()
        write()
        assertEquals(Writer.State.Failed(Writer.Why.OFFLINE), Writer.state.value)
        assertEquals(40, shelf.reels.size)
    }

    @Test
    fun `left to itself the app asks only when the feed is running out or has gone old`() {
        key("k")
        assertFalse(Writer.needed())
        write(quiet = true, now = 10_000_000)
        assertEquals(0, written.size)

        // A week and a day after its newest pack, the feed is old.
        Writer.today = { LocalDate.parse("2026-12-03") }
        assertTrue(Writer.needed())
        Writer.today = { LocalDate.parse("2026-12-02") }
        assertFalse(Writer.needed())

        // Fewer reels left than the day's goal asks for: time to write.
        Writer.today = { LocalDate.parse(day) }
        for (reel in shelf.reels.values.take(31)) Store.watched(reel, Watch(1.0), lessonsLeft = true)
        assertTrue(Writer.needed())
        write(quiet = true, now = 10_000_000)
        assertEquals(Writer.State.Done(reels = 3, lessons = 1), Writer.state.value)
    }

    @Test
    fun `after a try that failed, the app waits an hour before trying by itself again`() {
        key("k")
        for (reel in shelf.reels.values.take(31)) Store.watched(reel, Watch(1.0), lessonsLeft = true)
        refuses = 429 to "quota_exceeded"
        write(quiet = true, now = 10_000_000)
        val after = asked.size
        refuses = null
        write(quiet = true, now = 10_000_000 + 59 * 60 * 1000)
        assertEquals(after, asked.size)
        write(quiet = true, now = 10_000_000 + 61 * 60 * 1000)
        assertEquals(Writer.State.Done(reels = 3, lessons = 1), Writer.state.value)
    }

    @Test
    fun `an answer is read in either shape, and what the model thought is not what it wrote`() {
        assertEquals("Hello.", Gemini.written("""{"steps": [{"type": "thought", "content": [{"type": "text", "text": "Hm."}]}, {"type": "model_output", "content": [{"type": "text", "text": "Hel"}, {"type": "text", "text": "lo."}]}]}"""))
        assertEquals("Hello.", Gemini.written("""{"candidates": [{"content": {"parts": [{"text": "Hm.", "thought": true}, {"text": "Hello."}]}}]}"""))
        assertNull(Gemini.written("""{"status": "failed"}"""))
        assertNull(Gemini.written("<html>"))
    }
}

package app.spark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.spark.core.Packs
import app.spark.data.Library
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The library: the starter pack inside the app, and packs fetched from a server on this
 * machine standing in for the repo's content branch.
 */
@RunWith(AndroidJUnit4::class)
class LibraryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()

    // What the server has: the names in its index, and each pack's text.
    private var index = listOf<String>()
    private val packs = HashMap<String, String>()
    private var up = true

    private fun pack(day: String, reels: Int) = """{"format": 1, "day": "$day", "lessons": [], "reels": [""" +
        (1..reels).joinToString(",") { """{"id": "$day-r$it", "topic": "maths", "tags": ["numbers"], "kind": "idea", "hook": "Hook $it of $day", "beats": ["A beat."], "takeaway": "Kept."}""" } +
        "]}"

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    !up -> MockResponse().setResponseCode(503)
                    path == "/index.json" -> MockResponse().setBody("""{"format": 1, "packs": [${index.joinToString(",") { "\"$it\"" }}]}""")
                    else -> packs[path.removePrefix("/packs/").removeSuffix(".json")]?.let { MockResponse().setBody(it) } ?: MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        Library.base = server.url("/").toString()
        Library.load(context)
    }

    @After
    fun stop() = server.shutdown()

    private fun refresh(quiet: Boolean = false, now: Long = 0) = runBlocking { Library.refresh(context, quiet, now) }
    private val reels get() = Library.shelf.value.reels

    @Test
    fun `the starter pack inside the app is a good pack, and fills the shelf`() {
        val reading = Packs.read(context.assets.open("starter.json").bufferedReader().use { it.readText() })
        assertEquals(emptyList<String>(), reading.problems)
        assertEquals(40, reading.pack!!.reels.size)
        assertEquals(8, reading.pack!!.lessons.size)

        val shelf = Library.shelf.value
        assertEquals(40, shelf.reels.size)
        assertEquals(8, shelf.lessons.size)
        // Every lesson can be reached from a reel.
        assertEquals(shelf.lessons.keys, shelf.reels.values.mapNotNull { it.lesson }.toSet())
    }

    @Test
    fun `the packs the index names are fetched once and kept`() {
        index = listOf("2026-11-26", "2026-11-27")
        packs["2026-11-26"] = pack("2026-11-26", 3)
        packs["2026-11-27"] = pack("2026-11-27", 2)
        refresh()
        assertEquals(Library.State.Found(5), Library.state.value)
        assertEquals(45, reels.size)
        assertEquals(3, server.requestCount)

        // Again: only the index is asked for, and nothing is new.
        refresh()
        assertEquals(Library.State.Found(0), Library.state.value)
        assertEquals(4, server.requestCount)

        // Opened again with no server, the packs are still there.
        up = false
        Library.load(context)
        assertEquals(45, reels.size)
    }

    @Test
    fun `a pack the index drops is deleted`() {
        index = listOf("2026-11-26", "2026-11-27")
        packs["2026-11-26"] = pack("2026-11-26", 3)
        packs["2026-11-27"] = pack("2026-11-27", 2)
        refresh()
        index = listOf("2026-11-27")
        refresh()
        assertEquals(42, reels.size)
        assertFalse("2026-11-26-r1" in reels)
        assertTrue("2026-11-27-r1" in reels)
    }

    @Test
    fun `a pack that can't be read, or isn't there, is passed over`() {
        index = listOf("2026-11-26", "2026-11-27", "2026-11-28", "../../etc")
        packs["2026-11-26"] = "<html>not a pack</html>"
        packs["2026-11-27"] = pack("2026-11-27", 2)
        refresh()
        // The look wasn't whole, but the pack that did come is on the shelf.
        assertEquals(Library.State.Failed, Library.state.value)
        assertEquals(42, reels.size)
        packs["2026-11-28"] = pack("2026-11-28", 1)
        refresh()
        assertEquals(43, reels.size)
    }

    @Test
    fun `with no server the shelf stays as it was`() {
        up = false
        refresh()
        assertEquals(Library.State.Failed, Library.state.value)
        assertEquals(40, reels.size)
    }

    @Test
    fun `a quiet look happens at most once an hour and reports nothing`() {
        index = listOf("2026-11-26")
        packs["2026-11-26"] = pack("2026-11-26", 3)
        refresh(quiet = true, now = 10_000_000)
        assertEquals(43, reels.size)
        assertEquals(Library.State.Idle, Library.state.value)
        val asked = server.requestCount

        refresh(quiet = true, now = 10_000_000 + 59 * 60_000)
        assertEquals(asked, server.requestCount)
        refresh(quiet = true, now = 10_000_000 + 61 * 60_000)
        assertEquals(asked + 1, server.requestCount)
    }
}

package app.safeguard

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.safeguard.update.AppUpdate
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * The update's download, against a server on this machine standing in for GitHub: a whole
 * file, one carried on from where it stopped, one that isn't what was published, and the
 * tidying of downloads nobody needs any more.
 */
@RunWith(AndroidJUnit4::class)
class UpdateDownloadTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val apk = ByteArray(200_000) { (it * 31 % 251).toByte() }
    private val release = AppUpdate.Release(42, "1.42", "safeguard-release.apk", apk.size.toLong(), sha256(apk))

    // What the server has, and whether it sends just the rest of a file when asked to.
    private var published = apk
    private var resumes = true

    private val dir get() = File(folder.root, "updates")
    private val part get() = File(dir, "42-${release.sha256}.part")
    private val url get() = server.url("/apps/safeguard/apk/safeguard-release.apk").toString()

    @Before
    fun serve() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val from = request.getHeader("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toInt()
                return when {
                    from == null || !resumes -> MockResponse().setBody(Buffer().write(published))
                    from >= published.size -> MockResponse().setResponseCode(416)
                    else -> MockResponse().setResponseCode(206).setBody(Buffer().write(published, from, published.size - from))
                }
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun download(onProgress: (Float) -> Unit = {}): File = runBlocking { AppUpdate.download(url, release, dir, onProgress) }

    private fun leftBehind(bytes: Int) {
        dir.mkdirs()
        part.writeBytes(apk.copyOf(bytes))
    }

    @Test
    fun `a download lands whole, named after its build`() {
        var progress = 0f
        val file = download { progress = it }
        assertEquals("42-${release.sha256}.apk", file.name)
        assertArrayEquals(apk, file.readBytes())
        assertEquals(1f, progress)
        assertEquals(listOf(file.name), dir.list()!!.toList())
        assertNull(server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `a download that stopped halfway carries on from the bytes already there`() {
        leftBehind(80_000)
        val file = download()
        assertEquals("bytes=80000-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(apk, file.readBytes())
    }

    @Test
    fun `a server that sends the whole file again is taken from the start`() {
        resumes = false
        leftBehind(80_000)
        assertArrayEquals(apk, download().readBytes())
    }

    @Test
    fun `a connection that drops leaves what arrived for the next try`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setBody(Buffer().write(apk)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        }
        assertThrows(IOException::class.java) { download() }
        val arrived = part.length()
        assertTrue("some of it arrived: $arrived", arrived in 1 until apk.size)

        serve(arrived)
        assertArrayEquals(apk, download().readBytes())
    }

    // The server from serve(), which also checks that only the rest was asked for.
    private fun serve(expectedFrom: Long) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertEquals("bytes=$expectedFrom-", request.getHeader("Range"))
                return MockResponse().setResponseCode(206).setBody(Buffer().write(apk, expectedFrom.toInt(), apk.size - expectedFrom.toInt()))
            }
        }
    }

    @Test
    fun `a finished download isn't fetched again`() {
        val first = download()
        val second = download()
        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a file that isn't the published one is refused and not kept`() {
        published = ByteArray(apk.size) { 7 }
        assertThrows(AppUpdate.Mismatch::class.java) { download() }
        assertTrue(dir.list()!!.isEmpty())
    }

    @Test
    fun `a published file shorter than what is here is a different build`() {
        leftBehind(80_000)
        published = apk.copyOf(50_000)
        assertThrows(AppUpdate.Mismatch::class.java) { download() }
        assertTrue(dir.list()!!.isEmpty())
    }

    @Test
    fun `leftovers longer than the published file are thrown away`() {
        dir.mkdirs()
        part.writeBytes(ByteArray(apk.size + 10))
        assertArrayEquals(apk, download().readBytes())
        assertNull(server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `the server's refusal is reported with its code`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(404)
        }
        val error = assertThrows(AppUpdate.HttpError::class.java) { download() }
        assertEquals(404, error.code)
    }

    @Test
    fun `tidying keeps the build being fetched and deletes the rest`() {
        dir.mkdirs()
        val names = listOf("40-aaaa.apk", "42-${release.sha256}.part", "43-bbbb.part", "junk.tmp")
        names.forEach { File(dir, it).writeText("x") }

        AppUpdate.tidy(dir, installed = 41, keep = release)
        assertEquals(listOf("42-${release.sha256}.part"), dir.list()!!.toList())
    }

    @Test
    fun `tidying deletes downloads of builds the app already has`() {
        dir.mkdirs()
        listOf("41-aaaa.apk", "42-${release.sha256}.apk", "43-bbbb.part").forEach { File(dir, it).writeText("x") }

        AppUpdate.tidy(dir, installed = 42)
        assertEquals(listOf("43-bbbb.part"), dir.list()!!.toList())
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

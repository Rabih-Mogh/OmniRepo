package app.recitation.yt

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Downloads from a small server on this machine that answers as YouTube's do: a piece at a time, saying how big the whole is. */
class DownloadTest {
    private var sound = ByteArray(0)
    private var refusing = false
    private val askedFrom = ArrayList<Int>() // the byte each request began at
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/sound") { exchange ->
            val from = exchange.requestHeaders.getFirst("Range").removePrefix("bytes=").substringBefore('-').toInt()
            askedFrom += from
            when {
                refusing -> exchange.sendResponseHeaders(429, -1)
                from >= sound.size -> {
                    exchange.responseHeaders.add("Content-Range", "bytes */${sound.size}")
                    exchange.sendResponseHeaders(416, -1)
                }
                else -> {
                    exchange.responseHeaders.add("Content-Range", "bytes $from-${sound.size - 1}/${sound.size}")
                    exchange.sendResponseHeaders(206, (sound.size - from).toLong())
                    exchange.responseBody.use { it.write(sound, from, sound.size - from) }
                }
            }
            exchange.close()
        }
        start()
    }
    private val audio get() = YouTube.Audio("http://127.0.0.1:${server.address.port}/sound", "m4a", 128)
    private val dir = File.createTempFile("download", "").apply { delete(); mkdirs() }
    private val part = File(dir, "a.m4a.part")
    private val note = File(dir, "a.m4a.size.part")

    @AfterTest
    fun stop() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun bytes(n: Int, seed: Int) = ByteArray(n) { (it * 31 + seed).toByte() }

    @Test
    fun `a download begun afresh notes how big the sound is`() {
        sound = bytes(5_000, seed = 1)

        YouTube.download(audio, part, note)
        assertContentEquals(sound, part.readBytes())
        assertEquals("5000", note.readText())
    }

    @Test
    fun `a download that was cut short carries on from where it stopped`() {
        sound = bytes(5_000, seed = 1)
        part.writeBytes(sound.copyOf(2_000))
        note.writeText("5000")

        YouTube.download(audio, part, note)
        assertContentEquals(sound, part.readBytes())
        assertEquals(listOf(2_000), askedFrom)
    }

    @Test
    fun `the start of one sound is never carried on with the rest of another`() {
        // Half of a sound was fetched; YouTube now offers one of another size for the same video.
        part.writeBytes(bytes(2_000, seed = 1))
        note.writeText("5000")
        sound = bytes(4_000, seed = 2)

        YouTube.download(audio, part, note)
        assertContentEquals(sound, part.readBytes())
        assertEquals("4000", note.readText())
        assertEquals(listOf(2_000, 0), askedFrom)
    }

    @Test
    fun `the start of a sound whose size was never noted is fetched again`() {
        part.writeBytes(bytes(2_000, seed = 1))
        sound = bytes(5_000, seed = 2)

        YouTube.download(audio, part, note)
        assertContentEquals(sound, part.readBytes())
    }

    @Test
    fun `a sound that is already all there is left as it is`() {
        sound = bytes(3_000, seed = 3)
        part.writeBytes(sound)
        note.writeText("3000")

        YouTube.download(audio, part, note)
        assertContentEquals(sound, part.readBytes())
    }

    @Test
    fun `being told to slow down is told apart from a bad connection, and what was fetched is kept`() {
        part.writeBytes(bytes(2_000, seed = 1))
        note.writeText("5000")
        refusing = true

        assertFailsWith<YouTube.Refused> { YouTube.download(audio, part, note) }
        assertEquals(2_000, part.length())
    }
}

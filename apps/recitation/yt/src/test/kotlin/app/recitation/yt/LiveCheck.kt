package app.recitation.yt

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Talks to the real YouTube, so it is skipped unless asked for:
 * `YT_LIVE=1 YT_URL=<playlist or video link> ./gradlew :yt:test`
 */
class LiveCheck {
    /** With no link given, takes the first playlist a search turns up. */
    private fun findPlaylist(query: String): String {
        YouTube.playlist("https://www.youtube.com/watch?v=jNQXAC9IVRw") // starts the library
        val service = org.schabi.newpipe.extractor.ServiceList.YouTube
        val found = org.schabi.newpipe.extractor.search.SearchInfo.getInfo(
            service, service.searchQHFactory.fromQuery(query, listOf("playlists"), ""),
        )
        return found.relatedItems.first().url
    }

    @Test
    fun `reads a playlist and downloads the start of its first video`() {
        assumeTrue(System.getenv("YT_LIVE") == "1")
        val url = System.getenv("YT_URL") ?: findPlaylist("القرآن الكريم كامل مشاري العفاسي")
        println("link: $url")
        val playlist = YouTube.playlist(url)
        println("playlist: ${playlist.title} by ${playlist.channel}, ${playlist.videos.size} videos")
        playlist.videos.take(8).forEach { println("  ${it.id}  ${it.seconds}s  ${it.title}") }
        assertTrue(playlist.videos.isNotEmpty())

        val video = playlist.videos.first()
        val audio = YouTube.audio(video.id)
        println("audio: .${audio.extension}, ${audio.bitrate} kbps")
        // YT_OUT=<path> keeps the whole file there; otherwise only the start is fetched.
        val whole = System.getenv("YT_OUT")
        val file = whole?.let { File("$it.${audio.extension}") } ?: File.createTempFile("recitation", "." + audio.extension)
        val started = System.currentTimeMillis()
        // About 3 MB is enough to see that pieces arrive and at what speed.
        runCatching { YouTube.download(audio, file, stillWanted = { whole != null || file.length() < 3_000_000 }) }
        val seconds = (System.currentTimeMillis() - started) / 1000.0
        println("downloaded ${file.length()} bytes in ${"%.1f".format(seconds)} s to $file")
        assertTrue(file.length() > 100_000)
    }
}

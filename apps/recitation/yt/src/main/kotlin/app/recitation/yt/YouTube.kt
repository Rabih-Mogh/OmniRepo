package app.recitation.yt

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService.LinkType
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Reads a YouTube playlist and fetches the sound of its videos, through NewPipe Extractor
 * (the library behind the NewPipe app). No account, no API key.
 */
object YouTube {

    data class Video(val id: String, val title: String, val seconds: Long, val channel: String)

    data class Playlist(val url: String, val title: String, val channel: String, val videos: List<Video>)

    /** Where a video's sound can be fetched from. The address stops working after a few hours. */
    data class Audio(val url: String, val extension: String, val bitrate: Int)

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val ready by lazy { NewPipe.init(Http) }

    /** The videos behind [url]: a playlist's, or the one video a plain video link points at. */
    fun playlist(url: String): Playlist {
        ready
        val service = ServiceList.YouTube
        return when (service.getLinkTypeByUrl(url)) {
            LinkType.PLAYLIST -> {
                val info = PlaylistInfo.getInfo(service, url)
                val items = info.relatedItems.toMutableList()
                var page = info.nextPage
                while (page != null && items.size < MAX_VIDEOS) {
                    val more = PlaylistInfo.getMoreItems(service, url, page)
                    items += more.items
                    page = more.nextPage
                }
                Playlist(info.url, info.name, info.uploaderName.orEmpty(), items.map(::video))
            }
            LinkType.STREAM -> {
                val info = StreamInfo.getInfo(service, url)
                Playlist(info.url, info.name, info.uploaderName.orEmpty(), listOf(Video(info.id, info.name, info.duration, info.uploaderName.orEmpty())))
            }
            else -> throw IllegalArgumentException("not a playlist or a video")
        }
    }

    private fun video(item: StreamInfoItem) = Video(
        id = ServiceList.YouTube.streamLHFactory.getId(item.url),
        title = item.name,
        seconds = item.duration,
        channel = item.uploaderName.orEmpty(),
    )

    /** The best plain-download sound of a video. AAC is preferred: every phone decodes it. */
    fun audio(videoId: String): Audio {
        ready
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val plain = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
        val best = plain.filter { extension(it) == "m4a" }.maxByOrNull { it.averageBitrate }
            ?: plain.maxByOrNull { it.averageBitrate }
            ?: throw IOException("this video has no sound that can be downloaded")
        return Audio(best.content, extension(best), best.averageBitrate)
    }

    private fun extension(stream: AudioStream): String = stream.format?.suffix ?: "audio"

    /**
     * Saves [audio] to [file], carrying on from the bytes already there. YouTube slows down
     * one long request, so the file is asked for a piece at a time.
     */
    fun download(audio: Audio, file: File, stillWanted: () -> Boolean = { true }, onProgress: (Float) -> Unit = {}) {
        var have = file.length()
        var total = -1L
        while (total < 0 || have < total) {
            if (!stillWanted()) throw InterruptedException()
            val request = okhttp3.Request.Builder().url(audio.url)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=$have-${have + PIECE - 1}")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 416 && have > 0) return // nothing after what's here: it was already whole
                if (response.code != 206) throw IOException("HTTP ${response.code}")
                // "bytes 0-999/5000": the last number is the whole file's size.
                total = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    ?: throw IOException("the server didn't say how big the file is")
                FileOutputStream(file, true).use { out ->
                    response.body!!.byteStream().use { have += it.copyTo(out, 1 shl 16) }
                }
            }
            onProgress((have.toFloat() / total).coerceIn(0f, 1f))
        }
    }

    // The first second or so of every request is slow, so big pieces come down faster than small ones.
    private const val PIECE = 8L shl 20
    private const val MAX_VIDEOS = 400

    private object Http : Downloader() {
        override fun execute(request: Request): Response {
            val body = request.dataToSend()?.toRequestBody()
                ?: ByteArray(0).toRequestBody().takeIf { request.httpMethod() == "POST" }
            val builder = okhttp3.Request.Builder()
                .method(request.httpMethod(), body)
                .url(request.url())
                .header("User-Agent", USER_AGENT)
            for ((name, values) in request.headers()) {
                builder.removeHeader(name)
                for (value in values) builder.addHeader(name, value)
            }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) throw ReCaptchaException("YouTube asked for a captcha", request.url())
                return Response(
                    response.code, response.message, response.headers.toMultimap(),
                    response.body?.string(), response.request.url.toString(),
                )
            }
        }
    }
}

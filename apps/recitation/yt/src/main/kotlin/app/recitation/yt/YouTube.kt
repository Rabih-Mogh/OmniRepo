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
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
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

    /**
     * YouTube won't answer this phone for now: it has been asked too much, or wants proof
     * that a person is asking. It passes by itself, and sooner the less it is asked meanwhile.
     */
    class Refused(cause: Throwable? = null) : IOException("YouTube is refusing for now", cause)

    private fun <T> asked(ask: () -> T): T = try {
        ask()
    } catch (e: ReCaptchaException) {
        throw Refused(e)
    } catch (e: SignInConfirmNotBotException) {
        throw Refused(e)
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val ready by lazy { NewPipe.init(Http) }

    /** The videos behind [url]: a playlist's, or the one video a plain video link points at. */
    fun playlist(url: String): Playlist = asked {
        ready
        val service = ServiceList.YouTube
        when (service.getLinkTypeByUrl(url)) {
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
    fun audio(videoId: String): Audio = asked {
        ready
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val plain = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
        val best = plain.filter { extension(it) == "m4a" }.maxByOrNull { it.averageBitrate }
            ?: plain.maxByOrNull { it.averageBitrate }
            ?: throw IOException("this video has no sound that can be downloaded")
        Audio(best.content, extension(best), best.averageBitrate)
    }

    private fun extension(stream: AudioStream): String = stream.format?.suffix ?: "audio"

    /**
     * Saves [audio] to [file], carrying on from the bytes already there. YouTube slows down
     * one long request, so the file is asked for a piece at a time.
     *
     * YouTube can offer a different sound for the same video from one asking to the next
     * (another quality, say), and the start of one with the rest of another is noise. So how
     * big the sound is, is kept in [sizeNote] when a download begins, and the bytes already
     * there are carried on from only if what is on offer now is that big too. Otherwise the
     * download starts over.
     */
    fun download(audio: Audio, file: File, sizeNote: File, stillWanted: () -> Boolean = { true }, onProgress: (Float) -> Unit = {}) {
        val begun = if (sizeNote.exists()) sizeNote.readText().trim().toLongOrNull() else null
        var have = file.length()
        var total = -1L
        while (total < 0 || have < total) {
            if (!stillWanted()) throw InterruptedException()
            val request = okhttp3.Request.Builder().url(audio.url)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=$have-${have + PIECE - 1}")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 429) throw Refused()
                if (response.code != 206 && response.code != 416) throw IOException("HTTP ${response.code}")
                // "bytes 0-999/5000", or "bytes */5000" when there is nothing that far in: the last number is the whole file's size.
                val size = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                if (total < 0 && have > 0 && (begun == null || size != begun)) {
                    // Not known to be more of the same sound: from the beginning, then.
                    file.delete()
                    have = 0
                    return@use
                }
                if (response.code == 416) {
                    if (have > 0) return // nothing after what's here: it was already whole
                    throw IOException("HTTP 416")
                }
                total = size ?: throw IOException("the server didn't say how big the file is")
                if (have == 0L) sizeNote.writeText(total.toString())
                FileOutputStream(file, true).use { out ->
                    response.body!!.byteStream().use { input ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            // Asked every 64 KB, so pausing or cancelling takes effect at once.
                            if (!stillWanted()) throw InterruptedException()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            have += n
                            onProgress((have.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
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

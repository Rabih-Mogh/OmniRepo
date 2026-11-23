package app.recitation.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import app.recitation.core.HeardScript
import app.recitation.core.Mel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * The model that listens to a learner reciting and writes down how each sound was said:
 * HisnLabs Tajweed v2 (Apache-2.0), a small one made for this, 23 MB. It is fetched once,
 * from the app's own repository, the first time a recitation is to be checked, and kept
 * on the phone. It runs on the same engine as the model that follows a reciter's recording.
 */
class TajweedStore(context: Context, private val source: String = SOURCE, private val bytes: Long = BYTES, private val checksum: String = SHA256) {

    private val dir = File(context.filesDir, "tajweed")
    val file = File(dir, NAME)

    val isReady: Boolean get() = file.length() == bytes

    /** Puts the model on the phone if it isn't there yet, reporting progress 0..1. */
    suspend fun obtain(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (isReady) return@withContext
        dir.mkdirs()
        val part = File(dir, "$NAME.part")
        val conn = URL(source).openConnection().apply {
            connectTimeout = 20_000
            readTimeout = 30_000
        }
        try {
            if (conn is HttpURLConnection && conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Couldn't download the tajweed model (HTTP ${conn.responseCode})")
            }
            conn.getInputStream().use { input ->
                FileOutputStream(part, false).use { out ->
                    val buf = ByteArray(1 shl 16)
                    var have = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        have += n
                        onProgress(have.toFloat() / bytes)
                    }
                }
            }
        } finally {
            (conn as? HttpURLConnection)?.disconnect()
        }
        if (sha256(part) != checksum) {
            part.delete()
            throw IOException("The tajweed model was damaged on the way. Try again.")
        }
        file.delete()
        if (!part.renameTo(file)) throw IOException("Couldn't save the tajweed model.")
        file.setReadOnly()
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val NAME = "tajweed-v2-int8.onnx"
        const val BYTES = 23_173_931L
        private const val SHA256 = "79cca1a2fc7ef81e325a3071a5f26cfec69203ef2c63177549065cdc1022c373"
        private const val SOURCE = "https://raw.githubusercontent.com/Rabih-Mogh/OmniRepo/main/apps/recitation/models/tajweed-v2/model-int8.onnx"
    }
}

/**
 * The tajweed model, opened. It is given a phrase or a verse, up to [LONGEST_SECONDS] of
 * sound, and answers with what it heard in the Quran phonetic script. The engine must
 * have been started (see [EngineStore.start]).
 */
class TajweedEar(model: File) : AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        model.path,
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        },
    )

    /** What was heard in [sound]: 16,000 samples a second, between -1 and 1. Sound past the longest it takes is left out. */
    fun hear(sound: FloatArray): HeardScript {
        // The clips it learned from begin and end with a little silence, so it is given the same.
        val lead = HeardScript.LEAD_SAMPLES
        val body = minOf(sound.size, LONGEST_SECONDS * Mel.SAMPLE_RATE)
        val padded = FloatArray(body + 2 * lead)
        System.arraycopy(sound, 0, padded, lead, body)
        var frames = Mel.frames(padded.size)
        val mel = Mel.of(padded)
        // An even number of its frames, as its first layers halve them.
        val used = minOf(frames / 2 * 2, MOST_FRAMES)
        val input = if (used == frames) mel else FloatArray(Mel.BANDS * used).also { for (b in 0 until Mel.BANDS) System.arraycopy(mel, b * frames, it, b * used, used) }
        frames = used
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, Mel.BANDS.toLong(), frames.toLong())).use { picture ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(HAFS)), longArrayOf(1)).use { reading ->
                session.run(mapOf("mel" to picture, "riwaya" to reading)).use { result ->
                    val scores = (result[0] as OnnxTensor).floatBuffer // [1, frames / 2, symbols + 1]
                    val all = FloatArray(scores.remaining()).also { scores.get(it) }
                    return HeardScript.read(all, all.size / (HeardScript.ALPHABET.length + 1))
                }
            }
        }
    }

    override fun close() = session.close()

    companion object {
        /** The longest stretch it takes at once: a verse or a phrase, not a page. */
        const val LONGEST_SECONDS = 29
        private const val MOST_FRAMES = 3000

        /** Which reading the recitation is in: this app's is Hafs from 'Asim. */
        private const val HAFS = 0L
    }
}

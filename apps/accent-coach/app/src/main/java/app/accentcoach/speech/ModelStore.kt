package app.accentcoach.speech

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * The phoneme recogniser is too big to ship inside the app (GitHub won't store a
 * 330 MB APK), so it's downloaded once, checked against a fixed checksum, and kept in
 * the app's private storage.
 */
class ModelStore(context: Context) {

    private val dir = File(context.filesDir, "model")
    val modelFile = File(dir, "phonemes_int8.onnx")
    private val partFile = File(dir, "phonemes_int8.onnx.part")

    val isReady: Boolean get() = modelFile.length() == MODEL_BYTES

    /**
     * Downloads the model, resuming a partial download, and reports progress 0..1.
     * Throws if the network fails or the file doesn't match the expected checksum.
     */
    suspend fun download(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        var have = partFile.length().takeIf { it < MODEL_BYTES } ?: 0L
        val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) have = 0L // server ignored the range: start over
            else if (code != HttpURLConnection.HTTP_PARTIAL) error("Download failed (HTTP $code)")
            conn.inputStream.use { input ->
                FileOutputStream(partFile, have > 0).use { out ->
                    val buf = ByteArray(1 shl 16)
                    var lastReported = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        have += n
                        val pct = (have * 1000 / MODEL_BYTES).toInt()
                        if (pct != lastReported) {
                            lastReported = pct
                            onProgress(pct / 1000f)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (sha256(partFile) != MODEL_SHA256) {
            partFile.delete()
            error("The downloaded model was damaged. Try again.")
        }
        if (!partFile.renameTo(modelFile)) error("Couldn't save the model.")
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
        /**
         * wav2vec2-lv-60-espeak-cv-ft (Facebook AI, Apache-2.0), 8-bit ONNX export by
         * onnx-community, pinned to one revision so the file can't change under us.
         */
        const val MODEL_URL = "https://huggingface.co/onnx-community/wav2vec2-lv-60-espeak-cv-ft-ONNX/resolve/" +
            "c69750f5043e5e1f8a71ab95dd3b98338c280c92/onnx/model_int8.onnx"
        const val MODEL_BYTES = 317_712_780L
        const val MODEL_SHA256 = "74174710e34035bbb7f611601d016c32fc575de7a6f53b1078107dc10a84e7ae"
    }
}

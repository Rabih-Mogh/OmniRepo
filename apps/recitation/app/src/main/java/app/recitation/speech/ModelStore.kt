package app.recitation.speech

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * The speech model that hears which sound is being recited. It is the same 318 MB file
 * Accent Coach uses, too big to ship inside the app. If Accent Coach is installed, the file
 * is copied from there; if it isn't, the file is downloaded once. Either way it is checked
 * against a fixed checksum and kept in the app's private storage.
 */
class ModelStore(private val context: Context) {

    private val dir = File(context.filesDir, "model")
    val modelFile = File(dir, "phonemes_int8.onnx")
    private val partFile = File(dir, "phonemes_int8.onnx.part")

    val isReady: Boolean get() = modelFile.length() == MODEL_BYTES

    /** Whether Accent Coach can lend its copy of the model, and if not, why. */
    enum class Lender {
        /** Accent Coach is installed, shares, and has the model. */
        READY,

        /** Accent Coach isn't on this phone. */
        NOT_INSTALLED,

        /** It is installed, but in a version from before it shared the model. */
        TOO_OLD,

        /** It shares, but hasn't (fully) downloaded the model itself. */
        NO_MODEL,

        /** It said no, or the copy broke off. */
        REFUSED,
    }

    /** Accent Coach is installed but couldn't hand its model over. [detail] is the system's own wording, if any. */
    class Unavailable(val reason: Lender, val detail: String?) : Exception(detail)

    /** Asks Accent Coach, without copying anything yet. */
    fun lender(): Pair<Lender, String?> {
        val packages = context.packageManager
        try {
            packages.getPackageInfo(ACCENT_COACH, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return Lender.NOT_INSTALLED to null
        }
        if (packages.resolveContentProvider(ACCENT_COACH_MODEL.authority!!, 0) == null) return Lender.TOO_OLD to null
        return try {
            context.contentResolver.openFileDescriptor(ACCENT_COACH_MODEL, "r")?.close()
            Lender.READY to null
        } catch (_: FileNotFoundException) {
            Lender.NO_MODEL to null
        } catch (e: Exception) {
            Lender.REFUSED to "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * Gets the model if it isn't here yet, reporting progress 0..1 and whether it is being
     * [copied] or downloaded. It is copied from Accent Coach when that app is installed.
     * If it is installed but can't lend the model, this throws [Unavailable] rather than
     * quietly downloading 318 MB, unless [download] says a download is fine.
     */
    suspend fun obtain(download: Boolean, onProgress: (copied: Boolean, progress: Float) -> Unit) = withContext(Dispatchers.IO) {
        if (isReady) return@withContext
        dir.mkdirs()
        val (lender, detail) = lender()
        when {
            lender == Lender.READY -> copyFromAccentCoach { onProgress(true, it) }
            lender == Lender.NOT_INSTALLED || download -> download { onProgress(false, it) }
            else -> throw Unavailable(lender, detail)
        }
        if (sha256(partFile) != MODEL_SHA256) {
            partFile.delete()
            error("The speech model was damaged on the way. Try again.")
        }
        if (!partFile.renameTo(modelFile)) error("Couldn't save the speech model.")
    }

    private suspend fun copyFromAccentCoach(onProgress: (Float) -> Unit) {
        try {
            val input = context.contentResolver.openInputStream(ACCENT_COACH_MODEL) ?: throw FileNotFoundException("nothing to read")
            input.use { FileOutputStream(partFile, false).use { out -> copy(it, out, 0, onProgress) } }
        } catch (e: java.io.IOException) {
            throw Unavailable(Lender.REFUSED, "${e.javaClass.simpleName}: ${e.message}")
        } catch (e: SecurityException) {
            throw Unavailable(Lender.REFUSED, "${e.javaClass.simpleName}: ${e.message}")
        }
        if (partFile.length() != MODEL_BYTES) throw Unavailable(Lender.REFUSED, "the copy stopped at ${partFile.length()} bytes")
    }

    /** Downloads the model, carrying on from a partial download. */
    private suspend fun download(onProgress: (Float) -> Unit) {
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
                FileOutputStream(partFile, have > 0).use { out -> copy(input, out, have, onProgress) }
            }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun copy(input: InputStream, out: FileOutputStream, already: Long, onProgress: (Float) -> Unit) {
        val buf = ByteArray(1 shl 16)
        var have = already
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
        const val ACCENT_COACH = "app.accentcoach"

        /** Where Accent Coach hands its copy of the model to apps signed with the same key. */
        val ACCENT_COACH_MODEL: Uri = Uri.parse("content://app.accentcoach.model/phonemes")

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

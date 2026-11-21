package app.recitation.speech

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * The speech model that hears which sound is being recited. It is the same 318 MB file
 * Accent Coach uses, too big to ship inside the app.
 *
 * - If Accent Coach is installed and has the model, Recitation reads Accent Coach's copy
 *   where it lies and keeps none of its own, so the phone holds the file once. A copy
 *   Recitation made or downloaded earlier is deleted as soon as Accent Coach's has been
 *   checked.
 * - If Accent Coach isn't installed, the file is downloaded once into the app's private
 *   storage.
 *
 * Either way the file is checked against a fixed checksum before it is used.
 */
class ModelStore(private val context: Context, private val size: Long = MODEL_BYTES, private val checksum: String = MODEL_SHA256) {

    private val dir = File(context.filesDir, "model")
    private val ownFile = File(dir, "phonemes_int8.onnx")
    private val partFile = File(dir, "phonemes_int8.onnx.part")

    /** Accent Coach's copy has been checked against the checksum by this store. */
    @Volatile private var borrowedIsGood = false

    /** Whether this app holds a whole copy of its own. */
    private val hasOwn: Boolean get() = ownFile.length() == size

    /** Whether there is no model on the phone to use yet: neither this app's own copy nor one Accent Coach can lend. */
    val needsDownload: Boolean get() = !hasOwn && !borrowedIsGood && lender().first != Lender.READY

    /** The model opened for the speech engine: [bytes] when it is Accent Coach's copy, read where it lies; [file] when it is this app's own. */
    class Opened(val bytes: ByteBuffer?, val file: File?)

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

        /** It said no, or the file it handed over isn't the model. */
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
     * Makes sure there is a model to use. Accent Coach's copy is used when it can lend one:
     * it is checked ([checking] is true while it is), never copied, and this app's own copy,
     * if it has one, is deleted. Otherwise this app's own copy is used, downloaded first if
     * need be. If Accent Coach is installed but can't lend the model, and there is no copy
     * here, this throws [Unavailable] rather than quietly downloading 318 MB, unless
     * [download] says a download is fine.
     */
    suspend fun obtain(download: Boolean, onProgress: (checking: Boolean, progress: Float) -> Unit) = withContext(Dispatchers.IO) {
        val (lender, detail) = lender()
        if (lender == Lender.READY) {
            val problem = checkBorrowed { onProgress(true, it) }
            if (problem == null) {
                dropOwn()
                return@withContext
            }
            if (!hasOwn && !download) throw Unavailable(Lender.REFUSED, problem)
        }
        if (hasOwn) return@withContext
        if (lender != Lender.READY && lender != Lender.NOT_INSTALLED && !download) throw Unavailable(lender, detail)
        dir.mkdirs()
        download { onProgress(false, it) }
        if (sha256(FileInputStream(partFile).use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, partFile.length()) }) { } != checksum) {
            partFile.delete()
            error("The speech model was damaged on the way. Try again.")
        }
        if (!partFile.renameTo(ownFile)) error("Couldn't save the speech model.")
    }

    /**
     * Opens the model for the speech engine, after [obtain]: Accent Coach's copy, mapped
     * into memory straight from its file, if that is the one in use; else this app's own.
     */
    fun open(): Opened = if (borrowedIsGood) Opened(borrowed(), null) else Opened(null, ownFile)

    /**
     * Frees the room this app's own copy takes, when Accent Coach's can be used instead:
     * for a phone where Recitation copied or downloaded the model before it learnt to read
     * Accent Coach's. Returns whether anything was deleted.
     */
    suspend fun tidy(): Boolean = withContext(Dispatchers.IO) {
        if (!ownFile.exists() && !partFile.exists()) return@withContext false
        if (lender().first != Lender.READY || checkBorrowed { } != null) return@withContext false
        dropOwn()
        true
    }

    private fun dropOwn() {
        ownFile.delete()
        partFile.delete()
    }

    /** Accent Coach's copy of the model, mapped into memory for reading. Nothing is copied. */
    private fun borrowed(): ByteBuffer {
        val descriptor = context.contentResolver.openFileDescriptor(ACCENT_COACH_MODEL, "r") ?: throw FileNotFoundException("nothing to read")
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.channel.size()) }
    }

    /**
     * Checks that what Accent Coach lends is the model, byte for byte. Null if it is;
     * otherwise what is wrong with it. Once it has passed, this store doesn't read
     * through it again.
     */
    private suspend fun checkBorrowed(onProgress: (Float) -> Unit): String? {
        if (borrowedIsGood) return null
        return try {
            val bytes = borrowed()
            when {
                bytes.remaining().toLong() != size -> "its model file is ${bytes.remaining()} bytes, not $size"
                sha256(bytes, onProgress) != checksum -> "its model file is damaged"
                else -> null.also { borrowedIsGood = true }
            }
        } catch (e: java.io.IOException) {
            "${e.javaClass.simpleName}: ${e.message}"
        } catch (e: SecurityException) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
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

    private suspend fun sha256(bytes: ByteBuffer, onProgress: (Float) -> Unit): String {
        val md = MessageDigest.getInstance("SHA-256")
        val view = bytes.duplicate()
        val total = view.remaining().coerceAtLeast(1)
        while (view.hasRemaining()) {
            coroutineContext.ensureActive()
            val piece = view.slice()
            piece.limit(minOf(piece.remaining(), 8 shl 20))
            val taken = piece.remaining()
            md.update(piece)
            view.position(view.position() + taken)
            onProgress(view.position().toFloat() / total)
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

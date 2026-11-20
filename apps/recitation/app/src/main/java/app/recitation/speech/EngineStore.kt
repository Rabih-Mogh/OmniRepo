package app.recitation.speech

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

/**
 * The speech engine: ONNX Runtime's native library, which runs the speech model. At 34 MB
 * it was nine tenths of the app's download and the same in every build, so it is no longer
 * shipped inside the app. It is put on the phone once instead:
 *
 * - copied out of Accent Coach's installed package, which carries the very same files,
 *   when that app is on the phone;
 * - otherwise downloaded from this repo, compressed (13 MB).
 *
 * Either way every file must match its fixed checksum before it is kept, and it is kept in
 * the app's private storage, read-only.
 */
class EngineStore(private val context: Context, private val parts: List<Part> = PARTS, private val source: String = SOURCE) {

    /** One of the engine's files: its size and SHA-256 once unpacked. */
    class Part(val name: String, val bytes: Long, val sha256: String)

    /** The engine is on the phone but Android wouldn't start it. [detail] is the system's own wording. */
    class WontStart(val detail: String) : Exception(detail)

    private val root = File(context.filesDir, "engine")
    private val dir = File(root, VERSION)

    val isReady: Boolean get() = parts.all { File(dir, it.name).length() == it.bytes }

    /** Puts the engine on the phone if it isn't there yet, reporting progress 0..1. */
    suspend fun obtain(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (isReady) return@withContext
        dir.mkdirs()
        val total = parts.sumOf { it.bytes }
        var before = 0L
        for (part in parts) {
            val file = File(dir, part.name)
            if (file.length() != part.bytes) {
                val tmp = File(dir, part.name + ".part")
                val report = { have: Long -> onProgress((before + have).toFloat() / total) }
                val borrowed = fromAccentCoach(part, tmp, report) && sha256(tmp) == part.sha256
                if (!borrowed) {
                    download(part, tmp, report)
                    if (sha256(tmp) != part.sha256) {
                        tmp.delete()
                        throw IOException("The speech engine was damaged on the way. Try again.")
                    }
                }
                file.delete()
                if (!tmp.renameTo(file)) throw IOException("Couldn't save the speech engine.")
                file.setReadOnly()
            }
            before += part.bytes
        }
        // An engine of another version, from before an update, is no longer of use.
        root.listFiles()?.filter { it.name != VERSION }?.forEach { it.deleteRecursively() }
    }

    /** Copies [part] out of Accent Coach's package, if that app is installed and carries a file of that size. */
    private suspend fun fromAccentCoach(part: Part, into: File, onProgress: (Long) -> Unit): Boolean = try {
        val apk = context.packageManager.getApplicationInfo(ModelStore.ACCENT_COACH, 0).sourceDir
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry("lib/$ABI/${part.name}")
            if (entry == null || entry.size != part.bytes) {
                false
            } else {
                zip.getInputStream(entry).use { copy(it, into, onProgress) }
                true
            }
        }
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: IOException) {
        false
    }

    private suspend fun download(part: Part, into: File, onProgress: (Long) -> Unit) {
        val conn = URL(source + part.name + ".gz").openConnection().apply {
            connectTimeout = 20_000
            readTimeout = 30_000
        }
        try {
            if (conn is HttpURLConnection && conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Couldn't download the speech engine (HTTP ${conn.responseCode})")
            }
            GZIPInputStream(conn.getInputStream()).use { copy(it, into, onProgress) }
        } finally {
            (conn as? HttpURLConnection)?.disconnect()
        }
    }

    private suspend fun copy(input: InputStream, into: File, onProgress: (Long) -> Unit) {
        FileOutputStream(into, false).use { out ->
            val buf = ByteArray(1 shl 16)
            var have = 0L
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                have += n
                onProgress(have)
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

    /**
     * Loads the engine into the app, once. ONNX Runtime's Java side, when it sees it is
     * running on Android, only looks for its library inside the app's own package
     * (System.loadLibrary), where it no longer is. Off Android it loads the library from a
     * folder it is pointed at, by full path, which works on Android just as well. So for
     * the moment it starts up it is pointed at the folder and told it isn't on Android.
     */
    fun start() = synchronized(lock) {
        if (started) return
        val vendor = System.getProperty("java.vendor")
        try {
            System.setProperty("onnxruntime.native.path", dir.path)
            System.setProperty("java.vendor", "Recitation")
            OrtEnvironment.getEnvironment()
            started = true
        } catch (e: LinkageError) {
            // The library wouldn't load, or isn't the one these classes were built for.
            throw WontStart("${e.javaClass.simpleName}: ${(e.cause ?: e).message}")
        } catch (e: RuntimeException) {
            throw WontStart("${e.javaClass.simpleName}: ${(e.cause ?: e).message}")
        } finally {
            if (vendor != null) System.setProperty("java.vendor", vendor) else System.clearProperty("java.vendor")
        }
    }

    companion object {
        private val lock = Any()

        @Volatile private var started = false

        /** The ONNX Runtime release the app's Java classes are from; the library must be the same one. */
        const val VERSION = "1.31.1"
        private const val ABI = "arm64-v8a"
        private const val SOURCE = "https://raw.githubusercontent.com/Rabih-Mogh/OmniRepo/main/apps/recitation/engine/onnxruntime-$VERSION/$ABI/"

        /** In the order they have to be loaded: the second needs the first. */
        val PARTS = listOf(
            Part("libonnxruntime.so", 34_208_680L, "46e0c16cd515c62a1dd6c5994448b5413705478354bc20a756a5a82634c3ca03"),
            Part("libonnxruntime4j_jni.so", 122_576L, "c80ab74a6ff66f04943e509a7824742ea2dfe8c6aa1d9dfdaf86c31f8cebdcfc"),
        )
    }
}

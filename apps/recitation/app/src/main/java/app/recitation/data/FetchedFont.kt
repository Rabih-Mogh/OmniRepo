package app.recitation.data

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * A font that isn't shipped in the app or kept in the repo because its licence doesn't
 * clearly allow that. Like the verses, it is fetched the first time the app runs and kept
 * on the phone. It is only used if it is byte for byte the file this code was written
 * against ([sha256]).
 */
class FetchedFont(private val name: String, private val sha256: String, private val sources: List<String>) {

    private val _font = MutableStateFlow<FontFamily?>(null)

    /** The font, once it is on the phone; null before. */
    val font: StateFlow<FontFamily?> = _font.asStateFlow()

    /** The font's file, once it has been fetched and checked; null before. For drawing outside Compose. */
    fun file(context: Context): File? = File(File(context.filesDir, "fonts"), name).takeIf { _font.value != null }

    /** Reads the font from the phone, fetching it first if need be. Says nothing if it can't: whatever uses it falls back. */
    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        if (_font.value != null) return@withContext
        val file = File(File(context.filesDir, "fonts").apply { mkdirs() }, name)
        try {
            if (!file.exists() || digest(file.readBytes()) != sha256) {
                val bytes = sources.firstNotNullOfOrNull { url -> runCatching { fetch(url) }.getOrNull()?.takeIf { digest(it) == sha256 } }
                    ?: return@withContext
                val part = File(file.path + ".part")
                part.writeBytes(bytes)
                part.renameTo(file)
            }
            _font.value = FontFamily(Font(file))
        } catch (_: Exception) {
            // No connection, or no room: the fallback until next time.
        }
    }

    private fun fetch(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

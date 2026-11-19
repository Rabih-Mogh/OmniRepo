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
 * The surahs' names as calligraphy: Quran.com's surah-name font, in which every surah has
 * one glyph, its name written in the Thuluth hand of a mushaf's headings.
 *
 * The font carries no licence, so it isn't shipped in the app or kept in the repo. Like
 * the verses, it is fetched from Quran.com the first time the app runs and kept on the
 * phone. Until it is there, names are written in the bundled calligraphic font instead.
 * The file is only used if it is byte for byte the one this code was written against.
 */
object SurahNames {

    private val _font = MutableStateFlow<FontFamily?>(null)

    /** The calligraphy, once it is on the phone; null before. */
    val font: StateFlow<FontFamily?> = _font.asStateFlow()

    /** The font's file, once it has been fetched and checked; null before. For drawing outside Compose. */
    fun file(context: Context): File? = File(File(context.filesDir, "fonts"), "sura_names.ttf").takeIf { _font.value != null }

    /** The glyph holding surah [number]'s name. */
    fun glyph(number: Int): String = String(Character.toChars(0xE000 + number.toString().toInt(16)))

    /** The glyph holding the word "surah". */
    const val SURAH_WORD = ""

    /** Reads the font from the phone, fetching it first if need be. Says nothing if it can't: the names fall back. */
    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        if (_font.value != null) return@withContext
        val file = File(File(context.filesDir, "fonts").apply { mkdirs() }, "sura_names.ttf")
        try {
            if (!file.exists() || sha256(file.readBytes()) != SHA256) {
                val bytes = SOURCES.firstNotNullOfOrNull { url -> runCatching { fetch(url) }.getOrNull()?.takeIf { sha256(it) == SHA256 } }
                    ?: return@withContext
                val part = File(file.path + ".part")
                part.writeBytes(bytes)
                part.renameTo(file)
            }
            _font.value = FontFamily(Font(file))
        } catch (_: Exception) {
            // No connection, or no room: plain calligraphy until next time.
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

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val SOURCES = listOf(
        "https://quran.com/fonts/quran/surah-names/v1/sura_names.ttf",
        "https://raw.githubusercontent.com/quran/quran.com-frontend-next/production/public/fonts/quran/surah-names/v1/sura_names.ttf",
    )
    private const val SHA256 = "6caa808e10abc7e5d93ef45815e50ecf53287ceb010d3216266e49e44871e673"
}

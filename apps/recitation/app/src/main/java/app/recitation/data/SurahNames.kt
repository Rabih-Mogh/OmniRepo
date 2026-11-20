package app.recitation.data

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.StateFlow
import java.io.File

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

    private val fetched = FetchedFont(
        "sura_names.ttf", "6caa808e10abc7e5d93ef45815e50ecf53287ceb010d3216266e49e44871e673",
        listOf(
            "https://quran.com/fonts/quran/surah-names/v1/sura_names.ttf",
            "https://raw.githubusercontent.com/quran/quran.com-frontend-next/production/public/fonts/quran/surah-names/v1/sura_names.ttf",
        ),
    )

    /** The calligraphy, once it is on the phone; null before. */
    val font: StateFlow<FontFamily?> get() = fetched.font

    /** The font's file, once it has been fetched and checked; null before. For drawing outside Compose. */
    fun file(context: Context): File? = fetched.file(context)

    /** The glyph holding surah [number]'s name. */
    fun glyph(number: Int): String = String(Character.toChars(0xE000 + number.toString().toInt(16)))

    /** The glyph holding the word "surah". */
    const val SURAH_WORD = ""

    /** Reads the font from the phone, fetching it first if need be. Says nothing if it can't: the names fall back. */
    suspend fun load(context: Context) = fetched.load(context)
}

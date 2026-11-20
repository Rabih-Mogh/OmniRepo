package app.recitation.data

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * How the verses are written on the page. The words are the same in all of them, and the
 * recitation is always lined up with the Uthmani spelling; only what is shown changes.
 */
enum class Script(val key: String) {
    /** The Uthmani spelling, as typeset in the Madinah mushaf, in the King Fahd Complex's own font. */
    MADINAH("madinah"),

    /** The same Uthmani spelling in the Amiri Quran font, a rounder hand. */
    AMIRI("amiri"),

    /** The spelling and marks of the mushafs of the Indian subcontinent. */
    INDOPAK("indopak"),

    /** Everyday Arabic spelling, as taught at school, fully vowelled. */
    EVERYDAY("everyday");

    companion object {
        fun of(key: String?): Script = entries.firstOrNull { it.key == key } ?: MADINAH
    }
}

/**
 * The font for the IndoPak script: "AlQuran IndoPak by QuranWBW", which Quran.com uses. It
 * states no licence one could ship it under, so it is fetched from Quran.com on first run
 * and kept on the phone; the IndoPak script is offered once it is there.
 */
object IndoPakFont {
    private val fetched = FetchedFont(
        "indopak.ttf", "4c8f002e7538ef6351ac676eb0f2c1708ecd17f6f8a5fc4a19dbc8eaf4dddff5",
        listOf(
            "https://quran.com/fonts/quran/hafs/nastaleeq/indopak/indopak-nastaleeq-waqf-lazim-v4.2.1.ttf",
            "https://raw.githubusercontent.com/quran/quran.com-frontend-next/production/public/fonts/quran/hafs/nastaleeq/indopak/indopak-nastaleeq-waqf-lazim-v4.2.1.ttf",
        ),
    )

    val font: StateFlow<FontFamily?> get() = fetched.font

    /** The font's file, once it is on the phone; null before. */
    fun file(context: Context): File? = fetched.file(context)

    suspend fun load(context: Context) = fetched.load(context)
}

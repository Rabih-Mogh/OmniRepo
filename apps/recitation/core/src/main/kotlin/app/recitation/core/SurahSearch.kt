package app.recitation.core

import java.text.Normalizer

/**
 * Finds what was typed into the search box among the surahs: a surah by its number or by
 * part of its name (in Arabic or Latin letters, or what the name means), and a verse by its
 * numbers, as in "2:255" or "baqarah 255".
 */
object SurahSearch {

    /** [surahs] that match, in the Quran's order, and the verse asked for by number, if one was: surah and verse, counted from 1. */
    class Found(val surahs: List<Surah>, val verse: Pair<Int, Int>?)

    private val TWO_NUMBERS = Regex("""^(\d{1,3})\s*[:\s.,/-]\s*(\d{1,3})$""")
    private val NAME_AND_NUMBER = Regex("""^(.*\D)\s*(\d{1,3})$""")

    fun find(query: String): Found {
        val typed = digits(query.trim())
        if (typed.isEmpty()) return Found(emptyList(), null)

        TWO_NUMBERS.matchEntire(typed)?.let { numbers ->
            val surah = SURAHS.getOrNull(numbers.groupValues[1].toInt() - 1)
            val verse = numbers.groupValues[2].toInt()
            return Found(listOfNotNull(surah), surah?.takeIf { verse in 1..it.verses }?.let { it.number to verse })
        }
        typed.toIntOrNull()?.let { number -> return Found(listOfNotNull(SURAHS.getOrNull(number - 1)), null) }

        // A name, perhaps with a verse number after it.
        val withNumber = NAME_AND_NUMBER.matchEntire(typed)
        val named = named(withNumber?.groupValues?.get(1) ?: typed)
        val verse = withNumber?.groupValues?.get(2)?.toInt()
        val first = named.firstOrNull()
        return Found(named, if (first != null && verse != null && verse in 1..first.verses) first.number to verse else null)
    }

    private fun named(name: String): List<Surah> {
        val wanted = plain(name)
        if (wanted.isEmpty()) return emptyList()
        return SURAHS.filter { wanted in plain(it.latin) || wanted in plain(it.arabic) || wanted in plain(it.meaning) }
    }

    /**
     * Text reduced to what matters when comparing: letters and digits only, lower case,
     * without vowel marks or accents, and with the letters Arabic writes several ways made one.
     */
    fun plain(text: String): String {
        val out = StringBuilder(text.length)
        for (c in Normalizer.normalize(digits(text), Normalizer.Form.NFD)) {
            when {
                c == 'ٱ' || c == 'أ' || c == 'إ' || c == 'آ' -> out.append('ا')
                c == 'ى' -> out.append('ي')
                c == 'ة' -> out.append('ه')
                c == 'ـ' -> Unit
                c.isLetterOrDigit() -> out.append(c.lowercaseChar())
            }
        }
        return out.toString()
    }

    /** Arabic-Indic digits as the ones a regex knows. */
    private fun digits(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in '٠'..'٩') '0' + (c - '٠') else c)
    }
}

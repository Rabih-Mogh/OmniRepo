package app.recitation.core

/**
 * Works out which surah a video is from its title, e.g. "002 | سورة البقرة | مشاري العفاسي"
 * or "Surah Ya-Sin (Yaseen) - Full". Titles are untidy, so this is a best guess: the app
 * shows it and lets it be corrected.
 */
object SurahMatcher {

    private val ARABIC_SURAH_WORDS = setOf("سوره", "سورت")
    private val LATIN_SURAH_WORDS = setOf("surah", "sura", "surat", "sorah", "sora", "soorah", "sourate", "sourat", "sure", "suresi")

    /** The "the" in front of a Latin-letter name, in the many ways it gets written. */
    private val ARTICLES = setOf("al", "an", "ar", "as", "ash", "at", "ath", "az", "ad", "adh", "el", "aal", "ali")

    private val arabicNames: Map<String, Int> = buildMap {
        for (s in SURAHS) {
            val name = arabic(s.arabic).joinToString("")
            put(name, s.number)
            if (name.startsWith("ال") && name.length > 4) put(name.removePrefix("ال"), s.number)
        }
        // Other names in common use.
        for ((name, number) in listOf(
            "الإسراء" to 17, "بني إسرائيل" to 17, "غافر" to 40, "المؤمن" to 40, "فصلت" to 41, "التوبة" to 9,
            "براءة" to 9, "الإنسان" to 76, "الدهر" to 76, "المسد" to 111, "تبت" to 111, "الإخلاص" to 112,
            "عم" to 78, "تبارك" to 67,
        )) put(arabic(name).joinToString(""), number)
    }

    private val latinNames: Map<String, Int> = buildMap {
        for (s in SURAHS) put(latinKey(latin(s.latin)), s.number)
        // Other names and spellings in common use.
        for ((name, number) in listOf(
            "yasin" to 36, "isra" to 17, "ghafir" to 40, "mumin" to 40, "fussilat" to 41, "tawbah" to 9,
            "insan" to 76, "masad" to 111, "lahab" to 111, "ikhlas" to 112, "tabarak" to 67, "imran" to 3,
            "bakara" to 2, "fatiha" to 1, "nas" to 114, "falaq" to 113, "kahf" to 18, "mulk" to 67, "waqia" to 56,
        )) put(latinKey(listOf(name)), number)
    }

    fun match(title: String): Surah? {
        val number = find(title) ?: return null
        return SURAHS[number - 1]
    }

    private fun find(title: String): Int? {
        var best: Int? = null
        var bestRank = 0
        fun offer(number: Int, rank: Int) {
            if (rank > bestRank) {
                best = number
                bestRank = rank
            }
        }

        // Arabic: runs of one to three words, said right after "سورة" for preference.
        val ar = arabic(title)
        for (start in ar.indices) {
            for (length in 3 downTo 1) {
                if (start + length > ar.size) continue
                val number = arabicNames[ar.subList(start, start + length).joinToString("")] ?: continue
                val named = start > 0 && ar[start - 1] in ARABIC_SURAH_WORDS
                // One- and two-letter names (ق, ص, طه, يس) only count when introduced as a surah.
                val short = ar.subList(start, start + length).sumOf { it.length } <= 2
                if (named) offer(number, 100 + length) else if (!short) offer(number, 40 + length - start.coerceAtMost(9))
            }
        }

        // Latin letters, the same way.
        val la = latin(title)
        for (start in la.indices) {
            if (la[start] in LATIN_SURAH_WORDS) continue
            for (length in 3 downTo 1) {
                if (start + length > la.size) continue
                val words = la.subList(start, start + length)
                val key = latinKey(words)
                if (key.length < 2) continue
                val named = start > 0 && la[start - 1] in LATIN_SURAH_WORDS
                val number = latinNames[key] ?: if (key.length >= 5) latinNames.entries.firstOrNull { near(it.key, key) }?.value else null
                if (number == null) continue
                if (named) offer(number, 90 + length) else if (key.length >= 4) offer(number, 30 + length - start.coerceAtMost(9))
            }
        }

        // A number: "002 | …" at the very start, or straight after the word surah.
        val tokens = Regex("[\\p{L}\\p{Nd}]+").findAll(title).map { it.value }.toList()
        for ((i, token) in tokens.withIndex()) {
            val n = token.map { Character.getNumericValue(it) }.takeIf { d -> d.all { it in 0..9 } }
                ?.fold(0) { acc, d -> (acc * 10 + d).coerceAtMost(1000) } ?: continue
            if (n !in 1..114) continue
            val before = tokens.getOrNull(i - 1)?.let { arabic(it).firstOrNull() ?: latin(it).firstOrNull() }
            if (before != null && (before in ARABIC_SURAH_WORDS || before in LATIN_SURAH_WORDS)) offer(n, 80)
            else if (i == 0) offer(n, 60)
        }
        return best
    }

    /** Arabic words of [text], with marks dropped and look-alike letters made the same. */
    internal fun arabic(text: String): List<String> {
        val out = ArrayList<String>()
        val word = StringBuilder()
        fun flush() {
            if (word.isNotEmpty()) out += word.toString()
            word.setLength(0)
        }
        for (c in text) {
            when (c) {
                in 'ً'..'ٟ', 'ٰ', 'ـ' -> {}
                'أ', 'إ', 'آ', 'ٱ', 'ا' -> word.append('ا')
                'ى', 'ي', 'ئ' -> word.append('ي')
                'ة', 'ه' -> word.append('ه')
                'ؤ', 'و' -> word.append('و')
                in 'ء'..'ي' -> word.append(c)
                else -> flush()
            }
        }
        flush()
        return out
    }

    /** Latin-letter words of [text], lower-case, with accents and apostrophes dropped. */
    internal fun latin(text: String): List<String> {
        val plain = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\p{Mn}'’`ʿʾ]"), "").lowercase()
        return Regex("[a-z]+").findAll(plain).map { it.value }.toList()
    }

    /** One spelling for the many ways a name is written: "Al-Baqarah", "al baqara", "Baqarah" → "baqara". */
    internal fun latinKey(words: List<String>): String {
        val kept = if (words.size > 1) words.filter { it !in ARTICLES }.ifEmpty { words } else words
        var s = kept.joinToString("")
        s = s.replace("ee", "i").replace("oo", "u").replace("ou", "u").replace("aa", "a").replace("y", "i")
        s = s.replace(Regex("(.)\\1"), "$1")
        if (s.endsWith("ah") || s.endsWith("at")) s = s.dropLast(1)
        return s
    }

    /** One letter added, dropped or changed. */
    private fun near(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}

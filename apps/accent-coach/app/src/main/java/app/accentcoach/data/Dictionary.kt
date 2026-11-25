package app.accentcoach.data

import app.accentcoach.core.MyWord
import app.accentcoach.core.MyWords
import app.accentcoach.core.Word
import java.io.InputStream

/**
 * The native sounds of some 234,000 English words, bundled in assets/dictionary.txt: a
 * word, a tab and its phonemes on each line, the lines sorted. It is read through each
 * time rather than kept in memory, as it is only wanted when a word is added.
 */
class Dictionary(private val open: () -> InputStream) {

    /** The phonemes of those of [words] (in small letters) that the dictionary has. */
    fun lookup(words: Set<String>): Map<String, List<String>> {
        val found = HashMap<String, List<String>>()
        val last = words.maxOrNull() ?: return found
        open().bufferedReader().useLines { lines ->
            for (line in lines) {
                val tab = line.indexOf('\t')
                if (tab < 0) continue
                val word = line.substring(0, tab)
                if (word in words) found[word] = line.substring(tab + 1).split(' ')
                // Sorted, so nothing past the last word wanted can be one of them.
                if (found.size == words.size || word > last) break
            }
        }
        return found
    }
}

/**
 * Works out the native sounds of what the learner typed. A word the [dictionary] has
 * gets them from there. Any other is said by the phone's voice and written down by the
 * recogniser ([hear]); the result is marked as heard, since it is only as good as that
 * hearing.
 */
class References(private val dictionary: Dictionary, private val hear: suspend (String) -> List<String>) {

    /** [text] is as [MyWords.clean] left it. Throws what [hear] throws for a word that can't be worked out. */
    suspend fun of(text: String): MyWord {
        val parts = text.split(' ')
        val known = dictionary.lookup(parts.map(MyWords::key).toSet())
        var heard = false
        val words = parts.map { part ->
            Word(part, known[MyWords.key(part)] ?: hear(part).also { heard = true })
        }
        return MyWord(text, words, heard)
    }
}

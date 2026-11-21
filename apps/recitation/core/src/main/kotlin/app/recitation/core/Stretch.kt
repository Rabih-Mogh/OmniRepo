package app.recitation.core

/**
 * The words of a stretch of a recording that was heard out of turn (see [Locator]), found a
 * chunk of [chunk] ten-second pieces at a time.
 *
 * A chunk that has been heard whole is found once and kept. So however long the stretch
 * grows, only its last, unfinished chunk is gone over again, and only when a piece has
 * been added to it: the work stays small, and no stretch is too long to be found whole.
 */
class Stretch(private val recital: Recital, private val lengthMs: Long, private val chunk: Int = CHUNK) {

    /** The [words] found in a stretch, and which [pieces] of it they cover: a chunk whose words couldn't be told is left out. */
    class Found(val words: List<Repeat>, val pieces: List<IntRange>)

    private var start = -1
    private val whole = HashMap<Int, Repeat?>() // by a chunk's first piece; null where the words couldn't be told
    private var last: Repeat? = null // the unfinished chunk, as it was found with lastSize pieces
    private var lastAt = -1
    private var lastSize = 0

    /** [pieces] is what has been heard, by piece; the stretch begins at piece [first] and runs for as long as one piece follows another. */
    fun words(pieces: Map<Int, Heard>, first: Int): Found {
        if (first != start) {
            whole.clear()
            lastAt = -1
            start = first
        }
        val words = ArrayList<Repeat>()
        val covered = ArrayList<IntRange>()
        var from = -1 // where the run of chunks found, one after another, began
        var at = first
        while (at in pieces) {
            val heard = generateSequence(at) { it + 1 }.take(chunk).takeWhile { it in pieces }.map { pieces.getValue(it) }.toList()
            val full = heard.size == chunk
            val here = when {
                full && at in whole -> whole[at]
                !full && at == lastAt && heard.size == lastSize -> last
                else -> locate(heard, at).also {
                    if (full) whole[at] = it else { last = it; lastAt = at; lastSize = heard.size }
                }
            }
            if (here != null) {
                words += here
                if (from < 0) from = at
            } else if (from >= 0) {
                covered += from until at
                from = -1
            }
            at += heard.size
            if (!full) break
        }
        if (from >= 0) covered += from until at
        return Found(words, covered)
    }

    private fun locate(heard: List<Heard>, piece: Int): Repeat? {
        val joined = Hearing.joined(heard)
        return recital.locate(joined.rows, joined.frames, piece.toFloat() * Hearing.PIECE_MS / maxOf(1L, lengthMs))
    }

    companion object {
        /** Eighty seconds: long enough to tell where it is in the surah, short enough to place whole however fast the reciter. */
        const val CHUNK = 8
    }
}

package app.recitation.core

import kotlin.math.abs

/**
 * Finds which of a surah's words a stretch of its recording is, when the stretch was heard
 * out of turn: the reader jumped ahead, and the listening went there after them, so what
 * comes before it hasn't been placed and can't say where in the surah it is.
 *
 * How far through the recording the stretch begins gives a rough idea, good to within a
 * fifth of the surah or so. The stretch is then tried against every run of words in that
 * neighbourhood that it could be, beginning at the start of a word, and the run its sounds
 * fit best is taken. If even that one fits poorly, nothing is claimed.
 */
internal object Locator {

    /**
     * [rows] is what was heard of the stretch; [around] is how far through the recording it
     * begins, 0..1. Returns each sound found with the row it begins at, or null.
     */
    fun find(phones: List<Phone>, ordinal: IntArray, rows: FloatArray, around: Float): List<Pair<Int, Int>>? {
        val width = SoundColumns.WIDTH
        var n = rows.size / width
        if (n < FEWEST_ROWS) return null
        val count = phones.size
        // First where it begins, from its opening rows alone and anywhere in the neighbourhood.
        val guess = (around.coerceIn(0f, 1f) * count).toInt()
        val reach = maxOf(count / 5, 900)
        val opening = minOf(n, OPENING_ROWS)
        val began = walk(phones, ordinal, rows, 0, opening, maxOf(0, guess - reach), minOf(count, guess + reach + opening), guess, reach, PRIOR) ?: return null
        // Then all of it, knowing where it begins. A sound takes a row at least, so it can't run further than its rows.
        val start = began.first().first
        val from = if (n.toLong() * (n + 2 * MARGIN) > CELLS) n - (CELLS / (n + 2 * MARGIN)).toInt() else 0
        if (from > 0) return began // too long to place whole: its opening will have to do
        val all = walk(phones, ordinal, rows, 0, n, maxOf(0, start - MARGIN), minOf(count, start + n + MARGIN), start, MARGIN, 4 * PRIOR) ?: return null
        val required = all.filter { !phones[it.first].optional }
        val clear = required.count { rows[it.second * width + phones[it.first].sound.ordinal + 1] > CLEAR }
        return if (required.size >= FEWEST_SOUNDS && clear >= required.size * CLEAR_SHARE) all else null
    }

    /**
     * The best way of reading rows [top] until [bottom] as a run of the sounds [lo] until
     * [hi]: beginning at the start of a word (near [guess], more or less, by [prior]),
     * each sound in turn, ending wherever the rows run out.
     */
    private fun walk(phones: List<Phone>, ordinal: IntArray, rows: FloatArray, top: Int, bottom: Int, lo: Int, hi: Int, guess: Int, reach: Int, prior: Float): List<Pair<Int, Int>>? {
        val width = SoundColumns.WIDTH
        val n = bottom - top
        val size = hi - lo
        if (n <= 0 || size <= 0) return null
        var prev = FloatArray(size) { NEVER }
        var cur = FloatArray(size)
        val came = ByteArray(n * size) // 0: stayed; k: came on from k sounds back; START: began here
        for (row in 0 until n) {
            val scores = (top + row) * width
            for (x in 0 until size) {
                val s = lo + x
                val heard = rows[scores + phones[s].sound.ordinal + 1]
                var best = if (row > 0) prev[x] + maxOf(heard, NOISE) else NEVER
                var how = 0
                var back = 1
                while (row > 0 && x - back >= 0 && back <= SKIP) {
                    if (prev[x - back] + heard > best) { best = prev[x - back] + heard; how = back }
                    if (!phones[s - back].optional) break
                    back++
                }
                if (s == 0 || ordinal[s] != ordinal[s - 1]) {
                    val begin = row * NOISE + heard - prior * abs(s - guess) / reach
                    if (begin > best) { best = begin; how = START }
                }
                cur[x] = best
                came[row * size + x] = how.toByte()
            }
            val swap = prev; prev = cur; cur = swap
        }
        var x = 0
        for (i in 1 until size) if (prev[i] > prev[x]) x = i
        if (prev[x] <= NEVER / 2) return null
        val found = ArrayList<Pair<Int, Int>>()
        var row = n - 1
        while (row >= 0) {
            val how = came[row * size + x].toInt()
            if (how != 0) found += lo + x to top + row
            if (how == START) break
            x -= how
            row--
        }
        if (found.isEmpty()) return null
        found.reverse()
        return found
    }

    private const val START = 127
    private const val NEVER = -1e30f

    /** A sound scoring above this (about one chance in ten) was heard clearly where it was put. */
    private const val CLEAR = -2.3f

    /** A row that belongs to no sound of the run costs this much. */
    private const val NOISE = -5f

    /** How many sounds that may be left out can be passed over together. */
    private const val SKIP = 8

    /** How much a run is held back for beginning far from where the recording's clock says it should. */
    private const val PRIOR = 3f

    /** Too little to tell anything by. */
    private const val FEWEST_ROWS = 30

    /** How many of its rows are used to find where a stretch begins. */
    private const val OPENING_ROWS = 500

    /** How far either side of where it begins the whole stretch is then placed. */
    private const val MARGIN = 60

    /** A placing is believed when it has at least this many sounds, and this share of them heard clearly. */
    private const val FEWEST_SOUNDS = 20
    private const val CLEAR_SHARE = 0.6f

    /** The most (rows × sounds) a stretch is placed whole with. */
    private const val CELLS = 24_000_000L
}

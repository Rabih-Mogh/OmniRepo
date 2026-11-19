package app.recitation.core

/**
 * Lines a known run of sounds up with what the model heard.
 *
 * [rows] holds one row of [SoundColumns.WIDTH] scores for every frame that was kept (the
 * chance of a pause, then of each sound). The answer says, for each sound, which row it
 * starts at. Sounds marked optional may be left out, and any amount of other speech may
 * come before the first sound and after the last.
 *
 * This is a best-path search over (row, position in the sounds), the same idea as CTC
 * forced alignment, with one difference: the whole recording is solved at once, so every
 * sound must be placed somewhere. Settling the path a window at a time was tried first, and
 * on unclear recordings it fell behind and never caught up. To fit a two-hour surah in a
 * phone's memory the search is split in halves (Hirschberg's method): the best position
 * at the middle row is found from a forward and a backward pass that keep one row of
 * scores each, and the two halves are solved the same way.
 */
class Aligner(private val rows: FloatArray, sounds: List<Phone>, private val direct: Long = DIRECT) {

    private val width = SoundColumns.WIDTH
    private val frames = rows.size / width
    private val count = sounds.size

    // Positions: pause, sound 0, pause, sound 1, … pause. Odd positions are sounds.
    private val states = 2 * count + 1
    private val column = IntArray(states) { if (it % 2 == 1) sounds[it / 2].sound.ordinal + 1 else 0 }

    // Jumps over optional sounds: from the pause before a run of them (or the sound before
    // that) to the sound or the pause after the run. Listed by where they land, and again by
    // where they leave.
    private val jumpFrom: IntArray
    private val landing: IntArray // jumps landing on state s come from jumpFrom[landing[s] until landing[s + 1]]
    private val leaveTo: IntArray
    private val leaving: IntArray // jumps leaving state s go to leaveTo[leaving[s] until leaving[s + 1]]

    init {
        val from = ArrayList<Int>()
        val to = ArrayList<Int>()
        for (j in 1..count) {
            var k = j - 1
            while (k >= 0 && sounds[k].optional && j - k <= MAX_RUN) {
                for (start in intArrayOf(2 * k, 2 * k - 1)) {
                    if (start < 0) continue
                    from += start; to += 2 * j
                    if (j < count) { from += start; to += 2 * j + 1 }
                }
                k--
            }
        }
        landing = IntArray(states + 1)
        for (t in to) landing[t + 1]++
        for (s in 0 until states) landing[s + 1] += landing[s]
        jumpFrom = IntArray(from.size)
        var next = landing.copyOf()
        for (i in from.indices) jumpFrom[next[to[i]]++] = from[i]
        leaving = IntArray(states + 1)
        for (f in from) leaving[f + 1]++
        for (s in 0 until states) leaving[s + 1] += leaving[s]
        leaveTo = IntArray(from.size)
        next = leaving.copyOf()
        for (i in from.indices) leaveTo[next[from[i]]++] = to[i]
    }

    /** The row each sound starts at, or -1 for a sound that was left out. Null if the sounds can't fit. */
    fun align(): IntArray? {
        val path = path() ?: return null
        val starts = IntArray(count) { -1 }
        for (t in 0 until frames) {
            val s = path[t]
            if (s % 2 == 1 && starts[s / 2] < 0) starts[s / 2] = t
        }
        return starts
    }

    /** Where the best path stands at each row: an even number is a pause, an odd one the sound at half of it. */
    internal fun path(): IntArray? {
        if (count == 0 || frames == 0) return null
        val path = IntArray(frames)
        return if (solve(0, frames, 0, states - 1, path)) path else null
    }

    /** The score the model gave sound [index] at [row]: about 0 when heard clearly, very negative when not. */
    fun score(index: Int, row: Int): Float = rows[row * width + column[2 * index + 1]]

    /**
     * Fills [path] for rows [from] until [until]. The path stood at [before] just ahead of
     * them and must be able to step to [after] just past them.
     */
    private fun solve(from: Int, until: Int, before: Int, after: Int, path: IntArray): Boolean {
        val n = until - from
        if (n == 0) return true
        if (n.toLong() * (after - before + 1) <= direct) return direct(from, until, before, after, path)
        val mid = from + n / 2
        val ahead = forward(from, mid + 1, before, after)
        val behind = backward(mid + 1, until, before, after)
        var best = -1
        var bestScore = UNREACHABLE
        for (i in ahead.indices) {
            val score = ahead[i] + behind[i]
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        if (best < 0) return false
        val at = before + best
        path[mid] = at
        return solve(from, mid, before, at, path) && solve(mid + 1, until, at, after, path)
    }

    /** Best score of reaching each state in [lo]..[hi] at row `until - 1`, starting from [lo] before row [from]. */
    private fun forward(from: Int, until: Int, lo: Int, hi: Int): FloatArray {
        val size = hi - lo + 1
        var prev = FloatArray(size) { NEVER }
        var cur = FloatArray(size)
        prev[0] = 0f
        var reach = 0 // the highest state with a real score
        for (t in from until until) {
            val newReach = step(prev, cur, lo, hi, reach)
            val row = t * width
            for (i in 0..newReach) cur[i] += rows[row + column[lo + i]]
            reach = newReach
            val swap = prev; prev = cur; cur = swap
        }
        for (i in reach + 1 until size) prev[i] = NEVER
        return prev
    }

    /**
     * One row forward, before the row's own scores are added: [cur] gets the best of the
     * states each one can be entered from. Returns the highest state now reachable.
     */
    private fun step(prev: FloatArray, cur: FloatArray, lo: Int, hi: Int, reach: Int): Int {
        val top = minOf(hi - lo, reach + 2)
        for (i in 0..top) {
            var best = if (i <= reach) prev[i] else NEVER
            if (i >= 1 && i - 1 <= reach && prev[i - 1] > best) best = prev[i - 1]
            if (i >= 2 && (lo + i) % 2 == 1 && i - 2 <= reach && prev[i - 2] > best) best = prev[i - 2]
            cur[i] = best
        }
        var newReach = top
        // Jumps can land beyond the usual two steps.
        val last = minOf(hi, lo + reach + 2 * MAX_RUN + 2)
        for (s in lo..last) {
            val i = s - lo
            for (j in landing[s] until landing[s + 1]) {
                val f = jumpFrom[j] - lo
                if (f < 0 || f > reach) continue
                val score = prev[f]
                if (i > newReach) {
                    for (x in newReach + 1 until i) cur[x] = NEVER
                    cur[i] = score
                    newReach = i
                } else if (score > cur[i]) {
                    cur[i] = score
                }
            }
        }
        return newReach
    }

    /**
     * For each state in [lo]..[hi]: the best score of rows [from] until [until] given the
     * path stands on that state just before them and can step to [hi] just past them.
     */
    private fun backward(from: Int, until: Int, lo: Int, hi: Int): FloatArray {
        val size = hi - lo + 1
        var next = FloatArray(size) { NEVER }
        var cur = FloatArray(size)
        next[size - 1] = 0f
        var floor = size - 1 // the lowest state with a real score
        for (t in until - 1 downTo from) {
            floor = back(next, cur, lo, hi, floor)
            val row = t * width
            for (i in floor until size) cur[i] += rows[row + column[lo + i]]
            val swap = next; next = cur; cur = swap
        }
        floor = back(next, cur, lo, hi, floor)
        for (i in 0 until floor) cur[i] = NEVER
        return cur
    }

    /**
     * One row backward, before the row's own scores are added: [cur] gets the best of the
     * states each one can step to. Only states from [floor] up have a score in [next];
     * returns the lowest state that has one in [cur].
     */
    private fun back(next: FloatArray, cur: FloatArray, lo: Int, hi: Int, floor: Int): Int {
        val size = hi - lo + 1
        // A state can step at most this far up, so nothing lower can have a score yet.
        val bottom = maxOf(0, floor - 2 * MAX_RUN - 2)
        for (i in bottom until floor) next[i] = NEVER
        var newFloor = size
        for (i in size - 1 downTo bottom) {
            var best = next[i]
            if (i + 1 < size && next[i + 1] > best) best = next[i + 1]
            if (i + 2 < size && (lo + i) % 2 == 1 && next[i + 2] > best) best = next[i + 2]
            val s = lo + i
            for (j in leaving[s] until leaving[s + 1]) {
                val to = leaveTo[j] - lo
                if (to < size && next[to] > best) best = next[to]
            }
            cur[i] = best
            if (best > UNREACHABLE) newFloor = i
        }
        return newFloor
    }

    /** Small enough to solve in one go, remembering for every row and state where the path came from. */
    private fun direct(from: Int, until: Int, lo: Int, hi: Int, path: IntArray): Boolean {
        val size = hi - lo + 1
        val n = until - from
        val came = ByteArray(n * size) // how many states back the best way in came from
        var prev = FloatArray(size) { NEVER }
        var cur = FloatArray(size)
        prev[0] = 0f
        for (t in 0 until n) {
            val row = (from + t) * width
            val base = t * size
            for (i in 0 until size) {
                var best = prev[i]
                var step = 0
                if (i >= 1 && prev[i - 1] > best) { best = prev[i - 1]; step = 1 }
                if (i >= 2 && (lo + i) % 2 == 1 && prev[i - 2] > best) { best = prev[i - 2]; step = 2 }
                val s = lo + i
                for (j in landing[s] until landing[s + 1]) {
                    val f = jumpFrom[j] - lo
                    if (f >= 0 && prev[f] > best) { best = prev[f]; step = i - f }
                }
                cur[i] = best + rows[row + column[s]]
                came[base + i] = step.toByte()
            }
            val swap = prev; prev = cur; cur = swap
        }
        // The last row must be on a state that can step to `hi`.
        back(FloatArray(size) { if (it == size - 1) 0f else NEVER }, cur, lo, hi, 0)
        var at = -1
        var bestScore = UNREACHABLE
        for (i in 0 until size) {
            val score = prev[i] + cur[i]
            if (score > bestScore) { bestScore = score; at = i }
        }
        if (at < 0) return false
        for (t in n - 1 downTo 0) {
            path[from + t] = lo + at
            at -= came[t * size + at].toInt() and 0xFF
        }
        return at == 0
    }

    private companion object {
        /** A score no real path can have: adding row scores to it leaves it where it is. */
        const val NEVER = -1e30f

        /** Anything at or below this came from [NEVER]. */
        const val UNREACHABLE = -1e29f

        /** How many optional sounds in a row can be left out together (the opening formulas are about 60). */
        const val MAX_RUN = 80

        /** Problems up to this many (row, state) cells are solved in one go. */
        const val DIRECT = 2_000_000L
    }
}

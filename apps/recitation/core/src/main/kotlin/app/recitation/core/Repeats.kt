package app.recitation.core

/**
 * Words the reciter said once more: the surah's words from [first] on (counting from 0
 * through the whole surah), each with when it was said that time. A reciter who runs out
 * of breath, or wants a passage heard again, goes back some words and carries on from there.
 */
class Repeat(val first: Int, val words: List<WordTiming>)

/**
 * Finds what a reciter said twice.
 *
 * [Aligner] places every sound of the surah once, in order, so it has nowhere to put a
 * passage that is recited again. It gets past one as best it can: the sounds of the words
 * around are spread over the passage wherever they more or less fit, and the words stand
 * still, or run ahead, for as long as it lasts. Further off, where the reciter simply
 * recites, its placing is sound.
 *
 * So the recording is gone through again a stretch at a time, between places where a long
 * run of sounds was each heard clearly where it was put, and each stretch is placed
 * afresh, this time letting the reciter go back: from the end of any word to the start of
 * that word or an earlier one. Going back costs enough that it is only chosen when it
 * explains a good deal. If a stretch read that way has him say passages again, and those
 * were heard clearly, they are kept as [Repeat]s and the stretch's sounds are placed as
 * that reading has them; otherwise nothing changes.
 */
internal object Repeats {

    /**
     * [path] is where [Aligner.path] stands at each row and [starts] the row each sound
     * starts at; [starts] is put right where a passage said again had thrown it off.
     * [ordinal] numbers the words the sounds belong to, in order. Returns each passage said
     * again as (sound, the row it began at) pairs.
     */
    fun find(phones: List<Phone>, ordinal: IntArray, rows: FloatArray, path: IntArray, starts: IntArray): List<List<Pair<Int, Int>>> {
        val width = SoundColumns.WIDTH
        val placed = phones.indices.filter { starts[it] >= 0 }
        if (placed.isEmpty()) return emptyList()

        // The places to trust: the middle of each run of sounds heard clearly where they were put. The longer the run, the surer.
        val anchors = ArrayList<Int>()
        val sureness = ArrayList<Int>()
        var i = 0
        while (i < placed.size) {
            var j = i
            while (j < placed.size && rows[starts[placed[j]] * width + phones[placed[j]].sound.ordinal + 1] > CLEAR) j++
            // A very long run gives several.
            var x = i + minOf(j - i, SURE_RUN) / 2
            while (j - i >= SHORTEST_RUN && x < j) {
                anchors += x
                sureness += minOf(minOf(x - i, j - 1 - x) * 2 + 1, SURE_RUN)
                x += SURE_RUN
            }
            i = maxOf(j, i + 1)
        }

        val passes = ArrayList<List<Pair<Int, Int>>>()
        var a = 0
        while (a + 1 < anchors.size) {
            // As long a stretch as is handled at once, ending on the surest place in its later part; the latest of equals.
            val from = starts[placed[anchors[a]]]
            var best = -1
            var b = a + 1
            while (b < anchors.size && starts[placed[anchors[b]]] - from <= STRETCH) {
                if (starts[placed[anchors[b]]] - from >= STRETCH / 3 && (best < 0 || sureness[b] >= sureness[best])) best = b
                b++
            }
            // None there: the first one further on, however far.
            if (best < 0) best = (a + 1 until anchors.size).firstOrNull { starts[placed[anchors[it]]] - from >= STRETCH / 3 } ?: anchors.size - 1
            passes += read(phones, ordinal, rows, starts, placed[anchors[a]], placed[anchors[best]])
            a = best
        }
        return passes
    }

    /**
     * Places the rows between sound [u] and sound [v] again, both of which stay where they
     * are, letting the reciter go back. Returns the passages he said again, having put
     * [starts] right for the sounds between; or nothing, [starts] untouched, if he didn't.
     * A reading with a passage that can't be believed is tried again with going back dearer.
     */
    private fun read(phones: List<Phone>, ordinal: IntArray, rows: FloatArray, starts: IntArray, u: Int, v: Int): List<List<Pair<Int, Int>>> {
        for (jump in JUMPS) return read(phones, ordinal, rows, starts, u, v, jump) ?: continue
        return emptyList()
    }

    /** Null if the reading has a passage that can't be believed. */
    private fun read(phones: List<Phone>, ordinal: IntArray, rows: FloatArray, starts: IntArray, u: Int, v: Int, jump: Float): List<List<Pair<Int, Int>>>? {
        val width = SoundColumns.WIDTH
        val count = phones.size
        fun wordStart(s: Int) = s == 0 || ordinal[s] != ordinal[s - 1]
        fun wordEnd(s: Int) = s == count - 1 || ordinal[s] != ordinal[s + 1]

        // He may have gone back some words before u, and may have carried on some words past v the first time.
        var first = u
        while (first > 0 && ordinal[first - 1] >= ordinal[u] - BACK) first--
        var last = v
        while (last + 1 < count && ordinal[last + 1] <= ordinal[v] + AHEAD) last++
        // Positions as in Aligner: a pause, then each sound and the pause after it.
        val lo = 2 * first
        val size = 2 * (last - first + 1) + 1
        val top = starts[u]
        val n = starts[v] - top + 1
        if (n < 2 || n.toLong() * size > CELLS) return emptyList()
        fun column(x: Int) = if ((lo + x) % 2 == 1) phones[(lo + x) / 2].sound.ordinal + 1 else 0

        var prev = FloatArray(size) { NEVER }
        var cur = FloatArray(size)
        prev[2 * u + 1 - lo] = 0f
        val came = IntArray(n * size)
        // For each position, the best position at or after it to go back from, and its score.
        val leave = FloatArray(size + 1)
        val leaveAt = IntArray(size + 1)
        for (row in 1 until n) {
            leave[size] = NEVER
            leaveAt[size] = -1
            for (x in size - 1 downTo 0) {
                val s = lo + x
                // The last sound of a word, or the pause after it.
                val ends = if (s % 2 == 1) wordEnd(s / 2) else s >= 2 && wordEnd(s / 2 - 1)
                if (ends && prev[x] > leave[x + 1]) { leave[x] = prev[x]; leaveAt[x] = x } else { leave[x] = leave[x + 1]; leaveAt[x] = leaveAt[x + 1] }
            }
            val scores = (top + row) * width
            for (x in 0 until size) {
                val s = lo + x
                var best = prev[x]
                var how = x
                if (x >= 1 && prev[x - 1] > best) { best = prev[x - 1]; how = x - 1 }
                if (s % 2 == 1) {
                    if (x >= 2 && prev[x - 2] > best) { best = prev[x - 2]; how = x - 2 }
                    // Over sounds that may be left out: from the pause before them, or the sound before that.
                    var skipped = s / 2 - 1
                    while (skipped >= first && phones[skipped].optional && s / 2 - skipped <= SKIP) {
                        for (source in intArrayOf(2 * skipped - lo, 2 * skipped - 1 - lo)) {
                            if (source >= 0 && prev[source] > best) { best = prev[source]; how = source }
                        }
                        skipped--
                    }
                    // Going back to the start of a word, from the end of this word or a later one.
                    if (wordStart(s / 2) && leave[x + 1] + jump > best) { best = leave[x + 1] + jump; how = leaveAt[x + 1] }
                }
                cur[x] = if (best <= UNREACHABLE) NEVER else best + rows[scores + column(x)]
                came[row * size + x] = how
            }
            val swap = prev; prev = cur; cur = swap
        }
        var x = 2 * v + 1 - lo
        if (prev[x] <= UNREACHABLE) return emptyList()

        // Back along the way it came: each sound with the row it began at.
        val reached = ArrayList<Pair<Int, Int>>()
        var wentBack = false
        for (row in n - 1 downTo 1) {
            val how = came[row * size + x]
            if (how != x) {
                if ((lo + x) % 2 == 1) reached += (lo + x) / 2 to top + row
                if (how > x) wentBack = true
                x = how
            }
        }
        if (!wentBack || x != 2 * u + 1 - lo) return emptyList()
        reached.reverse()

        // A sound heard for the first time is the placing's own; one heard before is being said again.
        val again = ArrayList<ArrayList<Pair<Int, Int>>>()
        val once = ArrayList<Pair<Int, Int>>()
        var furthest = u
        var before = u
        for (each in reached) {
            val sound = each.first
            if (sound > furthest) {
                once += each
                furthest = sound
            } else {
                if (sound <= before || again.isEmpty()) again += ArrayList<Pair<Int, Int>>()
                again.last() += each
            }
            before = sound
        }
        // What he said past v before going back is placed by the stretch after this one, on his
        // second time through; his first time through it is a passage of its own.
        val beyond = once.filter { it.first > v }
        if (beyond.isNotEmpty()) again.add(0, ArrayList(beyond))
        // Is it really him going back? Every passage must be long enough, and heard clearly.
        for (pass in again) {
            val required = pass.filter { !phones[it.first].optional }
            val clear = required.count { rows[it.second * width + phones[it.first].sound.ordinal + 1] > CLEAR }
            if (required.size < ENOUGH_SOUNDS || clear < required.size * CLEAR_SHARE) return null
        }
        for (s in u + 1 until v) starts[s] = -1
        for ((sound, row) in once) if (sound < v) starts[sound] = row
        return again
    }

    private const val NEVER = -1e30f
    private const val UNREACHABLE = -1e29f

    /** A sound or a row scoring below this (about one chance in ten) wasn't heard clearly where it was put. */
    private const val CLEAR = -2.3f

    /** A run of sounds each heard clearly in its place is trusted from this length, and is as sure as can be at that one. */
    private const val SHORTEST_RUN = 6
    private const val SURE_RUN = 30

    /** About how many rows are placed again at once. */
    private const val STRETCH = 1500

    /** How many words he may have gone back before the doubtful stretch, and carried on past it the first time. */
    private const val BACK = 60
    private const val AHEAD = 25

    /** What going back costs, against simply reciting on: it must explain a good deal to be chosen. Tried in turn. */
    private val JUMPS = floatArrayOf(-12f, -30f, -60f)

    /** How many sounds that may be left out can be passed over together. */
    private const val SKIP = 8

    /** A passage said again is believed when it has at least this many sounds, and this share of them heard clearly. */
    private const val ENOUGH_SOUNDS = 6
    private const val CLEAR_SHARE = 0.6f

    /** The most (rows × positions) a doubtful stretch is placed again with: beyond it, it is left alone. */
    private const val CELLS = 8_000_000L
}

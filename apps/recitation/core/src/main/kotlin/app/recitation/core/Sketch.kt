package app.recitation.core

import kotlin.math.min
import kotlin.math.sqrt

/**
 * A quick look over a recording, before any of it has been listened to, that says
 * roughly where each verse begins: enough to go to a verse, and to show which verse is
 * being played, while the words wait to be placed.
 *
 * It needs no speech model. A reciter pauses at the end of a verse, and often inside one;
 * a verse lasts about as long as its share of the surah's sounds. So the pauses are found
 * from how loud the recording is, and each verse's end is put on the pause that leaves
 * every verse closest to its expected length, longer pauses being the likelier ends. Where
 * a reciter runs verses together there is no pause to find, and the end is put where the
 * lengths say.
 */
object Sketch {

    /** How loud the recording is, every 20 ms. */
    fun levels(source: Samples, stillWanted: () -> Boolean = { true }): FloatArray? {
        var levels = FloatArray(1 shl 16)
        var count = 0
        var sum = 0.0
        var held = 0
        while (true) {
            val block = source.read() ?: break
            for (sample in block) {
                sum += sample * sample
                if (++held == FRAME) {
                    if (count == levels.size) levels = levels.copyOf(count * 2)
                    levels[count++] = sqrt(sum / FRAME).toFloat()
                    sum = 0.0
                    held = 0
                }
            }
            if (!stillWanted()) return null
        }
        return levels.copyOf(count)
    }

    /** The pauses in a recording, in ms, from its [levels]. */
    fun pauses(levels: FloatArray): List<IntRange> {
        if (levels.isEmpty()) return emptyList()
        val sorted = levels.sortedArray()
        val loud = sorted[(sorted.size * 0.90f).toInt().coerceAtMost(sorted.lastIndex)]
        val floor = sorted[(sorted.size * 0.02f).toInt()]
        // Well under the reciter's voice; and in a hall that is never silent, not far above its hum.
        val quiet = maxOf(0.06f * loud, floor + 0.15f * (loud - floor))
        val out = ArrayList<IntRange>()
        var i = 0
        while (i < levels.size) {
            if (levels[i] >= quiet) {
                i++
                continue
            }
            var j = i
            while (j < levels.size && levels[j] < quiet) j++
            if ((j - i) * FRAME_MS >= SHORTEST_PAUSE_MS) out += i * FRAME_MS until j * FRAME_MS
            i = j
        }
        return out
    }

    /**
     * Where each stretch of a recording begins, in ms: [weights] says how long each is
     * expected to be against the others (its number of sounds), [pauses] where the reciter
     * pauses, and [totalMs] how long the recording is.
     */
    fun starts(weights: IntArray, pauses: List<IntRange>, totalMs: Int): IntArray {
        val n = weights.size
        if (n <= 1) return IntArray(n)
        val silent = pauses.sumOf { it.last + 1 - it.first }
        val speech = (totalMs - silent).coerceAtLeast(1).toDouble()
        val rate = speech / weights.sum().coerceAtLeast(1)
        // Places an end can be put, by how much has been recited before them: each pause, and
        // for each end the place the lengths alone would put it.
        class Place(val recited: Double, val ms: Int, val bonus: Double)
        val places = ArrayList<Place>()
        var gone = 0
        for (pause in pauses) {
            val length = pause.last + 1 - pause.first
            places += Place((pause.first - gone).toDouble(), pause.last + 1, -0.5 * min(length, LONGEST_BONUS_MS))
            gone += length
        }
        fun clock(recited: Double): Int {
            var ms = recited
            for (pause in pauses) if (pause.first <= ms) ms += pause.last + 1 - pause.first else break
            return ms.toInt().coerceAtMost(totalMs)
        }
        val want = DoubleArray(n - 1)
        var before = 0.0
        for (b in 0 until n - 1) {
            before += weights[b] * rate
            want[b] = before
            places += Place(before, clock(before), NO_PAUSE_COST)
        }
        places.sortBy { it.recited }
        val count = places.size
        val best = Array(n - 1) { DoubleArray(count) { Double.MAX_VALUE } }
        val back = Array(n - 1) { IntArray(count) { -1 } }
        for (b in 0 until n - 1) {
            val expected = (weights[b] * rate).coerceAtLeast(1.0)
            for (c in 0 until count) {
                val place = places[c]
                if (place.recited < want[b] - REACH_MS || place.recited > want[b] + REACH_MS) continue
                if (b == 0) {
                    val off = place.recited - expected
                    best[0][c] = off * off / expected + place.bonus
                    continue
                }
                var p = c - 1
                while (p >= 0 && place.recited - places[p].recited <= 6 * expected + 20_000) {
                    if (best[b - 1][p] < Double.MAX_VALUE) {
                        val off = place.recited - places[p].recited - expected
                        val cost = best[b - 1][p] + off * off / expected + place.bonus
                        if (cost < best[b][c]) {
                            best[b][c] = cost
                            back[b][c] = p
                        }
                    }
                    p--
                }
            }
        }
        // The last stretch runs to the end of the recording.
        val lastExpected = (weights[n - 1] * rate).coerceAtLeast(1.0)
        var end = -1
        var least = Double.MAX_VALUE
        for (c in 0 until count) {
            if (best[n - 2][c] == Double.MAX_VALUE) continue
            val off = speech - places[c].recited - lastExpected
            val cost = best[n - 2][c] + off * off / lastExpected
            if (cost < least) {
                least = cost
                end = c
            }
        }
        val starts = IntArray(n)
        if (end < 0) {
            // Nothing fitted: by the lengths alone.
            for (b in 0 until n - 1) starts[b + 1] = clock(want[b])
            return starts
        }
        for (b in n - 2 downTo 0) {
            starts[b + 1] = places[end].ms
            end = back[b][end]
            if (end < 0 && b > 0) {
                for (earlier in 0 until b) starts[earlier + 1] = clock(want[earlier])
                break
            }
        }
        return starts
    }

    private const val FRAME = 320
    private const val FRAME_MS = 20

    /** Anything shorter is a breath inside a word, or a stopped letter. */
    private const val SHORTEST_PAUSE_MS = 300

    /** A pause counts for more the longer it is, up to this. */
    private const val LONGEST_BONUS_MS = 2500

    /** What putting an end where there is no pause costs, against putting it on one. */
    private const val NO_PAUSE_COST = 400.0

    /** How far from where the lengths put it an end is looked for. */
    private const val REACH_MS = 90_000
}

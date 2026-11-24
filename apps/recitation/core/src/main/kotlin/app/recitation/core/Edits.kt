package app.recitation.core

/**
 * How one run of symbols differs from another, as the fewest changes that turn the first
 * into the second: stretches that are the same, stretches replaced, stretches left out
 * and stretches added. This is what lines up the sounds a reciter was heard to make with
 * the sounds the verse asks for.
 */
object Edits {
    enum class Kind { SAME, REPLACED, LEFT_OUT, ADDED }

    /** [kind] of change: [from] until [until] in the first run answers to [toFrom] until [toUntil] in the second. */
    data class Change(val kind: Kind, val from: Int, val until: Int, val toFrom: Int, val toUntil: Int)

    /** The fewest single changes (a symbol replaced, left out or added) between [a] and [b]. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1, prev[j] + 1, cur[j - 1] + 1)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /**
     * How far into [whole] a reciter has got who has so far said [said]: how many of
     * [whole]'s symbols the start of it that fits [said] best takes in. Ties go to the
     * longer start: a sound just begun counts as reached.
     */
    fun reach(whole: String, said: String): Int {
        if (said.isEmpty()) return 0
        // The fewest changes between every start of [whole] and all of [said]: one column of the table at a time.
        var prev = IntArray(whole.length + 1) { it }
        var cur = IntArray(whole.length + 1)
        for (j in 1..said.length) {
            cur[0] = j
            for (i in 1..whole.length) {
                cur[i] = minOf(prev[i - 1] + if (whole[i - 1] == said[j - 1]) 0 else 1, prev[i] + 1, cur[i - 1] + 1)
            }
            val t = prev; prev = cur; cur = t
        }
        var best = 0
        for (i in 1..whole.length) if (prev[i] <= prev[best]) best = i
        return best
    }

    /** The changes from [a] to [b], in order, covering both runs whole; neighbouring changes of one kind are one change. */
    fun between(a: String, b: String): List<Change> {
        val n = a.length
        val m = b.length
        val cost = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) cost[i][0] = i
        for (j in 0..m) cost[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            cost[i][j] = minOf(cost[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1, cost[i - 1][j] + 1, cost[i][j - 1] + 1)
        }
        // Walked back from the end the way the Levenshtein library the rule engine was written
        // with walks it (rapidfuzz), so that where several ways are equally short the same one
        // is taken: a symbol left out first, then one added, then one kept or replaced.
        val steps = ArrayList<Kind>()
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            when {
                cost[i][j] == cost[i - 1][j] + 1 -> { steps += Kind.LEFT_OUT; i-- }
                j > 1 && cost[i][j - 1] == cost[i - 1][j - 1] - 1 -> { steps += Kind.ADDED; j-- }
                else -> { steps += if (a[i - 1] == b[j - 1]) Kind.SAME else Kind.REPLACED; i--; j-- }
            }
        }
        while (i > 0) { steps += Kind.LEFT_OUT; i-- }
        while (j > 0) { steps += Kind.ADDED; j-- }
        steps.reverse()
        val out = ArrayList<Change>()
        var x = 0
        var y = 0
        var k = 0
        while (k < steps.size) {
            val kind = steps[k]
            val x0 = x
            val y0 = y
            while (k < steps.size && steps[k] == kind) {
                if (kind != Kind.ADDED) x++
                if (kind != Kind.LEFT_OUT) y++
                k++
            }
            out += Change(kind, x0, x, y0, y)
        }
        return out
    }
}

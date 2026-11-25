package app.recitation.speech

import android.content.Context
import app.recitation.data.Settings
import java.util.Locale

/**
 * How the speech engine is run: on how many threads, and whether those keep spinning while
 * they wait for their next piece of work (quicker to take it up, but the phone runs hotter,
 * and a hot phone slows itself down). Which is fastest differs from phone to phone, so the
 * app finds out by trying, once, before the first recording is listened to.
 *
 * Fastest isn't all: the listening shares the phone with whatever else is going on. So it
 * is never tried on more threads than half the phone's cores, and of the ways tried the
 * one with the fewest threads is taken unless more are clearly faster.
 */
data class Tuning(val threads: Int, val spin: Boolean) {

    override fun toString() = "$threads threads" + if (spin) ", spinning" else ""

    companion object {
        /** What is used until anything else has been tried, and when nothing else can be. */
        val USUAL = Tuning(2, spin = false)

        /** How many threads are worth trying on a phone with [cores] processor cores: up to half of them. */
        fun threadsToTry(cores: Int): List<Int> = listOf(2, 4, 6).filter { it <= maxOf(USUAL.threads, cores / 2) }

        /**
         * Finds the way to run it by trying. [time] runs a set piece of sound one way and
         * says how long it took, in seconds, or null if the engine wouldn't run that way.
         * [note] is told what was found.
         *
         * Not spinning is taken unless it is clearly slower: what it saves in heat shows
         * over an hour of listening, not in a trial of a few seconds. And more threads are
         * taken only if they are clearly faster than fewer.
         */
        fun find(cores: Int, time: (Tuning) -> Float?, note: (String) -> Unit = {}): Tuning {
            val took = LinkedHashMap<Tuning, Float>()
            fun tried(way: Tuning): Float? = time(way)?.also { took[way] = it }
            val spinning = tried(USUAL.copy(spin = true))
            val calm = tried(USUAL)
            val spin = calm == null || (spinning != null && calm > spinning * SLOWER_BY)
            for (threads in threadsToTry(cores)) if (threads != USUAL.threads) tried(Tuning(threads, spin))
            val ways = took.filterKeys { it.spin == spin }
            val fastest = ways.values.minOrNull()
            val best = if (fastest == null) USUAL else ways.filterValues { it <= fastest * NEARLY }.keys.minBy { it.threads }
            note("engine tried on $cores cores: " + took.entries.joinToString("; ") { "${it.key} ${String.format(Locale.US, "%.2f", it.value)} s" } + " -> $best")
            return best
        }

        /** What was found on this phone for the engine now in use; null if it hasn't been tried yet. */
        fun kept(context: Context): Tuning? {
            val parts = Settings.tuning(context)?.split(',') ?: return null
            if (parts.size != 3 || parts[2] != FOR) return null
            return Tuning(parts[0].toIntOrNull() ?: return null, parts[1] == "spin")
        }

        fun keep(context: Context, tuning: Tuning) =
            Settings.setTuning(context, "${tuning.threads},${if (tuning.spin) "spin" else "calm"},$FOR")

        /** What a trial holds for: the engine and the model it was made with. Another of either is tried afresh. */
        private const val FOR = EngineStore.VERSION + "/tajweed-v2/gentle"

        /** How much slower than the fastest a way with fewer threads may be and still be taken. */
        private const val NEARLY = 1.25f

        /** How much slower not spinning may be and still be taken. */
        private const val SLOWER_BY = 1.1f
    }
}

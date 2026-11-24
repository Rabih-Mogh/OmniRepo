package app.recitation.core

import kotlin.math.sqrt

/**
 * Where a reciter fell silent: to take a breath, or to stop on a word. A word stopped on
 * sounds differently from the same word recited on, and a reciter who stops rightly must
 * not be corrected for it, so the silences are what tells a stop from a mistake (see
 * [Expected.recited]).
 *
 * Silent is taken loosely: in a room that echoes, or a recording of one, the sound never
 * dies away between two breaths, it only drops. So a stretch counts when it stays under
 * [QUIET] of the loud level of the recitation for [LEAST_SECONDS].
 */
object Silences {
    /** The share of the recitation's loud level under which a moment counts as silent. */
    private const val QUIET = 0.3

    /** How long the silence has to last: a letter held closed is shorter than this, a breath is not. */
    private const val LEAST_SECONDS = 0.3

    /** Loudness is taken every 20 ms. */
    private const val FRAME = 320

    /** The silences in [sound] (16,000 samples a second), in seconds: where the first began, where it ended, the second's, and so on. */
    fun of(sound: FloatArray): FloatArray {
        val frames = sound.size / FRAME
        if (frames == 0) return FloatArray(0)
        val loudness = DoubleArray(frames) { f ->
            var sum = 0.0
            for (i in f * FRAME until (f + 1) * FRAME) sum += sound[i].toDouble() * sound[i]
            sqrt(sum / FRAME)
        }
        // The loud level: louder than nine tenths of the recitation, so a single shout or knock doesn't set it.
        val loud = loudness.sortedArray()[(0.9 * (frames - 1)).toInt()]
        val seconds = FRAME / Mel.SAMPLE_RATE.toDouble()
        val out = ArrayList<Float>()
        var t = 0
        while (t < frames) {
            if (loudness[t] < QUIET * loud) {
                var u = t
                while (u < frames && loudness[u] < QUIET * loud) u++
                if ((u - t) * seconds >= LEAST_SECONDS) { out += (t * seconds).toFloat(); out += (u * seconds).toFloat() }
                t = u
            } else t++
        }
        return out.toFloatArray()
    }

    /**
     * Where the silences of [sound] fall in what was [heard] of it: for each, how many of
     * the sounds heard had begun by the middle of it. The silence before the first sound
     * and the one after the last are left out.
     */
    fun among(heard: HeardScript, sound: FloatArray): IntArray = among(heard.times, of(sound))

    /** The same from the [times] of the sounds heard (see [HeardScript.times]) and the [silences] as [of] gives them. */
    fun among(times: FloatArray, silences: FloatArray): IntArray {
        val sounds = times.size - 1
        val out = ArrayList<Int>()
        for (s in 0 until silences.size / 2) {
            val middle = (silences[2 * s] + silences[2 * s + 1]) / 2
            var begun = 0
            for (i in 0 until sounds) if (times[i] < middle) begun++
            if (begun in 1 until sounds && begun !in out) out += begun
        }
        return out.toIntArray()
    }
}

package app.recitation.core

import kotlin.math.sqrt

/**
 * Times how long a reader holds a sound, for practising the length of a madd: sound is
 * handed in as it comes from the microphone, and each stretch of voice between two
 * silences is a hold. A madd is counted in harakat, the time it takes to close or open a
 * finger; how long that is the reader sets, and the hold is so many of them.
 *
 * Voice is told from silence by loudness alone, against the loudest the reader has been:
 * it begins once the sound has been well up for three looks running, and ends once it has
 * been well down for eight. A hold too short to be a vowel held on purpose isn't kept.
 */
class Holding(private val rate: Int = Mel.SAMPLE_RATE) {
    /** How long the hold under way has lasted, in seconds; 0 between holds. */
    var seconds = 0f
        private set

    /** The holds finished so far, in seconds, oldest first. */
    val holds: List<Float> get() = done

    private val done = ArrayList<Float>()
    private val frame = rate / 50 // 20 ms
    private var pending = FloatArray(0)
    private var loudest = 0f
    private var up = 0
    private var down = 0
    private var held = 0 // frames of the hold under way, as far as the last one with voice in it

    /** Takes in [block] of sound. */
    fun add(block: FloatArray) {
        pending += block
        var at = 0
        while (at + frame <= pending.size) {
            var sum = 0.0
            for (i in at until at + frame) sum += pending[i] * pending[i]
            look(sqrt(sum / frame).toFloat())
            at += frame
        }
        pending = pending.copyOfRange(at, pending.size)
    }

    private fun look(level: Float) {
        // The loudest so far, forgotten slowly, so one cough doesn't deafen it for good.
        loudest = maxOf(level, loudest * FORGET)
        val on = maxOf(FLOOR, loudest * VOICE)
        if (seconds == 0f) {
            up = if (level >= on) up + 1 else 0
            if (up >= BEGINS) {
                held = up
                seconds = held * SECONDS_A_FRAME
                down = 0
            }
            return
        }
        if (level >= on * STILL) {
            held += down + 1
            down = 0
            seconds = held * SECONDS_A_FRAME
        } else if (++down >= ENDS) {
            if (seconds >= SHORTEST) done += seconds
            seconds = 0f
            up = 0
            down = 0
            held = 0
        }
    }

    companion object {
        private const val SECONDS_A_FRAME = 0.02f
        private const val FLOOR = 0.01f
        private const val VOICE = 0.3f
        private const val STILL = 0.6f
        private const val FORGET = 0.9995f
        private const val BEGINS = 3
        private const val ENDS = 8
        private const val SHORTEST = 0.25f

        /** How many counts a hold of [seconds] is, when one count takes [pace] seconds. */
        fun counts(seconds: Float, pace: Float): Float = if (pace <= 0f) 0f else seconds / pace
    }
}

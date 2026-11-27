package app.recitation.core

import kotlin.math.sqrt

/**
 * Tells when a reader reciting a verse to the phone is done, so that what was recited is
 * gone over without their having to say so: they have fallen silent after reciting, or
 * nothing that could be a voice has come at all.
 *
 * It goes by loudness alone, taken every 20 ms and judged against the take itself, as
 * [Silences] does: a moment is quiet when it is under [QUIET] of the take's loud level.
 * A voice has been heard once the loud level stands well clear of the take's quiet level
 * ([CLEAR] times it) for long enough. In a room so noisy that a voice doesn't stand clear
 * it never says the reader is done: then the reader says so, as before.
 */
class Ending {
    private val loudness = ArrayList<Float>()
    private var sum = 0.0
    private var filled = 0

    /** How much has been listened to, in seconds. */
    val seconds: Float get() = loudness.size * FRAME_SECONDS

    /** Takes in the next [block] of sound (16,000 samples a second). */
    fun add(block: FloatArray) {
        for (sample in block) {
            sum += sample.toDouble() * sample
            if (++filled == FRAME) {
                loudness += sqrt(sum / FRAME).toFloat()
                sum = 0.0
                filled = 0
            }
        }
    }

    /**
     * Whether the reader is done. After a voice has been heard: once it has been quiet for
     * [AT_THE_END] seconds if the last word of the verse has been reached ([atEnd]), and
     * for [MID_VERSE] seconds otherwise, which leaves room for a breath, and for a stop
     * made on purpose in the middle of a verse. With no voice heard: after [NOTHING]
     * seconds, unless words of the verse have been made out all the same ([found]), which
     * says a voice is there though it doesn't stand clear of the noise.
     */
    fun over(atEnd: Boolean, found: Boolean): Boolean {
        val frames = loudness.size
        if (frames < LEAST_FRAMES) return false
        val sorted = loudness.toFloatArray().also { it.sort() }
        val loud = sorted[(0.9f * (frames - 1)).toInt()]
        val low = sorted[(0.1f * (frames - 1)).toInt()]
        val quiet = QUIET * loud
        var voiced = 0
        for (level in loudness) if (level >= quiet) voiced++
        val spoken = loud > 0f && loud >= CLEAR * low && voiced * FRAME_SECONDS >= VOICE_SECONDS
        if (!spoken) return !found && seconds >= NOTHING
        var silent = 0
        while (silent < frames && loudness[frames - 1 - silent] < quiet) silent++
        return silent * FRAME_SECONDS >= if (atEnd) AT_THE_END else MID_VERSE
    }

    companion object {
        /** Loudness is taken every 20 ms. */
        private const val FRAME = 320
        private const val FRAME_SECONDS = FRAME / 16_000f

        /** Nothing is said of a take shorter than this many frames: half a second. */
        private const val LEAST_FRAMES = 25

        /** The share of the take's loud level under which a moment counts as quiet. */
        private const val QUIET = 0.3f

        /** How many times the take's quiet level its loud level has to be for a voice to have been heard: about ten decibels. */
        private const val CLEAR = 3f

        /** How much of the take has to be voice. */
        private const val VOICE_SECONDS = 0.4f

        /** How long the quiet lasts before the reader is taken to be done, at the verse's last word and before it. */
        const val AT_THE_END = 1.0f
        const val MID_VERSE = 2.5f

        /** How long is waited for a voice before giving up on one. */
        const val NOTHING = 8f
    }
}

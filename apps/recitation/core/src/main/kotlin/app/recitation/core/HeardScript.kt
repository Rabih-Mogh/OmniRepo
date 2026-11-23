package app.recitation.core

import kotlin.math.exp

/**
 * A recitation as the tajweed model heard it, written in the Quran phonetic script: one
 * symbol for every sound, a long vowel written once for every count it was held, and
 * signs of their own for a hidden nun (ں), a nun turned to a mim (۾) and the echo of a
 * qalqalah (ڇ).
 *
 * [symbols] is the script, a character a sound. [sureness] is how sure the model was of
 * each (0..1). [times] says when each began, in seconds from the start of the sound, and
 * has one more entry than there are symbols: when the last one ended.
 */
class HeardScript(val symbols: String, val sureness: FloatArray, val times: FloatArray) {

    companion object {
        /** The script's symbols in the model's own order; the model's first output, before these, is "nothing here". */
        const val ALPHABET = "ءبتثجحخدذرزسشصضطظعغفقكلمنهوياۦۥَُِ۪ـٲڇں۾ؙۜ"

        /** The model gives a row of scores every 20 ms. */
        const val FRAME_SECONDS = 0.02f

        /** The silence put before and after the sound the model is given, as its training clips had: 0.4 s. */
        const val LEAD_SAMPLES = 6_400

        /**
         * Reads the model's answer: [scores] holds, for each of [frames] frames, the log of
         * the chance of "nothing" and then of each symbol. The likeliest at each frame is
         * taken, a symbol held over several frames counts once, and "nothing" is dropped.
         */
        fun read(scores: FloatArray, frames: Int): HeardScript {
            val width = ALPHABET.length + 1
            require(scores.size >= frames * width) { "too few scores" }
            val symbols = StringBuilder()
            val sureness = ArrayList<Float>()
            val times = ArrayList<Float>()
            val lead = LEAD_SAMPLES / Mel.SAMPLE_RATE.toFloat()
            var previous = 0
            var lastSounding = -1
            for (t in 0 until frames) {
                var best = 0
                for (v in 1 until width) if (scores[t * width + v] > scores[t * width + best]) best = v
                if (best != 0) lastSounding = t
                if (best != previous && best != 0) {
                    symbols.append(ALPHABET[best - 1])
                    sureness += exp(scores[t * width + best])
                    times += (t + 0.5f) * FRAME_SECONDS - lead
                }
                previous = best
            }
            times += if (lastSounding >= 0) (lastSounding + 1) * FRAME_SECONDS - lead else 0f
            return HeardScript(symbols.toString(), sureness.toFloatArray(), times.toFloatArray())
        }
    }
}

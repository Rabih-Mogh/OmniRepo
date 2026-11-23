package app.recitation.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The picture of sound the tajweed model listens to: Whisper's log-mel spectrogram. Every
 * 10 ms of 16 kHz sound becomes 80 numbers, the loudness in 80 bands of pitch spaced the
 * way the ear hears them.
 *
 * It has to be the very spectrogram the model was trained on, so this follows Whisper's
 * own recipe step by step: a 25 ms Hann window every 10 ms over the sound mirrored at
 * both ends, the power of each of 201 frequencies, 80 triangular bands on the Slaney mel
 * scale, the logarithm, nothing more than 8 below the loudest value, then (x + 4) / 4.
 */
object Mel {
    const val SAMPLE_RATE = 16_000
    const val BANDS = 80
    private const val WINDOW = 400
    private const val HOP = 160
    private const val BINS = WINDOW / 2 + 1

    private val hann = FloatArray(WINDOW) { (0.5 - 0.5 * cos(2 * PI * it / WINDOW)).toFloat() }
    private val cosines = FloatArray(WINDOW) { cos(2 * PI * it / WINDOW).toFloat() }
    private val sines = FloatArray(WINDOW) { sin(2 * PI * it / WINDOW).toFloat() }

    // Each band as the bins it takes in and how much of each: a triangle, so only a few bins.
    private val bandFrom = IntArray(BANDS)
    private val bandWeights: Array<FloatArray>

    init {
        fun toMel(hz: Double) = if (hz >= 1000.0) 15.0 + ln(hz / 1000.0) / (ln(6.4) / 27.0) else hz / (200.0 / 3)
        fun toHz(mel: Double) = if (mel >= 15.0) 1000.0 * exp((ln(6.4) / 27.0) * (mel - 15.0)) else mel * (200.0 / 3)
        val top = toMel(SAMPLE_RATE / 2.0)
        val points = DoubleArray(BANDS + 2) { toHz(top * it / (BANDS + 1)) }
        bandWeights = Array(BANDS) { band ->
            val lo = points[band]
            val mid = points[band + 1]
            val hi = points[band + 2]
            val weights = DoubleArray(BINS) { bin ->
                val hz = bin * SAMPLE_RATE.toDouble() / WINDOW
                max(0.0, min((hz - lo) / (mid - lo), (hi - hz) / (hi - mid))) * (2.0 / (hi - lo))
            }
            val first = weights.indexOfFirst { it > 0.0 }.coerceAtLeast(0)
            val last = weights.indexOfLast { it > 0.0 }.coerceAtLeast(first)
            bandFrom[band] = first
            FloatArray(last - first + 1) { weights[first + it].toFloat() }
        }
    }

    /** How many 10 ms frames [samples] of sound give. */
    fun frames(samples: Int): Int = samples / HOP

    /**
     * The spectrogram of [audio], band after band: band b's value at frame t is at
     * `b * frames + t`, which is the order the model takes it in.
     */
    fun of(audio: FloatArray): FloatArray {
        val frames = frames(audio.size)
        val out = FloatArray(BANDS * frames)
        if (frames == 0) return out
        val half = WINDOW / 2
        // The sound mirrored at each end, as Whisper pads it.
        fun at(i: Int): Float {
            val n = audio.size
            if (n == 1) return audio[0]
            var k = i
            while (k < 0 || k >= n) k = if (k < 0) -k else 2 * (n - 1) - k
            return audio[k]
        }
        val windowed = FloatArray(WINDOW)
        val power = FloatArray(BINS)
        var loudest = Float.NEGATIVE_INFINITY
        for (t in 0 until frames) {
            val start = t * HOP - half
            for (i in 0 until WINDOW) windowed[i] = at(start + i) * hann[i]
            for (bin in 0 until BINS) {
                var re = 0f
                var im = 0f
                var angle = 0
                for (i in 0 until WINDOW) {
                    re += windowed[i] * cosines[angle]
                    im -= windowed[i] * sines[angle]
                    angle += bin
                    if (angle >= WINDOW) angle -= WINDOW
                }
                power[bin] = re * re + im * im
            }
            for (band in 0 until BANDS) {
                val weights = bandWeights[band]
                val from = bandFrom[band]
                var sum = 0f
                for (k in weights.indices) sum += weights[k] * power[from + k]
                val value = log10(max(sum, 1e-10f))
                out[band * frames + t] = value
                if (value > loudest) loudest = value
            }
        }
        val floor = loudest - 8f
        for (i in out.indices) out[i] = (max(out[i], floor) + 4f) / 4f
        return out
    }
}

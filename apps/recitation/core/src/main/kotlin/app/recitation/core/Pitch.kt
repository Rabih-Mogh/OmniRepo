package app.recitation.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * How high a voice is at one moment: the YIN method (de Cheveigné and Kawahara, 2002).
 * A steady voice repeats itself every period, so a stretch of sound is compared with
 * itself moved along by every lag a voice could have; the first lag at which the two
 * all but agree is the period. Taking the first such lag, not the best of all, is what
 * keeps it from answering an octave low.
 */
object Pitch {
    const val SAMPLE_RATE = 16_000

    /** The sound looked at each time: 64 ms, enough for two periods of the lowest voice. */
    const val FRAME = 1024

    /** How far the look moves on each time: 16 ms. */
    const val HOP = 256

    private const val LOWEST_HZ = 65f
    private const val HIGHEST_HZ = 700f
    private val shortest = (SAMPLE_RATE / HIGHEST_HZ).toInt()
    private val longest = (SAMPLE_RATE / LOWEST_HZ).toInt()
    private val compared = FRAME - longest

    /** How nearly a stretch must agree with itself moved along for the lag to be taken as its period. */
    private const val AGREES = 0.12f

    /** Quieter than this (root mean square, of a full scale of 1) is taken for silence. */
    private const val SILENCE = 0.004f

    /**
     * The pitch, in Hz, of the [FRAME] samples of [sound] from [from]; 0 where there is no
     * steady pitch to hear: silence, breath, a consonant.
     */
    fun at(sound: FloatArray, from: Int = 0): Float {
        if (from + FRAME > sound.size) return 0f
        if (loudness(sound, from, FRAME) < SILENCE) return 0f
        // How unlike itself the stretch is at each lag, set against the average so far.
        val unlike = FloatArray(longest + 1)
        var running = 0f
        var found = -1
        for (lag in 1..longest) {
            var sum = 0f
            for (i in 0 until compared) {
                val d = sound[from + i] - sound[from + i + lag]
                sum += d * d
            }
            running += sum
            unlike[lag] = if (running > 0f) sum * lag / running else 1f
            if (found < 0 && lag > shortest && unlike[lag - 1] < AGREES && unlike[lag] >= unlike[lag - 1]) {
                found = lag - 1
                break
            }
        }
        if (found < shortest) return 0f
        // The dip's true bottom lies between whole lags: a parabola through it and its neighbours.
        val a = unlike[found - 1]
        val b = unlike[found]
        val c = unlike[found + 1]
        val bend = a - 2 * b + c
        val lag = if (bend > 0f) found + 0.5f * (a - c) / bend else found.toFloat()
        return SAMPLE_RATE / lag
    }

    /** How loud [count] samples from [from] are: their root mean square. */
    fun loudness(sound: FloatArray, from: Int, count: Int): Float {
        var sum = 0.0
        for (i in from until minOf(sound.size, from + count)) sum += sound[i] * sound[i]
        return sqrt(sum / count).toFloat()
    }

    /** How far [hz] is above [from], in cents: a hundredth of a semitone, 1200 to the octave. */
    fun cents(hz: Float, from: Float): Float = (1200.0 * ln((hz / from).toDouble()) / ln(2.0)).toFloat()

    /** [hz] as a note counted in semitones from the A of 440 Hz, fractions and all. */
    fun semitones(hz: Float): Float = cents(hz, 440f) / 100f
}

/**
 * What a held or recited sound amounts to, from its pitch at every look (0 where there was
 * none) and its loudness there: for the voice exercises.
 */
class VoiceTake(private val pitches: FloatArray, private val loudness: FloatArray) {

    private val voiced: List<Float> = pitches.filter { it > 0f }

    /** How long there was a voice to hear, in seconds. */
    val seconds: Float get() = voiced.size * Pitch.HOP / Pitch.SAMPLE_RATE.toFloat()

    /** The longest unbroken stretch of voice, in seconds: gaps of up to a fifth of a second don't break it. */
    val longestBreath: Float
        get() {
            val gap = (0.2f * Pitch.SAMPLE_RATE / Pitch.HOP).toInt()
            var best = 0
            var run = 0
            var silent = 0
            for (p in pitches) {
                if (p > 0f) { run += silent + 1; silent = 0 } else if (run > 0) {
                    if (++silent > gap) { best = maxOf(best, run); run = 0; silent = 0 }
                }
            }
            return maxOf(best, run) * Pitch.HOP / Pitch.SAMPLE_RATE.toFloat()
        }

    /** The middle pitch of it, in Hz; 0 when no voice was heard. */
    val middle: Float get() = if (voiced.isEmpty()) 0f else voiced.sorted()[voiced.size / 2]

    /**
     * How steadily a single note was held: how far, in cents, the voice strayed from its
     * middle as a rule (the spread about the middle, with the first and last tenth left
     * out, where a note is found and let go). Under 20 is very steady; a semitone is 100.
     */
    val wavering: Float
        get() {
            if (voiced.size < 10) return 0f
            val body = voiced.subList(voiced.size / 10, voiced.size - voiced.size / 10)
            val mid = body.sorted()[body.size / 2]
            return sqrt(body.sumOf { val c = Pitch.cents(it, mid).toDouble(); c * c } / body.size).toFloat()
        }

    /** The lowest and highest notes reached, in Hz, leaving out the stray twentieth at each end. */
    val range: Pair<Float, Float>
        get() {
            if (voiced.isEmpty()) return 0f to 0f
            val sorted = voiced.sorted()
            return sorted[sorted.size / 20] to sorted[sorted.size - 1 - sorted.size / 20]
        }

    /** That range in semitones. */
    val span: Float get() = range.let { (low, high) -> if (low > 0f) Pitch.cents(high, low) / 100f else 0f }

    /** How even the loudness was while there was voice: its spread in decibels. Under 3 is even. */
    val unevenness: Float
        get() {
            val db = pitches.indices.filter { pitches[it] > 0f && loudness[it] > 0f }.map { 20 * kotlin.math.log10(loudness[it].toDouble()) }
            if (db.size < 5) return 0f
            val mean = db.average()
            return sqrt(db.sumOf { (it - mean) * (it - mean) } / db.size).toFloat()
        }

    companion object {
        /** Reads a whole stretch of sound: its pitch and loudness every [Pitch.HOP] samples, short jumps of an octave smoothed away. */
        fun of(sound: FloatArray): VoiceTake {
            val looks = if (sound.size < Pitch.FRAME) 0 else (sound.size - Pitch.FRAME) / Pitch.HOP + 1
            val raw = FloatArray(looks) { Pitch.at(sound, it * Pitch.HOP) }
            val loud = FloatArray(looks) { Pitch.loudness(sound, it * Pitch.HOP, Pitch.FRAME) }
            return VoiceTake(smoothed(raw), loud)
        }

        /** Each pitch replaced by the middle one of it and its two neighbours either side, among those that have a voice: a lone wrong octave goes. */
        fun smoothed(raw: FloatArray): FloatArray = FloatArray(raw.size) { i ->
            if (raw[i] <= 0f) 0f else {
                val near = (maxOf(0, i - 2)..minOf(raw.size - 1, i + 2)).map { raw[it] }.filter { it > 0f }.sorted()
                if (near.size >= 3 && abs(Pitch.cents(raw[i], near[near.size / 2])) > 500f) near[near.size / 2] else raw[i]
            }
        }
    }
}

package app.spark.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

private const val C4 = 261.63
private const val E4 = 329.63
private const val C5 = 523.25
private const val E5 = 659.25
private const val G5 = 783.99
private const val A5 = 880.0
private const val C6 = 1046.5
private const val E6 = 1318.5
private const val G6 = 1568.0
private const val C7 = 2093.0

/** One struck note: when it starts and how long it rings, in milliseconds, and how loud next to the others. */
private class Note(val at: Int, val hz: Double, val lasts: Int, val loud: Double = 1.0, val to: Double = hz)

/**
 * The app's sounds. Each is a few bell-like notes worked out from sine waves, so no sound
 * file ships with the app.
 */
enum class Chime(private val notes: List<Note>) {
    /** A light touch: an answer picked, a like. A quick blip that slides up. */
    TAP(listOf(Note(0, 660.0, 70, 0.45, to = 990.0))),

    /** A puzzle's answer shown: one note. */
    REVEAL(listOf(Note(0, A5, 280, 0.6))),

    /** A right answer: two notes going up. */
    RIGHT(listOf(Note(0, G5, 260), Note(90, C6, 400))),

    /** A wrong answer: two low notes going down, soft. */
    WRONG(listOf(Note(0, E4, 180, 0.6), Note(110, C4, 320, 0.6))),

    /** A lesson finished: a chord climbed a note at a time. */
    FINISH(listOf(Note(0, C5, 300), Note(90, E5, 300), Note(180, G5, 300), Note(270, C6, 650))),

    /** The day's goal reached: the same climb an octave up, left to ring. */
    GOAL(listOf(Note(0, G5, 400), Note(100, C6, 400), Note(200, E6, 450), Note(300, G6, 600), Note(420, C7, 800, 0.5)));

    /** The sound as samples, [RATE] to the second. It starts and ends on silence. */
    fun samples(): ShortArray {
        val mix = DoubleArray(notes.maxOf { it.at + it.lasts } * RATE / 1000)
        for (note in notes) {
            val from = note.at * RATE / 1000
            val length = note.lasts * RATE / 1000
            var phase = 0.0
            for (i in 0 until length) {
                val along = i / length.toDouble()
                phase += 2 * PI * (note.hz + (note.to - note.hz) * along) / RATE
                // A bell: struck at once, dying away, its overtones dying sooner.
                val struck = minOf(1.0, i / EDGE)
                val closing = minOf(1.0, (length - 1 - i) / EDGE)
                val tone = sin(phase) + 0.3 * sin(2 * phase) * exp(-10 * along) + 0.1 * sin(3 * phase) * exp(-14 * along)
                mix[from + i] += note.loud * struck * closing * exp(-5 * along) * tone
            }
        }
        val scale = LOUDEST * notes.maxOf { it.loud } / mix.maxOf(::abs) * Short.MAX_VALUE
        return ShortArray(mix.size) { (mix[it] * scale).toInt().toShort() }
    }

    /** The sound as a WAV file. */
    fun wav(): ByteArray {
        val samples = samples()
        val out = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + samples.size * 2).put("WAVE".toByteArray(Charsets.US_ASCII))
        // One channel of 16-bit samples, uncompressed.
        out.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        out.put("data".toByteArray(Charsets.US_ASCII)).putInt(samples.size * 2)
        samples.forEach(out::putShort)
        return out.array()
    }

    companion object {
        const val RATE = 22050

        /** The loudest any sound gets, as a share of what the samples can hold. */
        const val LOUDEST = 0.8

        // The samples a note takes to come in and to go out, so it doesn't click.
        private const val EDGE = RATE * 4 / 1000.0
    }
}

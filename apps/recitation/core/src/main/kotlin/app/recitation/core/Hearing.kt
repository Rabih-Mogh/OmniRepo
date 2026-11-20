package app.recitation.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.sqrt

/** A recording as 16 kHz mono samples (-1..1), read a block at a time. */
interface Samples {
    /** Length of the recording in samples; 0 if unknown. */
    val total: Long

    /** How far into the recording the next block from [read] starts. */
    val position: Long

    /** Jumps to [sample] or a little before it; [position] says where once the next block is read. */
    fun seekTo(sample: Long)

    /** The next block, or null at the end. */
    fun read(): FloatArray?
}

/**
 * A stretch of a recording, from sample [from] up to [until], as a recording of its own:
 * for listening again to some verses only. What is heard of it is timed from its start.
 */
class Span(private val whole: Samples, private val from: Long, until: Long) : Samples {
    private val until = if (whole.total > 0) minOf(until, whole.total) else until
    private var started = false

    override val total: Long get() = (until - from).coerceAtLeast(0)
    override val position: Long get() = whole.position - from

    override fun seekTo(sample: Long) {
        whole.seekTo(from + sample)
        started = true
    }

    override fun read(): FloatArray? {
        if (!started) {
            if (from > 0) whole.seekTo(from)
            started = true
        }
        if (whole.position >= until) return null
        val block = whole.read() ?: return null
        val over = whole.position - until
        return if (over > 0) block.copyOf((block.size - over).toInt().coerceAtLeast(0)) else block
    }
}

/** The speech model: takes a stretch of sound and gives, for each 20 ms frame of it, a score per symbol. */
fun interface SpeechModel {
    fun run(samples: FloatArray): FloatArray
}

/** What the model heard in a recording: for every frame that wasn't a pause, its number and a row of scores. */
class Heard(val frames: IntArray, val rows: FloatArray)

/**
 * Runs the speech model over a whole recording and keeps, for each frame that isn't a
 * pause, one score per Arabic sound (see [SoundColumns]).
 *
 * The model takes a few seconds of sound at a time, so the recording goes through in
 * 10-second pieces, each with a second of the sound around it for context. Every finished
 * piece is appended to a file, so listening that is cut short (the phone restarts, Android
 * stops the app) carries on from the last piece instead of starting the recording again.
 */
class Hearing(private val model: SpeechModel, vocab: List<String>) {

    private val columns = SoundColumns(vocab)
    private val symbols = vocab.size
    private val width = SoundColumns.WIDTH

    /** How many samples a millisecond of a recording is. */
    val samplesPerMs: Int get() = SAMPLE_RATE / 1000

    /**
     * Listens to [source], keeping its work in [notes]. [onPiece] is handed each piece's kept
     * frames as soon as it is heard. [onProgress] gets 0..1. [stillWanted]
     * is asked between pieces; when it says no the listening stops, with what was heard so
     * far kept, and null is returned.
     */
    fun listen(
        source: Samples,
        notes: File,
        stillWanted: () -> Boolean = { true },
        onPiece: (frames: IntArray, rows: FloatArray) -> Unit = { _, _ -> },
        onProgress: (Float) -> Unit = {},
    ): Heard? {
        var piece = piecesIn(notes)
        val total = source.total.coerceAtLeast(1)
        // `window` holds the samples from `windowStart` on. A piece needs its own ten seconds and a second either side.
        var window = FloatArray(PIECE + 2 * CONTEXT + SAMPLE_RATE)
        var held = 0
        var windowStart = maxOf(0L, piece.toLong() * PIECE - CONTEXT)
        if (windowStart > 0) source.seekTo(windowStart)
        var lined = false // whether the first block has been lined up with the window
        var skip = 0L
        var ended = false
        DataOutputStream(FileOutputStream(notes, true).buffered(1 shl 16)).use { out ->
            while (true) {
                val bodyStart = piece.toLong() * PIECE
                val need = (bodyStart + PIECE + CONTEXT - windowStart).toInt()
                while (!ended && held < need) {
                    val block = source.read()
                    if (block == null) {
                        ended = true
                        break
                    }
                    if (!lined) {
                        // A seek lands on or before the sample asked for: drop what comes before
                        // the window. If it somehow landed after, the window starts there instead.
                        val landed = source.position - block.size
                        if (landed > windowStart) windowStart = landed else skip = windowStart - landed
                        lined = true
                    }
                    val from = minOf(skip, block.size.toLong()).toInt()
                    skip -= from
                    val n = block.size - from
                    if (held + n > window.size) window = window.copyOf(held + n + SAMPLE_RATE)
                    System.arraycopy(block, from, window, held, n)
                    held += n
                }
                val bodyOffset = (bodyStart - windowStart).toInt().coerceAtLeast(0)
                if (bodyOffset >= held) break // the recording ended before this piece
                val bodySamples = minOf(PIECE, held - bodyOffset)
                val length = minOf(held, need)
                if (length >= MIN_SAMPLES) write(out, piece, window, length, bodyOffset, bodySamples, windowStart, onPiece)
                out.flush()
                piece++
                onProgress(((bodyStart + bodySamples).toFloat() / total).coerceIn(0f, 1f))
                if (ended && bodyOffset + PIECE >= held) break
                if (!stillWanted()) return null
                // Keep the last second before the next piece as its context; drop the rest.
                val drop = (piece.toLong() * PIECE - CONTEXT - windowStart).toInt()
                if (drop > 0) {
                    System.arraycopy(window, drop, window, 0, held - drop)
                    held -= drop
                    windowStart += drop
                }
            }
        }
        return read(notes)
    }

    /** Runs the model on window[0 until length] and appends the kept frames of the piece's own ten seconds. */
    private fun write(
        out: DataOutputStream,
        piece: Int,
        window: FloatArray,
        length: Int,
        bodyOffset: Int,
        bodySamples: Int,
        windowStart: Long,
        onPiece: (IntArray, FloatArray) -> Unit,
    ) {
        val logits = model.run(normalized(window, length))
        val frames = logits.size / symbols
        val first = bodyOffset / HOP
        val last = minOf(frames, first + bodySamples / HOP)
        val firstFrame = (windowStart / HOP).toInt()
        val row = FloatArray(width)
        val kept = ArrayList<Int>()
        val rows = ArrayList<FloatArray>()
        for (f in first until last) {
            if (columns.reduce(logits, f * symbols, row, 0) < SoundColumns.PAUSE) {
                kept += firstFrame + f
                rows += row.copyOf()
            }
        }
        out.writeInt(piece)
        out.writeInt(kept.size)
        for (i in kept.indices) {
            out.writeInt(kept[i])
            for (v in rows[i]) out.writeFloat(v)
        }
        val flat = FloatArray(kept.size * width)
        for (i in kept.indices) System.arraycopy(rows[i], 0, flat, i * width, width)
        onPiece(kept.toIntArray(), flat)
    }

    /** What an earlier, unfinished listening left in [notes], ready to carry on from: whole pieces only. */
    fun resume(notes: File): Heard {
        piecesIn(notes)
        return if (notes.exists()) read(notes) else Heard(IntArray(0), FloatArray(0))
    }

    /** Zero mean, unit variance, as the model was trained on. */
    private fun normalized(audio: FloatArray, length: Int): FloatArray {
        var sum = 0.0
        for (i in 0 until length) sum += audio[i]
        val mean = (sum / length).toFloat()
        var v = 0.0
        for (i in 0 until length) v += (audio[i] - mean) * (audio[i] - mean)
        val std = sqrt(v / length + 1e-7).toFloat()
        return FloatArray(length) { (audio[it] - mean) / std }
    }

    /** How many whole pieces [notes] holds. A piece cut off mid-write is trimmed away. */
    private fun piecesIn(notes: File): Int {
        if (!notes.exists()) return 0
        var pieces = 0
        var good = 0L
        DataInputStream(notes.inputStream().buffered(1 shl 16)).use { input ->
            try {
                while (true) {
                    val piece = input.readInt()
                    val kept = input.readInt()
                    if (piece != pieces || kept < 0) break
                    val bytes = kept * (4 + 4 * width)
                    input.readFully(ByteArray(bytes))
                    good += 8 + bytes
                    pieces++
                }
            } catch (_: EOFException) {
                // The end, or a piece that was being written when the app stopped.
            }
        }
        if (good < notes.length()) RandomAccessFile(notes, "rw").use { it.setLength(good) }
        return pieces
    }

    companion object {
        const val SAMPLE_RATE = 16_000

        /** The model turns every 320 samples (20 ms) into one frame. */
        private const val HOP = 320
        private const val PIECE = 10 * SAMPLE_RATE
        private const val CONTEXT = SAMPLE_RATE
        private const val MIN_SAMPLES = 4_000

        /** Everything heard so far, from the file [listen] keeps. */
        fun read(notes: File): Heard {
            val width = SoundColumns.WIDTH
            val frames = ArrayList<Int>()
            val rows = ArrayList<FloatArray>()
            DataInputStream(notes.inputStream().buffered(1 shl 16)).use { input ->
                try {
                    while (true) {
                        input.readInt() // the piece's number
                        repeat(input.readInt()) {
                            frames += input.readInt()
                            rows += FloatArray(width) { input.readFloat() }
                        }
                    }
                } catch (_: EOFException) {
                    // Read to the end.
                }
            }
            val count = minOf(frames.size, rows.size)
            val data = FloatArray(count * width)
            for (i in 0 until count) System.arraycopy(rows[i], 0, data, i * width, width)
            return Heard(IntArray(count) { frames[it] }, data)
        }
    }
}

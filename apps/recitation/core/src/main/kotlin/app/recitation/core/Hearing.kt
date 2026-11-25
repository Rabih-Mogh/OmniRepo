package app.recitation.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

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
    private var jumped = false // the whole recording's position is that of before a jump until it is next read

    override val total: Long get() = (until - from).coerceAtLeast(0)
    override val position: Long get() = whole.position - from

    override fun seekTo(sample: Long) {
        whole.seekTo(from + sample)
        started = true
        jumped = true
    }

    override fun read(): FloatArray? {
        if (!started) {
            if (from > 0) {
                whole.seekTo(from)
                jumped = true
            }
            started = true
        }
        if (!jumped && whole.position >= until) return null
        jumped = false
        val block = whole.read() ?: return null
        val over = whole.position - until
        if (over >= block.size) return null
        return if (over > 0) block.copyOf((block.size - over).toInt()) else block
    }
}

/**
 * The speech model: takes a stretch of sound (16,000 samples a second, between -1 and 1)
 * and gives, for each 20 ms of it from its start, [SoundColumns.SYMBOLS] scores.
 */
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
 * 10-second pieces, each with half a second of the sound either side of it for context. Every finished
 * piece is appended to a file, so listening that is cut short (the phone restarts, Android
 * stops the app) carries on from the last piece instead of starting the recording again.
 */
class Hearing(private val model: SpeechModel) {

    private val columns = SoundColumns()
    private val symbols = SoundColumns.SYMBOLS
    private val width = SoundColumns.WIDTH

    /** How many samples a millisecond of a recording is. */
    val samplesPerMs: Int get() = SAMPLE_RATE / 1000

    /**
     * Listens to [source], keeping its work in [notes]. The recording is heard in ten-second
     * pieces, numbered from 0. [onPiece] is handed each piece's number and kept frames as
     * soon as it is heard. [onProgress] gets 0..1. [stillWanted] is asked between pieces;
     * when it says no the listening stops, with what was heard so far kept, and null is
     * returned.
     *
     * The pieces needn't be heard in order. Before each one, [next] is asked which piece it
     * would like, given which are done; it may name any piece not yet heard, so the
     * listening can go to where the recording is being played. When it has no wish (null,
     * or a piece that is done or past the end) the earliest piece not yet heard is taken.
     */
    fun listen(
        source: Samples,
        notes: File,
        stillWanted: () -> Boolean = { true },
        onPiece: (piece: Int, frames: IntArray, rows: FloatArray) -> Unit = { _, _, _ -> },
        onProgress: (Float) -> Unit = {},
        next: (done: Set<Int>) -> Int? = { null },
    ): Heard? {
        val done = HashSet(piecesIn(notes))
        // How many pieces there are, if the recording's length is known; the end is found for sure by reaching it.
        val expected = if (source.total > 0) ((source.total + PIECE - 1) / PIECE).toInt() else 0
        var end = Int.MAX_VALUE // the first piece found to lie past the end of the recording
        // `window` holds the samples from `windowStart` on. A piece needs its own ten seconds and half a second either side.
        var window = FloatArray(PIECE + 2 * CONTEXT + SAMPLE_RATE)
        var held = 0
        var windowStart = 0L
        var lined = false // whether the first block after a jump has been lined up with the window
        var skip = 0L
        var ended = false
        var reading = false // whether the source has been read from since the last jump
        DataOutputStream(FileOutputStream(notes, true).buffered(1 shl 16)).use { out ->
            while (true) {
                var earliest = 0
                while (earliest in done) earliest++
                if (earliest >= end) break
                val wish = next(done)?.takeIf { it >= 0 && it !in done && it < end && (expected == 0 || it < expected) }
                val piece = wish ?: earliest
                val bodyStart = piece.toLong() * PIECE
                val from = maxOf(0L, bodyStart - CONTEXT)
                if (reading && from >= windowStart && from <= windowStart + held && !ended) {
                    // Carrying straight on: keep the last second before the piece as its context; drop the rest.
                    val drop = (from - windowStart).toInt()
                    if (drop > 0) {
                        System.arraycopy(window, drop, window, 0, held - drop)
                        held -= drop
                        windowStart += drop
                    }
                } else {
                    // Somewhere else in the recording: start the window afresh there.
                    held = 0
                    windowStart = from
                    lined = false
                    skip = 0
                    ended = false
                    if (reading || from > 0) source.seekTo(from)
                    reading = true
                }
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
                    val start = minOf(skip, block.size.toLong()).toInt()
                    skip -= start
                    val n = block.size - start
                    if (held + n > window.size) window = window.copyOf(held + n + SAMPLE_RATE)
                    System.arraycopy(block, start, window, held, n)
                    held += n
                }
                val bodyOffset = (bodyStart - windowStart).toInt().coerceAtLeast(0)
                if (bodyOffset >= held) {
                    // The recording ended before this piece.
                    end = minOf(end, piece)
                    continue
                }
                val bodySamples = minOf(PIECE, held - bodyOffset)
                val length = minOf(held, need)
                if (length >= MIN_SAMPLES) write(out, piece, window, length, bodyOffset, bodySamples, windowStart, onPiece)
                out.flush()
                done += piece
                if (ended && bodyOffset + PIECE >= held) end = minOf(end, piece + 1)
                val all = if (end != Int.MAX_VALUE) end else maxOf(expected, done.size + 1)
                onProgress((done.count { it < all }.toFloat() / all).coerceIn(0f, 1f))
                if (!stillWanted()) return null
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
        onPiece: (Int, IntArray, FloatArray) -> Unit,
    ) {
        val logits = model.run(if (length == window.size) window else window.copyOf(length))
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
        onPiece(piece, kept.toIntArray(), flat)
    }

    /** What an earlier, unfinished listening left in [notes], ready to carry on from: whole pieces only, by their numbers. */
    fun resume(notes: File): Map<Int, Heard> {
        piecesIn(notes)
        return if (notes.exists()) pieces(notes) else emptyMap()
    }

    /** Which whole pieces [notes] holds. A piece cut off mid-write is trimmed away. */
    private fun piecesIn(notes: File): Set<Int> {
        if (!notes.exists()) return emptySet()
        val pieces = HashSet<Int>()
        var good = 0L
        DataInputStream(notes.inputStream().buffered(1 shl 16)).use { input ->
            try {
                while (true) {
                    val piece = input.readInt()
                    val kept = input.readInt()
                    if (piece < 0 || kept < 0 || kept > PIECE / HOP) break
                    val bytes = kept * (4 + 4 * width)
                    input.readFully(ByteArray(bytes))
                    good += 8 + bytes
                    pieces += piece
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

        /** How long each piece the recording is heard in lasts. */
        const val PIECE_MS = 10_000

        /** The model turns every 320 samples (20 ms) into one frame. */
        private const val HOP = 320
        private const val PIECE = 10 * SAMPLE_RATE

        /**
         * How much sound either side of a piece the model is given with it: half a second, a
         * whole number of its frames. It was a second; on five real recordings the words came
         * out as near the published timings with half (the same median in each, and 517 of
         * 1,943 words more than 300 ms off against 516), and the model has 11 seconds to get
         * through for every ten instead of 12.
         */
        private const val CONTEXT = SAMPLE_RATE / 2
        private const val MIN_SAMPLES = 4_000

        /** What was heard of each piece, from the file [listen] keeps, by the pieces' numbers. */
        fun pieces(notes: File): Map<Int, Heard> {
            val width = SoundColumns.WIDTH
            val pieces = java.util.TreeMap<Int, Heard>()
            DataInputStream(notes.inputStream().buffered(1 shl 16)).use { input ->
                try {
                    while (true) {
                        val piece = input.readInt()
                        val kept = input.readInt()
                        val frames = IntArray(kept)
                        val rows = FloatArray(kept * width)
                        for (i in 0 until kept) {
                            frames[i] = input.readInt()
                            for (c in 0 until width) rows[i * width + c] = input.readFloat()
                        }
                        pieces.putIfAbsent(piece, Heard(frames, rows))
                    }
                } catch (_: EOFException) {
                    // Read to the end.
                }
            }
            return pieces
        }

        /** Everything heard so far, in the recording's own order, whatever order it was heard in. */
        fun read(notes: File): Heard = joined(pieces(notes).values)

        /** Pieces put end to end. */
        fun joined(pieces: Collection<Heard>): Heard {
            val width = SoundColumns.WIDTH
            val count = pieces.sumOf { it.frames.size }
            val frames = IntArray(count)
            val rows = FloatArray(count * width)
            var at = 0
            for (piece in pieces) {
                System.arraycopy(piece.frames, 0, frames, at, piece.frames.size)
                System.arraycopy(piece.rows, 0, rows, at * width, piece.rows.size)
                at += piece.frames.size
            }
            return Heard(frames, rows)
        }
    }
}

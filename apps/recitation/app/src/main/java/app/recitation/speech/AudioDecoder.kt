package app.recitation.speech

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import app.recitation.core.Hearing
import app.recitation.core.Samples
import java.io.File
import java.nio.ByteOrder

private const val SAMPLE_RATE = Hearing.SAMPLE_RATE

/**
 * Reads a downloaded recording as 16 kHz mono samples, the form the speech model takes,
 * a block at a time so a two-hour surah never has to fit in memory.
 */
class AudioDecoder private constructor(open: (MediaExtractor) -> Unit) : Samples, AutoCloseable {

    constructor(file: File) : this({ it.setDataSource(file.path) })

    /** A recording read from wherever [source] has it, such as one still arriving (see [Arriving]). */
    constructor(source: MediaDataSource) : this({ it.setDataSource(source) })

    private val extractor = MediaExtractor()
    private val codec: MediaCodec
    private val info = MediaCodec.BufferInfo()
    private var inputDone = false
    private var outputDone = false
    private var sourceRate: Int
    private var channels: Int
    private var float = false

    // Resampling carries on across blocks: where the next output sample falls between two source samples.
    private var phase = 0.0
    private var lastSample = 0f

    override val total: Long

    override var position = 0L
        private set
    private var positioned = false

    init {
        open(extractor)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
        } ?: error("No sound in this file")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        total = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) * SAMPLE_RATE / 1_000_000 else 0
        codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
    }

    override fun seekTo(sample: Long) {
        extractor.seekTo(sample * 1_000_000 / SAMPLE_RATE, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        codec.flush()
        inputDone = false
        outputDone = false
        positioned = false
        phase = 0.0
    }

    override fun read(): FloatArray? {
        while (!outputDone) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val index = codec.dequeueOutputBuffer(info, 10_000)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // What the decoder really produces; it can differ from what the file's header says.
                val out = codec.outputFormat
                sourceRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                float = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            } else if (index >= 0) {
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                var block: FloatArray? = null
                if (info.size > 0) {
                    if (!positioned) {
                        position = info.presentationTimeUs * SAMPLE_RATE / 1_000_000
                        positioned = true
                    }
                    val buffer = codec.getOutputBuffer(index)!!.order(ByteOrder.nativeOrder())
                    buffer.limit(info.offset + info.size).position(info.offset)
                    block = resample(mono(buffer))
                    position += block.size
                }
                codec.releaseOutputBuffer(index, false)
                if (block != null && block.isNotEmpty()) return block
            }
        }
        return null
    }

    private fun mono(buffer: java.nio.ByteBuffer): FloatArray {
        if (float) {
            val samples = buffer.asFloatBuffer()
            return FloatArray(samples.remaining() / channels) { var sum = 0f; repeat(channels) { sum += samples.get() }; sum / channels }
        }
        val samples = buffer.asShortBuffer()
        return FloatArray(samples.remaining() / channels) { var sum = 0; repeat(channels) { sum += samples.get() }; sum / (32768f * channels) }
    }

    /** To 16 kHz, by drawing a straight line between neighbouring samples: plenty for speech. */
    private fun resample(input: FloatArray): FloatArray {
        if (sourceRate == SAMPLE_RATE || input.isEmpty()) return input
        val step = sourceRate.toDouble() / SAMPLE_RATE
        val out = FloatArray(((input.size - phase) / step).toInt().coerceAtLeast(0) + 1)
        var n = 0
        // `phase` counts from the last sample of the block before, which sits at -1 here.
        while (phase < input.size) {
            val i = phase.toInt()
            val a = if (i == 0) lastSample else input[i - 1]
            val b = input[i]
            out[n++] = a + (b - a) * (phase - i).toFloat()
            phase += step
        }
        phase -= input.size
        lastSample = input.last()
        return if (n == out.size) out else out.copyOf(n)
    }

    override fun close() {
        runCatching { codec.stop() }
        codec.release()
        extractor.release()
    }
}

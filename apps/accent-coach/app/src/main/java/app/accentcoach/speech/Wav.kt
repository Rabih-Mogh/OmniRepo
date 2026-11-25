package app.accentcoach.speech

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads the WAV files the phone's voice writes, into what the recogniser takes. */
object Wav {

    /**
     * The sound in a 16-bit PCM WAV file as mono samples in -1..1 at [SAMPLE_RATE],
     * whatever rate and number of channels it was written at. Null if [bytes] isn't such
     * a file.
     */
    fun read(bytes: ByteArray): FloatArray? {
        if (bytes.size < 12 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var channels = 0
        var rate = 0
        var at = 12
        while (at + 8 <= bytes.size) {
            val id = String(bytes, at, 4, Charsets.US_ASCII)
            val size = buf.getInt(at + 4)
            val body = at + 8
            when (id) {
                "fmt " -> {
                    if (body + 16 > bytes.size) return null
                    val pcm = buf.getShort(body).toInt() == 1
                    channels = buf.getShort(body + 2).toInt()
                    rate = buf.getInt(body + 4)
                    val bits = buf.getShort(body + 14).toInt()
                    if (!pcm || bits != 16 || channels < 1 || rate < 1) return null
                }
                "data" -> {
                    if (channels == 0) return null
                    // A length that wasn't filled in, or runs past the end, means "the rest of the file".
                    val length = if (size <= 0 || body + size > bytes.size) bytes.size - body else size
                    val frames = length / (2 * channels)
                    val mono = FloatArray(frames) { f ->
                        var sum = 0
                        for (c in 0 until channels) sum += buf.getShort(body + (f * channels + c) * 2)
                        sum / (32768f * channels)
                    }
                    return resample(mono, rate)
                }
            }
            if (size < 0) return null
            at = body + size + (size and 1) // chunks are padded to an even length
        }
        return null
    }

    /** [audio] at [rate] brought to [SAMPLE_RATE], each new sample a mix of the two it falls between. */
    internal fun resample(audio: FloatArray, rate: Int): FloatArray {
        if (rate == SAMPLE_RATE || audio.isEmpty()) return audio
        val step = rate.toDouble() / SAMPLE_RATE
        return FloatArray((audio.size / step).toInt()) { i ->
            val pos = i * step
            val a = pos.toInt()
            val b = minOf(a + 1, audio.lastIndex)
            val t = (pos - a).toFloat()
            audio[a] * (1 - t) + audio[b] * t
        }
    }
}

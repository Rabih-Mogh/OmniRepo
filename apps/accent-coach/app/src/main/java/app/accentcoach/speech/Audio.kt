package app.accentcoach.speech

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.concurrent.thread
import kotlin.math.abs

const val SAMPLE_RATE = 16_000

/**
 * Records 16 kHz mono from the microphone on its own thread, up to [maxSeconds].
 * [snapshot] can be read at any time for live feedback while recording.
 */
class Recorder(private val maxSeconds: Int = 12) {

    private val samples = FloatArray(SAMPLE_RATE * maxSeconds)
    @Volatile private var count = 0
    @Volatile private var running = false
    private var worker: Thread? = null

    /** Loudness of the latest chunk, 0..1, for the level meter. */
    @Volatile var level = 0f
        private set

    val isRecording: Boolean get() = running

    /** Needs RECORD_AUDIO; the caller checks it. Calls [onLimit] if the time runs out. */
    @SuppressLint("MissingPermission")
    fun start(onLimit: () -> Unit) {
        if (running) return
        count = 0
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, SAMPLE_RATE / 5 * 2),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("The microphone couldn't start")
        }
        running = true
        record.startRecording()
        worker = thread(name = "recorder") {
            val chunk = ShortArray(SAMPLE_RATE / 20)
            try {
                while (running) {
                    val n = record.read(chunk, 0, chunk.size)
                    if (n <= 0) continue
                    var peak = 0
                    val room = minOf(n, samples.size - count)
                    for (i in 0 until room) {
                        samples[count + i] = chunk[i] / 32768f
                        peak = maxOf(peak, abs(chunk[i].toInt()))
                    }
                    count += room
                    level = peak / 32768f
                    if (count >= samples.size) {
                        running = false
                        onLimit()
                    }
                }
            } finally {
                record.stop()
                record.release()
                level = 0f
            }
        }
    }

    /** Stops and returns everything recorded. */
    fun stop(): FloatArray {
        running = false
        worker?.join(1_000)
        worker = null
        return snapshot()
    }

    fun snapshot(): FloatArray = samples.copyOf(count)
}

/** Plays back a recording made by [Recorder]. */
object Playback {
    private var track: AudioTrack? = null

    fun play(audio: FloatArray) {
        stop()
        if (audio.isEmpty()) return
        val pcm = ShortArray(audio.size) { (audio[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        t.write(pcm, 0, pcm.size)
        t.play()
        track = t
    }

    fun stop() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }
}

package app.recitation.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Listens through the phone's microphone: one channel, 16,000 samples a second, the form
 * the speech models and the pitch finder take. The sound is asked for as it is meant for
 * speech recognition, which phones hand over without evening out its loudness: a voice
 * swelling or fading has to be heard as it was.
 */
object Mic {
    const val SAMPLE_RATE = 16_000

    /** The microphone couldn't be opened: something else has it, or the phone refused. */
    class Unavailable : Exception()

    /**
     * The microphone's sound for as long as it is collected: a stretch at a time (about a
     * sixteenth of a second), as samples between -1 and 1. Letting go of it closes the
     * microphone. The caller must hold the permission to record.
     */
    @SuppressLint("MissingPermission")
    fun sound(): Flow<FloatArray> = flow {
        val least = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (least <= 0) throw Unavailable()
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(least, 8 * BLOCK))
        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) throw Unavailable()
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw Unavailable()
            val block = ShortArray(BLOCK)
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(block, 0, BLOCK)
                if (read < 0) throw Unavailable()
                if (read > 0) emit(FloatArray(read) { block[it] / 32768f })
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)

    private const val BLOCK = 1024
}

package app.recitation.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.recitation.core.Mel

/**
 * Plays back a stretch of what the reader recited to the phone, as the microphone took it:
 * a word they are told to go over, so they hear what the phone heard. The sound is the
 * one kept in memory since the verse was recited; nothing of it is written to the phone.
 * Where the phone gives no sound to play it with, it stays silent rather than fail.
 */
class TakePlayer {
    private var track: AudioTrack? = null

    /** Plays [sound] (16,000 samples a second) from [fromSeconds] until [untilSeconds]; what was playing stops. */
    fun play(sound: FloatArray, fromSeconds: Float, untilSeconds: Float) {
        stop()
        val from = (fromSeconds * Mel.SAMPLE_RATE).toInt().coerceIn(0, sound.size)
        val until = (untilSeconds * Mel.SAMPLE_RATE).toInt().coerceIn(from, sound.size)
        if (until - from < Mel.SAMPLE_RATE / 20) return
        runCatching {
            val samples = ShortArray(until - from) { (sound[from + it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(Mel.SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
                .also {
                    it.write(samples, 0, samples.size)
                    it.play()
                }
        }
    }

    fun stop() {
        runCatching { track?.release() }
        track = null
    }
}

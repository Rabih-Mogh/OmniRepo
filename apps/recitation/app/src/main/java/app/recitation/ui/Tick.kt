package app.recitation.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * A soft tick, for counting by: a short note that dies away at once. It is made here, not
 * kept as a file, and is played at the loudness the recitations are. Where the phone gives
 * no sound to play it with, it stays silent rather than fail.
 */
class Tick {
    private var track: AudioTrack? = null

    /** Plays the tick; one still sounding is cut off by it. */
    fun play() {
        runCatching {
            val ready = track ?: made().also { track = it }
            ready.stop()
            ready.reloadStaticData()
            ready.play()
        }
    }

    fun release() {
        runCatching { track?.release() }
        track = null
    }

    private fun made(): AudioTrack {
        // A note of 880 Hz that fades within 45 ms: heard, and not in the way of a voice.
        val sound = ShortArray(RATE * LENGTH_MS / 1000) { i ->
            val t = i.toDouble() / RATE
            (LOUDNESS * Short.MAX_VALUE * sin(2 * PI * PITCH * t) * exp(-t * FADE)).toInt().toShort()
        }
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(sound.size * 2)
            .build()
            .also { it.write(sound, 0, sound.size) }
    }

    private companion object {
        const val RATE = 44_100
        const val LENGTH_MS = 45
        const val PITCH = 880.0
        const val LOUDNESS = 0.3
        const val FADE = 90.0
    }
}

/** A [Tick] that is let go of when the screen that made it goes. */
@Composable
fun rememberTick(): Tick {
    val tick = remember { Tick() }
    DisposableEffect(Unit) { onDispose { tick.release() } }
    return tick
}

package app.spark.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import app.spark.core.Chime
import app.spark.data.Store
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * The app's little sounds. They are worked out when the app starts ([Chime]) and kept in
 * its cache, so none ships with it. They play at the media volume, and keep quiet while
 * the phone is on silent or vibrate and when the user has turned them off.
 */
object Sounds {
    private const val VOLUME = 0.7f

    private var opened = false
    internal var pool: SoundPool? = null
        private set
    private var audio: AudioManager? = null
    private val ids = ConcurrentHashMap<Chime, Int>()
    private val loaded: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** Gets the sounds ready, the first time it is called. Returns at once. */
    fun open(context: Context) {
        if (opened) return
        opened = true
        val app = context.applicationContext
        thread(name = "chimes") { load(app) }
    }

    internal fun load(context: Context) {
        opened = true
        pool?.release()
        ids.clear()
        loaded.clear()
        audio = context.getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val made = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
        made.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id }
        pool = made
        try {
            for (chime in Chime.entries) {
                val file = file(context, chime)
                file.parentFile?.mkdirs()
                file.writeBytes(chime.wav())
                ids[chime] = made.load(file.path, 1)
            }
        } catch (_: Exception) {
            // No room in the cache: the app goes without the sounds it couldn't write.
        }
    }

    internal fun file(context: Context, chime: Chime) = File(File(context.cacheDir, "chimes"), chime.name.lowercase() + ".wav")

    /** Plays [chime], unless sounds are off, the phone is on silent or vibrate, or it isn't ready yet. */
    fun play(chime: Chime) {
        if (!Store.kept.value.sounds) return
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = ids[chime]?.takeIf(loaded::contains) ?: return
        pool?.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }
}

/**
 * Gives a way to mark a moment: its sound and a touch from the vibration motor. The touch
 * follows the phone's own setting for touch feedback; both stop when sounds are turned off.
 */
@Composable
fun rememberFeel(): (Chime) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { chime ->
            Sounds.play(chime)
            if (Store.kept.value.sounds) view.performHapticFeedback(touch(chime))
        }
    }
}

private fun touch(chime: Chime): Int = when (chime) {
    Chime.TAP, Chime.REVEAL -> HapticFeedbackConstants.CLOCK_TICK
    Chime.WRONG -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    Chime.RIGHT, Chime.FINISH, Chime.GOAL -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
}

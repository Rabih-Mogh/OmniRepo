package app.fajralarm.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.StringRes
import app.fajralarm.R
import java.io.File

/** How a ringing alarm stopped, kept so the main screen can say what happened. */
enum class StopReason {
    /** The camera saw a sink. */
    SINK,
    SUM,
    BUTTON,
    NOTIFICATION,
    SNOOZED,
    /** Rang out with no answer, so it snoozed itself. */
    TIMED_OUT_SNOOZED,
    /** Rang out with no answer and gave up. */
    TIMED_OUT,
    STOPPED_BY_ANDROID,
}

/** The latest ringing: when it started, and how and when it stopped. */
data class LastAlarm(val rangAt: Long, val stoppedAt: Long, val reason: StopReason?)

/**
 * The "Last alarm" sentence for how the app's process ended after [since], from Android's
 * own record (Android 11+), or null if Android doesn't say. Used when an alarm left no
 * record of how it stopped.
 */
@StringRes
fun appExitSince(context: Context, since: Long): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val exit = runCatching {
        context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName, 0, 10)
            .filter { it.timestamp >= since }
            .minByOrNull { it.timestamp }
    }.getOrNull() ?: return null
    return when (exit.reason) {
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE -> R.string.last_exit_crash
        ApplicationExitInfo.REASON_ANR -> R.string.last_exit_froze
        ApplicationExitInfo.REASON_LOW_MEMORY -> R.string.last_exit_memory
        ApplicationExitInfo.REASON_USER_REQUESTED, ApplicationExitInfo.REASON_USER_STOPPED -> R.string.last_exit_force_stopped
        else -> null
    }
}

/** The frame the camera took for a sink the last time it turned the alarm off. */
object SinkProof {
    private fun file(context: Context) = File(context.filesDir, "last_sink.jpg")

    fun save(context: Context, frame: Bitmap) {
        runCatching { file(context).outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    fun path(context: Context): File? = file(context).takeIf { it.exists() }
}

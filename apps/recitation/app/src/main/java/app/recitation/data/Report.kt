package app.recitation.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.work.WorkManager
import app.recitation.prepare.PrepareWorker
import app.recitation.speech.EngineStore
import app.recitation.speech.TajweedStore
import app.recitation.speech.Tuning
import app.recitation.update.AppUpdate
import java.io.File

/**
 * What the app knows about how it is running on this phone, as text to copy and send to
 * whoever is looking into a problem: the phone, the room on it, the speech model and
 * engine, the queue and what failed in it, the background work, and the last lines of the
 * [Journal]. It is put together when asked for and leaves the phone only if the reader
 * pastes it somewhere.
 */
object Report {

    fun text(context: Context): String = buildString {
        val files = context.filesDir
        fun size(file: File): Long = if (file.isDirectory) file.listFiles().orEmpty().sumOf(::size) else file.length()
        fun mb(bytes: Long) = "${bytes / (1L shl 20)} MB"
        fun line(name: String, value: () -> Any?) {
            // One thing that can't be found out doesn't cost the rest of the report.
            append(name).append(": ").append(runCatching { value().toString() }.getOrElse { "? (${it.javaClass.simpleName})" }).append('\n')
        }

        line("Recitation") { AppUpdate.installedVersionName(context) }
        line("Phone") { "${Build.MANUFACTURER} ${Build.MODEL}" + if (Build.VERSION.SDK_INT >= 31) ", ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else "" }
        line("Android") { "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }
        line("Cores") { Runtime.getRuntime().availableProcessors() }
        line("Memory") {
            val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            "${mb(memory.totalMem)}, ${mb(memory.availMem)} free" + if (memory.lowMemory) ", low" else ""
        }
        line("Battery saver") {
            val power = context.getSystemService(PowerManager::class.java)
            (if (power.isPowerSaveMode) "on" else "off") + "; this app " + if (power.isIgnoringBatteryOptimizations(context.packageName)) "is exempt from battery limits" else "is under battery limits"
        }
        line("Storage") { "${mb(files.usableSpace)} free; sound ${mb(size(File(files, "audio")))}, heard ${mb(size(File(files, "heard")))}, model ${mb(size(File(files, "tajweed")))}, engine ${mb(size(File(files, "engine")))}" }
        line("Speech model") { if (TajweedStore(context).isReady) "on the phone" else "not on the phone" }
        line("Speech engine") { (if (EngineStore(context).isReady) "on the phone" else "not on the phone") + "; run on ${Tuning.kept(context) ?: "${Tuning.USUAL} (nothing else tried yet)"}" }
        line("Paused") { Settings.paused(context) }

        val tracks = Library.get(context).playlists.value.flatMap { it.tracks }
        line("Recordings") {
            "${tracks.size}: ${tracks.count { it.ready }} ready, ${tracks.count { it.playable && !it.ready }} playable, " +
                "${tracks.count { it.wanted }} in the queue, ${tracks.count { it.error != null }} failed"
        }
        for (track in tracks.filter { it.error != null }.take(MOST_FAILURES)) append("  failed, surah ${track.surah}: ${track.error}\n")

        for (name in listOf(PrepareWorker.WORK_NAME, PrepareWorker.WAITING_NAME)) {
            line("Work \"$name\"") {
                WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get().joinToString("; ") {
                    "${it.state}, attempt ${it.runAttemptCount}, stop reason ${it.stopReason}" + if (it.tags.contains(PrepareWorker.REFUSED_TAG)) ", waiting on YouTube" else ""
                }.ifEmpty { "none" }
            }
        }

        append("\nLately:\n")
        for (line in Journal.get(context).lines()) append(line).append('\n')
    }

    private const val MOST_FAILURES = 8
}

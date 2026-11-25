package app.accentcoach.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.accentcoach.core.Practice
import app.accentcoach.core.Streak
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDate

/** Keeps [Practice] in a small file on the phone, added to as recordings are scored. */
class Progress(private val file: File, private val today: () -> LocalDate = LocalDate::now) {

    var practice by mutableStateOf(load())
        private set

    val streak: Streak get() = practice.streak(today())

    /** Notes one more scored recording today. */
    fun recorded() {
        practice = practice.recorded(today())
        save()
    }

    /** Notes a finished drill of [lesson], [native] of its items native. */
    fun drilled(lesson: String, native: Int) {
        practice = practice.drilled(lesson, native)
        save()
    }

    private fun load(): Practice = if (!file.exists()) Practice() else try {
        val json = JSONObject(file.readText())
        val days = json.getJSONObject("days")
        val drills = json.getJSONObject("drills")
        Practice(
            days.keys().asSequence().associate { LocalDate.parse(it) to days.getInt(it) },
            drills.keys().asSequence().associateWith { drills.getInt(it) },
        )
    } catch (_: Exception) {
        // One that can't be read: start counting afresh, keeping it beside in case it can be mended.
        file.renameTo(File(file.path + ".unread"))
        Practice()
    }

    private fun save() {
        val json = JSONObject()
            .put("days", JSONObject().apply { for ((day, count) in practice.days) put(day.toString(), count) })
            .put("drills", JSONObject().apply { for ((lesson, native) in practice.drills) put(lesson, native) })
        // Written beside and moved into place, so a write cut short doesn't lose what was there.
        // With the storage full it can't be kept; the next recording tries again.
        try {
            val beside = File(file.path + ".new")
            beside.writeText(json.toString())
            if (!beside.renameTo(file)) throw IOException("couldn't replace ${file.name}")
        } catch (_: IOException) {
        }
    }
}

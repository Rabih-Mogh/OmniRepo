package app.recitation.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A few lines about what the background work did lately: when it started and stopped, what
 * went wrong, how fast it listened. It is kept in a small file on the phone and goes nowhere
 * by itself; it is there for the report the reader can copy from the menu (see [Report]),
 * since what happens on a phone can't otherwise be seen from anywhere else.
 */
class Journal(private val file: File, private val now: () -> LocalDateTime = LocalDateTime::now) {

    /** Adds a line, with the time. The oldest lines go once there are too many. */
    @Synchronized
    fun note(line: String) {
        try {
            file.parentFile?.mkdirs()
            if (file.length() > MOST_BYTES) {
                // Kept to its newer half, cut at a line's start.
                val text = file.readText()
                file.writeText(text.substring(text.indexOf('\n', text.length / 2) + 1))
            }
            file.appendText("${STAMP.format(now())} ${line.replace('\n', ' ').take(LONGEST)}\n")
        } catch (_: IOException) {
            // The phone is full, most likely. The work isn't held up for a note about it.
        }
    }

    /** The last [most] lines, oldest first. */
    @Synchronized
    fun lines(most: Int = 80): List<String> = try {
        if (file.exists()) file.readLines().takeLast(most) else emptyList()
    } catch (_: IOException) {
        emptyList()
    }

    companion object {
        private const val MOST_BYTES = 24_000L

        /** How much of a line is kept. */
        const val LONGEST = 300
        private val STAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

        @Volatile private var instance: Journal? = null

        fun get(context: Context): Journal =
            instance ?: synchronized(this) { instance ?: Journal(File(context.applicationContext.filesDir, "journal.txt")).also { instance = it } }
    }
}

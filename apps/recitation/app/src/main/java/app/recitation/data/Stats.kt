package app.recitation.data

import android.content.Context
import app.recitation.core.SURAHS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDate

/**
 * What has been listened to: how long on each day, and which verses of each surah
 * (numbered from 1) have been heard at least once.
 */
class Listening(val days: Map<LocalDate, Long> = emptyMap(), val heard: Map<Int, Set<Int>> = emptyMap()) {

    /** The figures the Stats tab shows, as they stand on [today]. */
    fun summary(today: LocalDate): Summary {
        val week = List(7) { today.minusDays((6 - it).toLong()) }.map { it to (days[it] ?: 0L) }
        // Days in a row with a minute or more of listening, up to today; a day not yet begun doesn't break the run.
        var streak = 0
        var day = if ((days[today] ?: 0L) >= A_MINUTE) today else today.minusDays(1)
        while ((days[day] ?: 0L) >= A_MINUTE) {
            streak++
            day = day.minusDays(1)
        }
        // The longest such run there has been.
        var best = 0
        var run = 0
        var before: LocalDate? = null
        for (listened in days.filter { it.value >= A_MINUTE }.keys.sorted()) {
            run = if (before != null && listened == before.plusDays(1)) run + 1 else 1
            best = maxOf(best, run)
            before = listened
        }
        return Summary(
            today = days[today] ?: 0L,
            week = week,
            allTime = days.values.sum(),
            streak = streak,
            best = best,
            versesHeard = heard.values.sumOf { it.size },
            surahsHeard = heard.count { (surah, verses) -> SURAHS.getOrNull(surah - 1)?.verses == verses.size },
        )
    }

    private companion object {
        const val A_MINUTE = Summary.A_DAYS_LISTENING
    }
}

/**
 * [week]: the last seven days, oldest first, each with its listening time in ms. [streak]
 * is how many days in a row have been listened on, up to today or yesterday, and [best]
 * the longest such run there has been.
 */
class Summary(val today: Long, val week: List<Pair<LocalDate, Long>>, val allTime: Long, val streak: Int, val versesHeard: Int, val surahsHeard: Int, val best: Int = streak) {
    val thisWeek: Long get() = week.sumOf { it.second }

    /** Whether today has been listened on: until it has, the run stands but hasn't grown. */
    val todayDone: Boolean get() = today >= A_DAYS_LISTENING

    companion object {
        /** How much listening makes a day count, in ms: a minute. */
        const val A_DAYS_LISTENING = 60_000L
    }
}

/** Keeps [Listening] in a small file on the phone, added to while a recitation plays. */
class Stats(private val file: File, private val today: () -> LocalDate = LocalDate::now) {

    private val _listening = MutableStateFlow(load())
    val listening: StateFlow<Listening> = _listening.asStateFlow()

    /** Notes [ms] more of listening today, and that [verses] of [surah] (counted from 0) have been heard. */
    @Synchronized
    fun listened(surah: Int, verses: IntRange, ms: Long) {
        val now = _listening.value
        val day = today()
        val heard = now.heard[surah].orEmpty() + verses.map { it + 1 }.filter { it in 1..(SURAHS.getOrNull(surah - 1)?.verses ?: 0) }
        _listening.value = Listening(now.days + (day to (now.days[day] ?: 0L) + ms), now.heard + (surah to heard))
        save()
    }

    private fun load(): Listening = if (!file.exists()) Listening() else try {
        val json = JSONObject(file.readText())
        val days = json.getJSONObject("days")
        val heard = json.getJSONObject("heard")
        Listening(
            days.keys().asSequence().associate { LocalDate.parse(it) to days.getLong(it) },
            heard.keys().asSequence().associate { it.toInt() to ranges(heard.getString(it)) },
        )
    } catch (_: Exception) {
        // One that can't be read: start counting afresh, but not by writing over it.
        file.setAside()
        Listening()
    }

    private fun save() {
        val now = _listening.value
        val json = JSONObject()
            .put("days", JSONObject().apply { for ((day, ms) in now.days) put(day.toString(), ms) })
            .put("heard", JSONObject().apply { for ((surah, verses) in now.heard) put(surah.toString(), written(verses)) })
        // With the storage full it can't be kept; the next note tries again. The recitation isn't stopped for it.
        try {
            file.writeWhole(json.toString())
        } catch (_: IOException) {
        }
    }

    companion object {
        /** Verse numbers as runs, to keep the file small: "1-7,9,12-20". */
        internal fun written(verses: Set<Int>): String {
            val sorted = verses.sorted()
            val runs = ArrayList<String>()
            var i = 0
            while (i < sorted.size) {
                var j = i
                while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
                runs += if (j > i) "${sorted[i]}-${sorted[j]}" else "${sorted[i]}"
                i = j + 1
            }
            return runs.joinToString(",")
        }

        internal fun ranges(written: String): Set<Int> = written.split(',').filter { it.isNotEmpty() }.flatMap { run ->
            val ends = run.split('-')
            (ends.first().toInt()..ends.last().toInt()).toList()
        }.toSet()

        @Volatile private var instance: Stats? = null

        fun get(context: Context): Stats =
            instance ?: synchronized(this) { instance ?: Stats(File(context.applicationContext.filesDir, "stats.json")).also { instance = it } }
    }
}

/** How much room the app's files take on the phone, in bytes. */
class Space(val recordings: Long, val engine: Long, val model: Long, val other: Long)

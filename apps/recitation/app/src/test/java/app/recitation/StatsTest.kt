package app.recitation

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recitation.data.Listening
import app.recitation.data.Stats
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** What is kept about listening, and the figures made from it. */
@RunWith(AndroidJUnit4::class)
class StatsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val friday = LocalDate.of(2026, 11, 20)

    @Test
    fun listeningAddsUpByDayAndVersesAreHeardOnce() {
        var today = friday
        val stats = Stats(File(folder.root, "stats.json")) { today }
        stats.listened(1, 0..2, 4_000)
        stats.listened(1, 2..3, 4_000)
        today = friday.plusDays(1)
        stats.listened(2, 254..254, 4_000)
        val now = stats.listening.value
        assertEquals(8_000L, now.days[friday])
        assertEquals(4_000L, now.days[friday.plusDays(1)])
        assertEquals(setOf(1, 2, 3, 4), now.heard[1])
        assertEquals(setOf(255), now.heard[2])
        // A verse number the surah doesn't have is not counted.
        stats.listened(1, 6..9, 0)
        assertEquals(setOf(1, 2, 3, 4, 7), stats.listening.value.heard[1])
    }

    @Test
    fun itIsStillThereNextTime() {
        val file = File(folder.root, "stats.json")
        Stats(file) { friday }.apply {
            listened(1, 0..6, 60_000)
            listened(18, 9..9, 30_000)
        }
        val again = Stats(file) { friday }.listening.value
        assertEquals(90_000L, again.days[friday])
        assertEquals((1..7).toSet(), again.heard[1])
        assertEquals(setOf(10), again.heard[18])
    }

    @Test
    fun versesAreWrittenAsRuns() {
        assertEquals("1-7,9,12-13", Stats.written(setOf(13, 1, 2, 3, 4, 5, 6, 7, 9, 12)))
        assertEquals(setOf(1, 2, 3, 9), Stats.ranges("1-3,9"))
        assertEquals(emptySet<Int>(), Stats.ranges(""))
    }

    @Test
    fun theFiguresForADay() {
        val minute = 60_000L
        val listening = Listening(
            days = mapOf(
                friday to 12 * minute, friday.minusDays(1) to 30 * minute, friday.minusDays(2) to 5 * minute,
                // A day missed, then an older day: not part of the run of days.
                friday.minusDays(4) to 40 * minute, friday.minusDays(20) to 60 * minute,
            ),
            heard = mapOf(1 to (1..7).toSet(), 2 to setOf(255, 256), 112 to (1..4).toSet()),
        )
        val summary = listening.summary(friday)
        assertEquals(12 * minute, summary.today)
        assertEquals(87 * minute, summary.thisWeek)
        assertEquals(147 * minute, summary.allTime)
        assertEquals(3, summary.streak)
        assertEquals(13, summary.versesHeard)
        // Al-Fatihah and Al-Ikhlas heard in full; Al-Baqarah only begun.
        assertEquals(2, summary.surahsHeard)
        assertEquals(listOf(0L, 0L, 40 * minute, 0L, 5 * minute, 30 * minute, 12 * minute), summary.week.map { it.second })
    }

    @Test
    fun aDayNotYetBegunDoesNotBreakTheRun() {
        val listening = Listening(days = mapOf(friday.minusDays(1) to 120_000L, friday.minusDays(2) to 90_000L))
        assertEquals(2, listening.summary(friday).streak)
        assertEquals(0, Listening().summary(friday).streak)
    }

    @Test
    fun theRunGrowsOnceTodayIsListenedOnAndTheLongestIsRemembered() {
        // Five days in a row a fortnight ago; then yesterday and the day before, and nothing yet today.
        val long = (10..14).associate { friday.minusDays(it.toLong()) to 300_000L }
        val listening = Listening(days = long + mapOf(friday.minusDays(1) to 120_000L, friday.minusDays(2) to 90_000L))
        val before = listening.summary(friday)
        assertEquals(listOf(2, 5), listOf(before.streak, before.best))
        assertEquals(false, before.todayDone)
        // Under a minute doesn't count; a minute does.
        assertEquals(false, Listening(listening.days + (friday to 40_000L)).summary(friday).todayDone)
        val after = Listening(listening.days + (friday to 61_000L)).summary(friday)
        assertEquals(listOf(3, 5), listOf(after.streak, after.best))
        assertEquals(true, after.todayDone)
        // A day missed, and it is a seed again; the longest stays.
        val missed = listening.summary(friday.plusDays(2))
        assertEquals(listOf(0, 5), listOf(missed.streak, missed.best))
    }
}

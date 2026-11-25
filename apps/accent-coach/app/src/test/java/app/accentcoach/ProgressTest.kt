package app.accentcoach

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.accentcoach.core.Streak
import app.accentcoach.data.Progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ProgressTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, "progress.json")
    private var today = LocalDate.of(2026, 11, 25)
    private fun progress() = Progress(file) { today }

    @Test
    fun whatWasPractisedIsStillThereTheNextTimeTheAppOpens() {
        val first = progress()
        repeat(Streak.GOAL) { first.recorded() }
        first.drilled("th", 7)

        val reopened = progress()
        assertEquals(Streak.GOAL, reopened.streak.today)
        assertEquals(1, reopened.streak.days)
        assertEquals(7, reopened.practice.drills["th"])
    }

    @Test
    fun theStreakGrowsByADayAndEndsWhenOneIsMissed() {
        val progress = progress()
        repeat(Streak.GOAL) { progress.recorded() }
        today = today.plusDays(1)
        assertEquals(1, progress.streak.days)
        assertFalse(progress.streak.todayDone)
        repeat(Streak.GOAL) { progress.recorded() }
        assertEquals(2, progress.streak.days)

        today = today.plusDays(2)
        assertEquals(0, progress.streak.days)
        assertEquals(2, progress.streak.best)
    }

    @Test
    fun aFileThatCannotBeReadIsSetAsideAndCountingStartsAfresh() {
        file.writeText("not json")
        val progress = progress()
        assertEquals(0, progress.streak.today)
        assertTrue(File(file.path + ".unread").exists())
        progress.recorded()
        assertEquals(1, progress().streak.today)
    }
}

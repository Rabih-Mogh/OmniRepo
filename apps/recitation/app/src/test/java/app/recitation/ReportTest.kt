package app.recitation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import app.recitation.data.Journal
import app.recitation.data.Library
import app.recitation.data.Playlist
import app.recitation.data.Report
import app.recitation.data.Track
import app.recitation.speech.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime

/** The journal of what the background work did, how the engine's fastest way of running is found, and the report made of them. */
@RunWith(AndroidJUnit4::class)
class ReportTest {
    @get:Rule
    val folder = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theJournalKeepsItsLatestLinesWithTheirTimes() {
        var now = LocalDateTime.of(2026, 11, 23, 9, 30, 0)
        val journal = Journal(File(folder.root, "journal.txt")) { now }
        journal.note("work started")
        now = now.plusMinutes(10)
        journal.note("surah 36: 60 pieces\nat 1.08x")
        assertEquals(listOf("11-23 09:30:00 work started", "11-23 09:40:00 surah 36: 60 pieces at 1.08x"), journal.lines())
        // What the system says went wrong can run to pages: a line keeps its beginning.
        journal.note("x".repeat(5_000))
        assertEquals(15 + Journal.LONGEST, journal.lines().last().length)

        // It never grows past a few pages: the oldest lines go, whole.
        repeat(2_000) { journal.note("line $it") }
        val kept = journal.lines(most = 10_000)
        assertTrue("${kept.size} lines kept", kept.size in 200..1_200)
        assertTrue(kept.first().startsWith("11-23 09:40:00 line "))
        assertEquals("11-23 09:40:00 line 1999", kept.last())
        assertTrue(File(folder.root, "journal.txt").length() < 40_000)
    }

    /** Seconds each way of running the engine takes, in the order they are tried. */
    private fun found(cores: Int, took: Map<Tuning, Float?>): Pair<Tuning, List<Tuning>> {
        val tried = ArrayList<Tuning>()
        return Tuning.find(cores, time = { tried += it; took[it] }) to tried
    }

    @Test
    fun theFastestNumberOfThreadsIsKeptAndNotSpinningIsPreferred() {
        val (best, tried) = found(8, mapOf(Tuning(4, true) to 4.0f, Tuning(4, false) to 4.2f, Tuning(2, false) to 6.5f, Tuning(6, false) to 3.4f))
        assertEquals(Tuning(6, spin = false), best)
        assertEquals(listOf(Tuning(4, true), Tuning(4, false), Tuning(2, false), Tuning(6, false)), tried)
    }

    @Test
    fun spinningIsKeptWhereNotSpinningIsClearlySlower() {
        val (best, _) = found(8, mapOf(Tuning(4, true) to 4.0f, Tuning(4, false) to 5.0f, Tuning(2, true) to 6.0f, Tuning(6, true) to 4.4f))
        assertEquals(Tuning(4, spin = true), best)
    }

    @Test
    fun aPhoneWithFewCoresIsNotTriedOnMoreThreadsThanItHas() {
        val (best, tried) = found(4, mapOf(Tuning(4, true) to 4.0f, Tuning(4, false) to 4.0f, Tuning(2, false) to 3.0f))
        assertEquals(Tuning(2, spin = false), best)
        assertTrue(tried.none { it.threads == 6 })
    }

    @Test
    fun whereTheEngineCannotBeTriedItIsRunAsBefore() {
        assertEquals(Tuning.USUAL, found(8, emptyMap()).first)
        // Only not spinning fails: the fastest that does run.
        assertEquals(Tuning(6, spin = true), found(8, mapOf(Tuning(4, true) to 4f, Tuning(2, true) to 5f, Tuning(6, true) to 3f)).first)
    }

    @Test
    fun whatWasFoundIsKeptForTheNextTime() {
        assertNull(Tuning.kept(context))
        Tuning.keep(context, Tuning(6, spin = false))
        assertEquals(Tuning(6, spin = false), Tuning.kept(context))
    }

    @Test
    fun theReportSaysWhatThePhoneAndTheQueueAreAndWhatHappenedLately() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        Library.get(context).add(
            Playlist(
                "https://example.org/report", "Report", "Test",
                listOf(Track("report-a", "A", 60, 36, ready = true), Track("report-b", "B", 60, 2, error = "The phone's storage is full."), Track("report-c", "C", 60, 3, wanted = true)),
            ),
        )
        Tuning.keep(context, Tuning(6, spin = false))
        Journal.get(context).note("surah 36: 60 pieces at 0.91x")

        val report = Report.text(context)
        for (line in listOf("Recitation: ", "Phone: ", "Android: ", "Cores: ", "Memory: ", "Battery saver: ", "Storage: ", "Speech model: not on the phone", "Work \"prepare-now\": none")) {
            assertTrue("\"$line\" is in:\n$report", line in report)
        }
        assertTrue(report, "run on 6 threads" in report)
        assertTrue(report, Regex("Recordings: \\d+: \\d+ ready, \\d+ playable, \\d+ in the queue, \\d+ failed").containsMatchIn(report))
        assertTrue(report, "  failed, surah 2: The phone's storage is full." in report)
        assertTrue(report, report.trimEnd().endsWith("surah 36: 60 pieces at 0.91x"))
        assertTrue("nothing in it could not be found out:\n$report", "? (" !in report)
    }
}

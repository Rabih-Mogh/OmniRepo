package app.safeguard.core

import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookTest {
    private val hour = 3_600_000L
    private val start = Stamp(wall = 1_000_000_000_000, elapsed = 50_000, boot = 7)

    private fun later(hours: Double, from: Stamp = start) =
        from.copy(wall = from.wall + (hours * hour).toLong(), elapsed = from.elapsed + (hours * hour).toLong())

    private val youtube = Presets.limits.first()
    private val locked = Book(Rules(limits = Presets.limits, sites = setOf("reddit.com"), locked = true))

    @Test
    fun `unlocked, every change is immediate`() {
        val book = Book(Rules(sites = setOf("reddit.com")))
            .ask(Change.Drop(Listed.SITE, "reddit.com"), start)
            .ask(Change.Add(Listed.KEYWORD, "casino"), start)
        assertEquals(Rules(keywords = setOf("casino")), book.rules)
        assertTrue(book.pending.isEmpty())
    }

    @Test
    fun `locked, a change that tightens is immediate`() {
        val stricter = youtube.copy(minutesPerDay = 10, apps = youtube.apps + "app.revanced.android.youtube")
        val book = locked
            .ask(Change.Add(Listed.SITE, "x.com"), start)
            .ask(Change.Add(Listed.APP, "TikTok"), start)
            .ask(Change.PutLimit(stricter), start)
            .ask(Change.PutLimit(Limit("new", "Games", apps = setOf("com.example.game"), minutesPerDay = 15)), start)
            .ask(Change.Wait(48), start)
        assertTrue(book.pending.isEmpty())
        assertEquals(setOf("reddit.com", "x.com"), book.rules.sites)
        assertEquals(setOf("TikTok"), book.rules.apps)
        assertEquals(stricter, book.rules.limits.first())
        assertEquals(4, book.rules.limits.size)
        assertEquals(48, book.rules.waitHours)
    }

    @Test
    fun `locked, a change that loosens waits out the waiting period`() {
        val asked = locked.ask(Change.Drop(Listed.SITE, "reddit.com"), start)
        assertEquals(locked.rules, asked.rules)
        assertEquals(24 * hour, asked.pending.single().left)

        val almost = asked.at(later(23.9))
        assertEquals(setOf("reddit.com"), almost.rules.sites)
        val done = almost.at(later(24.0))
        assertTrue(done.rules.sites.isEmpty())
        assertTrue(done.pending.isEmpty())
    }

    @Test
    fun `what counts as loosening a limit`() {
        val rules = locked.rules
        assertTrue(Change.PutLimit(youtube.copy(minutesPerDay = 45)).loosens(rules))
        assertTrue(Change.PutLimit(youtube.copy(minutesPerDay = null)).loosens(rules))
        assertTrue(Change.PutLimit(youtube.copy(hours = null)).loosens(rules))
        assertTrue(Change.PutLimit(youtube.copy(hours = youtube.hours!!.copy(to = 16 * 60))).loosens(rules))
        assertTrue(Change.PutLimit(youtube.copy(sites = emptySet())).loosens(rules))
        assertTrue(Change.DropLimit("youtube").loosens(rules))
        assertTrue(Change.Wait(1).loosens(rules))
        assertTrue(Change.Unlock.loosens(rules))
        assertFalse(Change.PutLimit(youtube.copy(name = "Videos", minutesPerDay = 0)).loosens(rules))
        assertFalse(Change.PutLimit(youtube.copy(hours = youtube.hours!!.copy(days = DayOfWeek.values().toSet()))).loosens(rules))
        assertFalse(Change.Wait(24).loosens(rules))
    }

    @Test
    fun `setting the clock forward doesn't shorten a wait`() {
        val asked = locked.ask(Change.Unlock, start)
        // A minute of running time later, the clock claims two days have passed.
        val cheated = Stamp(wall = start.wall + 48 * hour, elapsed = start.elapsed + 60_000, boot = start.boot)
        val book = asked.at(cheated)
        assertTrue(book.rules.locked)
        assertEquals(24 * hour - 60_000, book.pending.single().left)
        // The rest still has to be waited out from there.
        assertTrue(book.at(later(23.0, cheated)).rules.locked)
        assertFalse(book.at(later(24.0, cheated)).rules.locked)
    }

    @Test
    fun `a wait carries on across a restart by the clock`() {
        val asked = locked.ask(Change.Unlock, start).at(later(10.0))
        // The phone was off for six hours and has been on for two minutes.
        val restarted = Stamp(wall = start.wall + 16 * hour, elapsed = 120_000, boot = start.boot + 1)
        assertEquals(8 * hour, asked.at(restarted).pending.single().left)
        // A clock set back never adds to a wait.
        val back = restarted.copy(wall = start.wall)
        assertEquals(14 * hour, asked.at(back).pending.single().left)
    }

    @Test
    fun `once unlocked, whatever was waiting goes through`() {
        val asked = locked
            .ask(Change.Unlock, start)
            .ask(Change.Drop(Listed.SITE, "reddit.com"), later(12.0))
        assertEquals(2, asked.pending.size)
        val done = asked.at(later(24.0)).at(later(24.0))
        assertFalse(done.rules.locked)
        assertTrue(done.rules.sites.isEmpty())
        assertTrue(done.pending.isEmpty())
    }

    @Test
    fun `asking twice doesn't start the wait again, and a request can be withdrawn`() {
        val asked = locked.ask(Change.Unlock, start).at(later(5.0)).ask(Change.Unlock, later(5.0))
        assertEquals(19 * hour, asked.pending.single().left)
        val withdrawn = asked.cancel(Change.Unlock)
        assertTrue(withdrawn.pending.isEmpty())
        assertTrue(withdrawn.at(later(30.0)).rules.locked)
    }

    @Test
    fun `tightening a limit withdraws a looser version that was waiting`() {
        val asked = locked.ask(Change.PutLimit(youtube.copy(minutesPerDay = 60)), start)
        assertEquals(1, asked.pending.size)
        val tightened = asked.ask(Change.PutLimit(youtube.copy(minutesPerDay = 20)), later(1.0))
        assertTrue(tightened.pending.isEmpty())
        assertEquals(20, tightened.at(later(30.0)).rules.limits.first().minutesPerDay)
    }

    @Test
    fun `a second loosening of the same limit takes the place of the first`() {
        val asked = locked
            .ask(Change.PutLimit(youtube.copy(minutesPerDay = 60)), start)
            .ask(Change.PutLimit(youtube.copy(minutesPerDay = 90)), later(2.0))
        assertEquals(Change.PutLimit(youtube.copy(minutesPerDay = 90)), asked.pending.single().change)
        assertEquals(24 * hour, asked.pending.single().left)
    }
}

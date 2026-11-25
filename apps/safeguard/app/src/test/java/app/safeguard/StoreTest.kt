package app.safeguard

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.safeguard.core.Change
import app.safeguard.core.Hours
import app.safeguard.core.Limit
import app.safeguard.core.Listed
import app.safeguard.core.Pending
import app.safeguard.core.Presets
import app.safeguard.core.Rules
import app.safeguard.core.Stamp
import app.safeguard.data.Codec
import app.safeguard.data.Store
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek

/** What the app keeps on the phone: the rules, the changes that are waiting, and the day's use. */
@RunWith(AndroidJUnit4::class)
class StoreTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun fresh() = Store.load(app)

    @Test
    fun `a first run starts unlocked with the three limits`() {
        val rules = Store.book.value.rules
        assertEquals(listOf("YouTube", "Facebook", "Instagram"), rules.limits.map { it.name })
        assertEquals(Presets.limits, rules.limits)
        assertFalse(rules.locked)
        assertEquals(24, rules.waitHours)
    }

    @Test
    fun `rules and a waiting change are still there when the app starts again`() {
        Store.ask(app, Change.Add(Listed.SITE, "reddit.com"))
        Store.ask(app, Change.Lock)
        Store.ask(app, Change.Drop(Listed.SITE, "reddit.com"))
        val before = Store.book.value
        assertTrue(before.rules.locked)
        assertEquals(setOf("reddit.com"), before.rules.sites)
        assertEquals(Change.Drop(Listed.SITE, "reddit.com"), before.pending.single().change)

        Store.load(app)
        assertEquals(before, Store.book.value)
    }

    @Test
    fun `a waiting change can be withdrawn`() {
        Store.ask(app, Change.Lock)
        Store.ask(app, Change.Unlock)
        Store.cancel(app, Change.Unlock)
        Store.load(app)
        assertTrue(Store.book.value.pending.isEmpty())
        assertTrue(Store.book.value.rules.locked)
    }

    @Test
    fun `the day's use is kept, by limit`() {
        Store.spend(app, "youtube", 90_000)
        Store.spend(app, "youtube", 45_000)
        Store.spend(app, "facebook", 10_000)
        Store.load(app)
        assertEquals(135, Store.usedSeconds("youtube"))
        assertEquals(10, Store.usedSeconds("facebook"))
        assertEquals(0, Store.usedSeconds("instagram"))
    }

    @Test
    fun `every kind of rule and change survives being written down`() {
        val rules = Rules(
            limits = Presets.limits + Limit("games", "ألعاب", apps = setOf("com.example.game")) +
                Limit("night", "Night", sites = setOf("example.com"), minutesPerDay = 0, hours = Hours(22 * 60, 6 * 60, setOf(DayOfWeek.SUNDAY))),
            sites = setOf("reddit.com", "x.com"),
            keywords = setOf("casino", "قمار"),
            apps = setOf("TikTok"),
            waitHours = 72,
            locked = true,
        )
        assertEquals(rules, Codec.rules(JSONObject(Codec.write(rules).toString())))

        val seen = Stamp(wall = 1_800_000_000_000, elapsed = 123_456, boot = 9)
        val changes = listOf(
            Change.Add(Listed.APP, "TikTok"),
            Change.Drop(Listed.KEYWORD, "casino"),
            Change.PutLimit(rules.limits.last()),
            Change.DropLimit("games"),
            Change.Wait(1),
            Change.Lock,
            Change.Unlock,
        )
        val pending = changes.mapIndexed { i, change -> Pending(change, left = i * 1_000L, seen = seen) }
        assertEquals(pending, Codec.pending(JSONArray(Codec.write(pending).toString())))
    }
}

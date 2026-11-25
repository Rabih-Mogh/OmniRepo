package app.safeguard.core

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class JudgeTest {
    private val own = listOf("Safeguard", "الحارس")

    // 23 November 2026 is a Monday, 28 November a Saturday.
    private val monday10 = LocalDateTime.of(2026, 11, 23, 10, 0)
    private val monday20 = LocalDateTime.of(2026, 11, 23, 20, 0)
    private val saturday = LocalDateTime.of(2026, 11, 28, 11, 0)

    private val rules = Rules(
        limits = Presets.limits,
        sites = setOf("reddit.com"),
        keywords = setOf("casino"),
        apps = setOf("TikTok"),
    )

    private fun look(front: Front, now: LocalDateTime = monday20, used: Long = 0, rules: Rules = this.rules) =
        Judge.look(rules, front, now, own) { used }

    private fun chrome(address: String, vararg page: String) = Front(
        "com.android.chrome", "Chrome", browser = true,
        seen = listOf(Seen(address, "com.android.chrome:id/url_bar", editable = true)) + page.map { Seen(it) },
    )

    private val youtube = Front("com.google.android.youtube", "YouTube")

    @Test
    fun `an app with no rule about it is left alone`() {
        assertEquals(Verdict.Free, look(Front("com.whatsapp", "WhatsApp")))
        assertEquals(Verdict.Free, look(chrome("wikipedia.org/wiki/Time")))
    }

    @Test
    fun `a limited app is closed during its hours and says until when`() {
        val closed = Verdict.Closed(Why.Hours("YouTube", LocalDateTime.of(2026, 11, 23, 17, 0)), page = false)
        assertEquals(closed, look(youtube, monday10))
    }

    @Test
    fun `outside its hours a limited app counts against its allowance`() {
        assertEquals(Verdict.Counting("youtube"), look(youtube, monday20, used = 29 * 60 + 59))
        assertEquals(Verdict.Counting("youtube"), look(youtube, saturday))
    }

    @Test
    fun `a limited app is closed once the day's minutes are used`() {
        assertEquals(Verdict.Closed(Why.Spent("YouTube", 30), page = false), look(youtube, monday20, used = 30 * 60))
    }

    @Test
    fun `a limit's website in a browser is the same limit`() {
        assertEquals(Verdict.Counting("youtube"), look(chrome("m.youtube.com/watch?v=1")))
        assertEquals(
            Verdict.Closed(Why.Spent("Facebook", 30), page = true),
            look(chrome("https://m.facebook.com/home"), used = 31 * 60),
        )
    }

    @Test
    fun `a browser whose address bar is out of sight is taken to be where it last was`() {
        val scrolled = Front("com.android.chrome", "Chrome", browser = true, seen = listOf(Seen("Comments")), host = "m.youtube.com")
        assertEquals(Verdict.Counting("youtube"), look(scrolled))
    }

    @Test
    fun `a blocked website is closed in a browser`() {
        assertEquals(Verdict.Closed(Why.Site("reddit.com"), page = true), look(chrome("old.reddit.com/r/all")))
    }

    @Test
    fun `a blocked word closes a browser page that shows it and nothing else`() {
        assertEquals(Verdict.Closed(Why.Keyword("casino"), page = true), look(chrome("example.com", "Welcome to the Casino")))
        assertEquals(Verdict.Closed(Why.Keyword("casino"), page = true), look(chrome("search.example/?q=casino")))
        val chat = Front("com.whatsapp", "WhatsApp", seen = listOf(Seen("the casino scene in that film")))
        assertEquals(Verdict.Free, look(chat))
    }

    @Test
    fun `a blocked app is closed in the store and when opened, on the phone or not`() {
        val store = Front("com.android.vending", "Google Play Store", store = true, seen = listOf(Seen("TikTok"), Seen("Install")))
        assertEquals(Verdict.Closed(Why.Never("TikTok"), page = true), look(store))
        assertEquals(Verdict.Closed(Why.Never("TikTok"), page = false), look(Front("com.zhiliaoapp.musically", "TikTok")))
        assertEquals(Verdict.Closed(Why.Never("TikTok"), page = false), look(Front("com.zhiliaoapp.musically.go", "TikTok Lite")))
        // Other pages of the store stay open.
        val other = Front("com.android.vending", "Google Play Store", store = true, seen = listOf(Seen("Signal"), Seen("Install")))
        assertEquals(Verdict.Free, look(other))
    }

    @Test
    fun `locked, the settings pages that name the app are closed`() {
        val info = Front("com.android.settings", "Settings", settings = true, seen = listOf(Seen("App info"), Seen("Safeguard"), Seen("Force stop")))
        assertEquals(Verdict.Free, look(info))
        assertEquals(Verdict.Closed(Why.Guarded, page = true), look(info, rules = rules.copy(locked = true)))
        val arabic = info.copy(seen = listOf(Seen("إلغاء تثبيت الحارس")))
        assertEquals(Verdict.Closed(Why.Guarded, page = true), look(arabic, rules = rules.copy(locked = true)))
        // The rest of the settings stay open.
        val wifi = Front("com.android.settings", "Settings", settings = true, seen = listOf(Seen("Wi-Fi")))
        assertEquals(Verdict.Free, look(wifi, rules = rules.copy(locked = true)))
    }
}

package app.safeguard

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.safeguard.core.Change
import app.safeguard.core.Hours
import app.safeguard.core.Limit
import app.safeguard.core.Listed
import app.safeguard.core.Seen
import app.safeguard.data.Store
import app.safeguard.guard.Watch
import app.safeguard.ui.BlockActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.time.DayOfWeek
import java.time.Duration

/**
 * The watching service on the JVM, shown made-up windows: what it closes, how it closes
 * it, and the time it counts. What the rules say about a window is tested in the core
 * module; this is the service acting on it.
 */
@RunWith(AndroidJUnit4::class)
class WatchTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var watch: Watch
    private var front: AccessibilityNodeInfo? = null

    private val always = Hours(0, 0, DayOfWeek.values().toSet())

    @Before
    fun start() {
        Store.load(app)
        // Chrome is on the phone and opens web links, which is what makes it a browser.
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        val chrome = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.chrome"
                name = "Main"
            }
        }
        @Suppress("DEPRECATION")
        shadowOf(app.packageManager).addResolveInfoForIntent(link, chrome)

        watch = Robolectric.buildService(Watch::class.java).create().get()
        watch.window = { front }
        watch.onServiceConnected()
    }

    @Suppress("DEPRECATION")
    private fun show(app: String, vararg texts: Seen) {
        val root = AccessibilityNodeInfo.obtain().apply { packageName = app }
        for (seen in texts) {
            val child = AccessibilityNodeInfo.obtain().apply {
                packageName = app
                text = seen.text
                isEditable = seen.editable
                viewIdResourceName = seen.id
            }
            shadowOf(root).addChild(child)
        }
        front = root
        watch.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply { packageName = app })
    }

    private fun blocked(): Intent? = shadowOf(app).nextStartedActivity?.takeIf { it.component?.className == BlockActivity::class.java.name }

    private fun steppedBack() = AccessibilityService.GLOBAL_ACTION_BACK in shadowOf(watch).globalActionsPerformed

    @Test
    fun `an app in its closed hours is covered and left by going home`() {
        Store.ask(app, Change.PutLimit(Limit("youtube", "YouTube", apps = setOf("com.google.android.youtube"), hours = always)))
        show("com.google.android.youtube")
        val block = blocked()!!
        assertTrue(block.getBooleanExtra("leave", false))
        assertEquals("YouTube is closed at all hours.", block.getStringExtra("reason"))
        assertFalse(steppedBack())
    }

    @Test
    fun `an app with no rule about it is left alone`() {
        show("com.whatsapp")
        assertNull(blocked())
        assertFalse(steppedBack())
    }

    @Test
    fun `time in a limited app is counted, and closes it when the day's minutes are used`() {
        Store.ask(app, Change.PutLimit(Limit("youtube", "YouTube", apps = setOf("com.google.android.youtube"), minutesPerDay = 1)))
        show("com.google.android.youtube")
        assertNull(blocked())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(45))
        assertTrue("counted ${Store.usedSeconds("youtube")} s", Store.usedSeconds("youtube") in 40..45)
        assertNull(blocked())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertEquals("Today's 1 minutes of YouTube are used up. It opens again tomorrow.", blocked()!!.getStringExtra("reason"))
    }

    @Test
    fun `time stops counting when another app comes to the front`() {
        Store.ask(app, Change.PutLimit(Limit("youtube", "YouTube", apps = setOf("com.google.android.youtube"), minutesPerDay = 30)))
        show("com.google.android.youtube")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        show("com.whatsapp")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(5))
        assertEquals(30, Store.usedSeconds("youtube"))
    }

    @Test
    fun `a blocked website is stepped back out of, in the browser`() {
        Store.ask(app, Change.Add(Listed.SITE, "reddit.com"))
        show("com.android.chrome", Seen("old.reddit.com/r/all", "com.android.chrome:id/url_bar", editable = true), Seen("Popular posts"))
        val block = blocked()!!
        assertFalse(block.getBooleanExtra("leave", true))
        assertEquals("reddit.com is on your list of blocked websites.", block.getStringExtra("reason"))
        assertTrue(steppedBack())
    }

    @Test
    fun `a blocked word on a page closes it`() {
        Store.ask(app, Change.Add(Listed.KEYWORD, "casino"))
        show("com.android.chrome", Seen("example.com", "com.android.chrome:id/url_bar", editable = true), Seen("Best online casino bonuses"))
        assertEquals("That page showed “casino”, a word you blocked.", blocked()!!.getStringExtra("reason"))
        assertTrue(steppedBack())
    }

    @Test
    fun `a limit's website counts in the browser as the app does`() {
        Store.ask(app, Change.PutLimit(Limit("youtube", "YouTube", sites = setOf("youtube.com"), minutesPerDay = 30)))
        show("com.android.chrome", Seen("m.youtube.com/watch?v=1", "com.android.chrome:id/url_bar", editable = true))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        // Scrolled down, the address bar is gone; the page is still the same one.
        show("com.android.chrome", Seen("Comments"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertEquals(40, Store.usedSeconds("youtube"))
    }

    @Test
    fun `a blocked app that isn't on the phone is closed on its page in the store`() {
        Store.ask(app, Change.Add(Listed.APP, "TikTok"))
        show("com.android.vending", Seen("TikTok"), Seen("Install"))
        assertEquals("TikTok is on your list of blocked apps.", blocked()!!.getStringExtra("reason"))
        assertTrue(steppedBack())
    }

    @Test
    fun `a blocked app is stopped at Android's install prompt`() {
        Store.ask(app, Change.Add(Listed.APP, "TikTok"))
        show("com.google.android.packageinstaller", Seen("TikTok"), Seen("Do you want to install this app?"))
        assertEquals("TikTok is on your list of blocked apps.", blocked()!!.getStringExtra("reason"))
        assertTrue(steppedBack())
    }

    @Test
    fun `a blocked app that is on the phone is closed when it is opened`() {
        val tiktok = PackageInfo().apply {
            packageName = "com.zhiliaoapp.musically"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.zhiliaoapp.musically"
                nonLocalizedLabel = "TikTok"
            }
        }
        shadowOf(app.packageManager).installPackage(tiktok)
        Store.ask(app, Change.Add(Listed.APP, "tiktok"))

        show("com.zhiliaoapp.musically")
        val block = blocked()!!
        assertEquals("tiktok is on your list of blocked apps.", block.getStringExtra("reason"))
        assertTrue(block.getBooleanExtra("leave", false))
        assertFalse(steppedBack())
    }

    @Test
    fun `the settings page about the app is open while unlocked and closed once locked`() {
        show("com.android.settings", Seen("App info"), Seen("Safeguard"), Seen("Force stop"))
        assertNull(blocked())

        Store.ask(app, Change.Lock)
        show("com.android.settings", Seen("App info"), Seen("Safeguard"), Seen("Force stop"))
        assertTrue(blocked()!!.getStringExtra("reason")!!.startsWith("Safeguard is locked"))
        assertTrue(steppedBack())
    }

    @Test
    fun `locked, the rest of the settings stay open`() {
        Store.ask(app, Change.Lock)
        show("com.android.settings", Seen("Wi-Fi"), Seen("Bluetooth"))
        assertNull(blocked())
    }
}

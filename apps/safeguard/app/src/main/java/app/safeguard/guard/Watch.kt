package app.safeguard.guard

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import app.safeguard.R
import app.safeguard.core.Front
import app.safeguard.core.Judge
import app.safeguard.core.Page
import app.safeguard.core.Seen
import app.safeguard.core.Verdict
import app.safeguard.data.Store
import app.safeguard.ui.BlockActivity
import app.safeguard.ui.Words
import app.safeguard.update.AppUpdate
import java.time.LocalDateTime

/**
 * The part that watches. Android tells it when the window in front changes; it asks
 * [Judge] what the rules say about that window and, when they say closed, steps back out
 * of it and puts up [BlockActivity]. While a limited app or website is in front it counts
 * the time against the day's allowance.
 *
 * It is an accessibility service because that is the one way Android lets an app see what
 * is in front and act on it. The text of a window is read only in browsers (for the
 * address and the blocked words), app stores (for the blocked apps) and the
 * phone's settings (to keep a locked Safeguard from being switched off). Nothing read is
 * kept, and nothing leaves the phone.
 */
class Watch : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var apps: Apps

    // The limit whose time is running, and the running time it was last charged up to.
    private var counting: String? = null
    private var since = 0L

    // Looks again while a limit's time runs, to stop it the moment the allowance is used.
    private val tick = Runnable { look() }

    // Looks once more after a burst of changes in a window, so the last of them isn't missed.
    private val again = Runnable { look() }
    private var waiting = false
    private var looked = 0L

    private var closedAt = -10_000L

    // Where a browser last showed itself to be, for while its address bar is scrolled away.
    private var lastHost: Pair<String, String>? = null

    /** The window in front. Replaced in tests, which have no screen. */
    internal var window: () -> AccessibilityNodeInfo? = { rootInActiveWindow }

    // The screen going dark stops the count; unlocking the phone looks at what is in front again.
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                settle()
                count(null)
            } else {
                look()
            }
        }
    }

    public override fun onServiceConnected() {
        Store.open(this)
        apps = Apps(this)
        val events = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_USER_PRESENT) }
        // System broadcasts both; nothing another app sends is listened for.
        ContextCompat.registerReceiver(this, screen, events, ContextCompat.RECEIVER_NOT_EXPORTED)
        look()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val app = event.packageName?.toString() ?: return
        when {
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> look()
            // What a window shows only matters where it is read, and not on every keystroke.
            !apps.read(app) -> Unit
            SystemClock.uptimeMillis() - looked >= GAP -> look()
            !waiting -> {
                waiting = true
                handler.postDelayed(again, GAP)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        settle()
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(screen)
        } catch (_: Exception) {
            // Never connected.
        }
        super.onDestroy()
    }

    private fun look() {
        handler.removeCallbacks(again)
        waiting = false
        looked = SystemClock.uptimeMillis()
        settle()
        Store.tick(this)

        val root = try {
            window()
        } catch (_: Exception) {
            null
        }
        val app = root?.packageName?.toString()
        // Nothing to judge, or one of the app's own screens.
        if (root == null || app == null || app == packageName) return count(null)

        val browser = apps.browser(app)
        // Android's own install prompt is let through while it is asking about this app's update.
        val updating = apps.installer(app) && AppUpdate.state.value == AppUpdate.State.Installing
        val settings = apps.settings(app) && !updating
        val store = apps.store(app)
        val seen = if (browser || store || settings) Reading.texts(root) else emptyList()
        val host = if (browser) host(app, seen) else null
        val front = Front(app, apps.label(app), browser, store, settings, seen, host)

        when (val verdict = Judge.look(Store.book.value.rules, front, LocalDateTime.now(), names(), Store::usedSeconds)) {
            Verdict.Free -> count(null)
            is Verdict.Counting -> count(verdict.limit)
            is Verdict.Closed -> {
                count(null)
                close(verdict)
            }
        }
    }

    private fun host(app: String, seen: List<Seen>): String? {
        val shown = Page.host(seen)
        if (shown != null) lastHost = app to shown
        return shown ?: lastHost?.takeIf { it.first == app }?.second
    }

    // What the app is called in the phone's language and in the other one, for the settings pages about it.
    private fun names(): List<String> = listOf(getString(R.string.app_name), "Safeguard", "الحارس").distinct()

    private fun close(verdict: Verdict.Closed) {
        // Out of the page, so that coming back to the app doesn't land on it again.
        if (verdict.page) performGlobalAction(GLOBAL_ACTION_BACK)
        val now = SystemClock.uptimeMillis()
        if (now - closedAt < 1_000) return
        closedAt = now
        startActivity(BlockActivity.intent(this, Words.why(this, verdict.why), leave = !verdict.page))
    }

    /** Charges the time since the last look to the limit that was running. */
    private fun settle() {
        val now = SystemClock.elapsedRealtime()
        counting?.let { Store.spend(this, it, now - since) }
        since = now
    }

    private fun count(limit: String?) {
        counting = limit
        since = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        if (limit != null) handler.postDelayed(tick, TICK)
    }

    companion object {
        private const val GAP = 300L
        private const val TICK = 10_000L

        /** Whether the phone has the service switched on. */
        fun on(context: Context): Boolean {
            val me = ComponentName(context, Watch::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}

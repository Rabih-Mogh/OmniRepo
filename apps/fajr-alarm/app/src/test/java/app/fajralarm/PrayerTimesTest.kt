package app.fajralarm

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.PrayerClock
import app.fajralarm.core.PrayerMoment
import app.fajralarm.core.PrayerNow
import app.fajralarm.core.PrayerTime
import app.fajralarm.data.SettingsStore
import app.fajralarm.prayer.PrayerTicker
import app.fajralarm.ui.MainActivity
import app.fajralarm.widget.CurrentPrayerWidget
import app.fajralarm.widget.PrayerTimesWidget
import app.fajralarm.widget.PrayerWidgets
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * The other prayers on the JVM: the list on the main screen, the notification a time
 * can ask for, the lock screen countdown and the two widgets. The clock here is the
 * real one, so each test works out what the current moment should show.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h4400dp-420dpi")
class PrayerTimesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val store get() = SettingsStore(app)
    private val beirut = AlarmSettings(latitude = 33.8938, longitude = 35.5018)
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    private fun now(): PrayerNow = PrayerClock.at(ZonedDateTime.now(), beirut.calcParams()!!)!!

    /** The alarms queued with Android, as (when, whether it wakes the phone). */
    private fun queued(): List<Pair<Long, Boolean>> =
        shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .map { it.triggerAtMs to (it.type == AlarmManager.RTC_WAKEUP) }

    private fun PrayerMoment.millis() = at.toInstant().toEpochMilli()

    private fun texts(view: View): List<String> = when (view) {
        is Chronometer -> emptyList() // a running clock, not words
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test
    fun `the main screen lists every time of the day and a bell turns its notification on`() {
        store.save(beirut)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithText("TIMES OF THE DAY").assertIsDisplayed()
            listOf("Duha", "Dhuhr", "Asr", "Maghrib", "Isha", "Midnight", "Last third of the night").forEach {
                // "Asr" is also the name of a calculation setting further down.
                compose.onAllNodesWithText(it).onFirst().assertIsDisplayed()
            }
            // Fajr has the alarm, not a bell.
            compose.onNodeWithContentDescription("Notify at Fajr").assertDoesNotExist()

            compose.onNodeWithContentDescription("Notify at Dhuhr").performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(setOf(PrayerTime.DHUHR), store.load().alerts)
            compose.onNodeWithContentDescription("Notify at Dhuhr").performClick()
            compose.waitForIdle()
            assertEquals(emptySet<PrayerTime>(), store.load().alerts)
            captureScreenRoboImage("build/screens/en-prayers.png")
        }
    }

    @Test
    fun `with no bell, widget or lock screen notice nothing is queued`() {
        store.save(beirut)
        PrayerTicker.refresh(app)
        assertNull(store.awaitedPrayer)
        assertTrue(queued().isEmpty())
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun `the phone is woken only for the time whose bell is on`() {
        // The third time from now: two others come before it and get no alarm at all.
        val belled = now().upcoming[2]
        store.save(beirut.copy(alerts = setOf(belled.prayer)))
        PrayerTicker.refresh(app)
        assertEquals(belled.prayer to belled.millis(), store.awaitedPrayer)
        assertEquals(listOf(belled.millis() to true), queued())
    }

    @Test
    fun `a bell queues the next time of the day and it gets its notification when it comes`() {
        store.save(beirut.copy(alerts = PrayerTime.values().toSet()))
        PrayerTicker.refresh(app)
        val next = now().nextEvent
        assertEquals(next.prayer to next.millis(), store.awaitedPrayer)
        assertEquals("an alarm that wakes the phone is set for it", listOf(next.millis() to true), queued())
        assertTrue(notifications.isEmpty())

        // The time comes.
        store.awaitedPrayer = PrayerTime.ASR to System.currentTimeMillis() - 1_000
        PrayerTicker.refresh(app)
        val alert = notifications.single()
        assertEquals(FajrAlarmApp.CHANNEL_PRAYER, alert.channelId)
        assertTrue(alert.extras.getString(Notification.EXTRA_TITLE)!!.startsWith("Asr · "))
        assertEquals("It's time to pray.", alert.extras.getString(Notification.EXTRA_TEXT))
        // And the one after it is queued.
        assertEquals(now().nextEvent.prayer, store.awaitedPrayer?.first)
    }

    @Test
    fun `a time with its bell off, or one that came long ago, gets no notification`() {
        store.save(beirut.copy(alerts = setOf(PrayerTime.DHUHR)))
        store.awaitedPrayer = PrayerTime.ASR to System.currentTimeMillis() - 1_000
        PrayerTicker.refresh(app)
        assertTrue(notifications.isEmpty())

        store.awaitedPrayer = PrayerTime.DHUHR to System.currentTimeMillis() - 20 * 60_000
        PrayerTicker.refresh(app)
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun `the lock screen notice counts down to the end of the current prayer`() {
        store.save(beirut.copy(lockScreenCountdown = true))
        PrayerTicker.refresh(app)
        val state = now()
        val target = (state.currentEnds ?: state.next.at).toInstant().toEpochMilli()

        val notice = notifications.single()
        assertEquals(FajrAlarmApp.CHANNEL_NOW, notice.channelId)
        assertTrue("it stays put", notice.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notice.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notice.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue("counts down to $target, was ${notice.`when`}", abs(notice.`when` - target) < 2_000)
        assertEquals(Notification.VISIBILITY_PUBLIC, notice.visibility)
        val title = notice.extras.getString(Notification.EXTRA_TITLE)!!
        val name = (state.current ?: state.next).prayer.name.lowercase().replaceFirstChar { it.uppercase() }
        assertTrue(title, title.startsWith(name) && (" until " in title || " at " in title))
        // It has to be right the moment the screen lights up, so the phone is woken to change it.
        assertEquals(listOf(state.nextEvent.millis() to true), queued())
        assertNull(store.awaitedPrayer)

        store.save(beirut)
        PrayerTicker.refresh(app)
        assertTrue(notifications.isEmpty())
        assertTrue(queued().isEmpty())
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun `the prayer times widget shows all nine times with the current prayer marked`() {
        val state = now()
        val view = PrayerWidgets.timesViews(app, state).apply(app, FrameLayout(app))
        assertEquals(5, view.findViewById<ViewGroup>(R.id.widget_prayers).childCount)
        assertEquals(4, view.findViewById<ViewGroup>(R.id.widget_optional).childCount)
        val shown = texts(view)
        listOf("Fajr", "Sunrise", "Duha", "Dhuhr", "Asr", "Maghrib", "Isha", "Midnight", "Last third").forEach {
            assertTrue("$it is on the widget: $shown", it in shown)
        }
        assertTrue(shown.first().endsWith(" ends in") || shown.first().endsWith(" starts in"))
        val marked = (0 until 5).map { view.findViewById<ViewGroup>(R.id.widget_prayers).getChildAt(it) }.count { it.background != null }
        val markedOptional = (0 until 4).map { view.findViewById<ViewGroup>(R.id.widget_optional).getChildAt(it) }.count { it.background != null }
        assertEquals("one prayer is marked", 1, marked)
        assertEquals(0, markedOptional)

        picture("widget-times", view, 328, 136)
        picture("widget-current", PrayerWidgets.currentViews(app, state).apply(app, FrameLayout(app)), 156, 84)
        picture(
            "notification-current",
            android.widget.RemoteViews(app.packageName, R.layout.notification_current_prayer).apply {
                setTextViewText(R.id.current_headline, "Asr ends in")
                setTextViewText(R.id.current_next, "Next: Maghrib 4:31 PM")
            }.apply(app, FrameLayout(app)),
            328, 64,
        )
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-420dpi")
    fun `the widgets speak Arabic and say so when there is no location`() {
        val view = PrayerWidgets.timesViews(app, now()).apply(app, FrameLayout(app))
        val shown = texts(view)
        listOf("الفجر", "الشروق", "الضحى", "الظهر", "العصر", "المغرب", "العشاء", "منتصف الليل", "الثلث الأخير").forEach {
            assertTrue("$it is on the widget: $shown", it in shown)
        }
        picture("ar-widget-times", view, 328, 136)

        val empty = PrayerWidgets.currentViews(app, null).apply(app, FrameLayout(app))
        assertEquals(listOf("افتح مواقيت الصلاة لتحديد موقعك"), texts(empty).filter { it.isNotEmpty() }.take(1))
        assertEquals(View.GONE, empty.findViewById<View>(R.id.widget_countdown).visibility)
    }

    @Test
    fun `a widget on the home screen is drawn and is kept current without waking the phone`() {
        store.save(beirut)
        val widgets = shadowOf(AppWidgetManager.getInstance(app))
        val id = widgets.createWidget(PrayerTimesWidget::class.java, R.layout.widget_times)
        assertTrue(PrayerWidgets.anyPlaced(app))
        assertTrue("Asr" in texts(widgets.getViewFor(id)))
        assertEquals(listOf(now().nextEvent.millis() to false), queued())
        assertNull(store.awaitedPrayer)

        val small = widgets.createWidget(CurrentPrayerWidget::class.java, R.layout.widget_current)
        assertTrue(texts(widgets.getViewFor(small)).any { it.startsWith("Next: ") })
        assertFalse(notifications.any { it.channelId == FajrAlarmApp.CHANNEL_PRAYER })
    }

    /** Saves a picture of [view] at [widthDp] x [heightDp] to app/build/screens. */
    private fun picture(name: String, view: View, widthDp: Int, heightDp: Int) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val density = activity.resources.displayMetrics.density
        activity.setContentView(
            FrameLayout(activity).apply {
                setBackgroundColor(0xFF30405A.toInt())
                setPadding(24, 24, 24, 24)
                addView(view, FrameLayout.LayoutParams((widthDp * density).toInt(), (heightDp * density).toInt()))
            },
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()
        captureScreenRoboImage("build/screens/$name.png")
        // Off the screen again, so the next picture isn't of this one.
        controller.pause().stop().destroy()
    }
}

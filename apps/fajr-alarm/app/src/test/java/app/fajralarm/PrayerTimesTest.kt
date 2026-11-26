package app.fajralarm

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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

    /** A fixed moment in Beirut, for what depends on the time of day. */
    private fun at(hour: Int, minute: Int): PrayerNow = PrayerClock.at(
        ZonedDateTime.of(LocalDate.of(2026, 11, 25), LocalTime.of(hour, minute), ZoneId.of("Asia/Beirut")),
        beirut.calcParams()!!,
    )!!

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
            listOf("Duha", "Dhuhr", "Asr", "Maghrib", "Isha", "Midnight", "Qiyam (last third)").forEach {
                // "Asr" is also the name of a calculation setting further down.
                compose.onAllNodesWithText(it).onFirst().assertIsDisplayed()
            }
            // Each prayer has its shape, a dot or a ring, and says when its time runs out.
            compose.onAllNodesWithContentDescription("Obligatory").assertCountEquals(5)
            compose.onAllNodesWithContentDescription("Nafl").assertCountEquals(2)
            val endTime = SemanticsMatcher("starts with \"until\"") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("until ") } == true
            }
            compose.onAllNodes(endTime).assertCountEquals(7)
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
    fun `the prayer times widget is a table of all nine times with the current prayer marked`() {
        val state = now()
        val names = listOf("Fajr", "Sunrise", "Duha", "Dhuhr", "Asr", "Maghrib", "Isha", "Midnight", "Qiyam")

        // Four cells by two: the table in two columns, read down the first and then the second.
        val small = PrayerWidgets.timesViews(app, state, beirut, size(300, 150)).apply(app, FrameLayout(app))
        assertEquals(names.take(5), rows(small, R.id.table_start).map { it.name() })
        assertEquals(names.drop(5) + "", rows(small, R.id.table_end).map { it.name() })
        assertTrue(texts(small).first().let { it.endsWith(" ends in") || it.endsWith(" starts in") })
        assertEquals("one prayer is marked", 1, rows(small).count { it.background != null })
        assertEquals(View.GONE, small.findViewById<View>(R.id.widget_next).visibility)
        assertEquals(View.GONE, small.findViewById<View>(R.id.widget_footer).visibility)
        assertTrue("no room for AM and PM", rows(small).none { it.text(R.id.row_time).endsWith("M") })
        // Each prayer has its shape before its name: a dot if obligatory, a ring if voluntary.
        assertEquals(
            mapOf(
                "dot" to listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"),
                "ring" to listOf("Duha", "Qiyam"),
                "none" to listOf("Sunrise", "Midnight", ""),
            ),
            rows(small).groupBy({ it.mark() }, { it.name() }),
        )
        picture("widget-times", small, 300, 150)

        // A little taller: the next prayer under the countdown.
        val medium = PrayerWidgets.timesViews(app, state, beirut, size(300, 200)).apply(app, FrameLayout(app))
        assertEquals(5, rows(medium, R.id.table_start).size)
        assertTrue(medium.findViewById<TextView>(R.id.widget_next).text.startsWith("Next: "))
        assertEquals(state.current != null, medium.findViewById<View>(R.id.widget_next).visibility == View.VISIBLE)
        picture("widget-times-medium", medium, 300, 200)

        // Taller still: one column, each prayer with its start and end, and a bell beside the times that notify.
        val belled = beirut.copy(alerts = setOf(PrayerTime.DHUHR, PrayerTime.MIDNIGHT))
        val tall = PrayerWidgets.timesViews(app, state, belled, size(250, 270)).apply(app, FrameLayout(app))
        assertEquals(names, rows(tall, R.id.table_start).map { it.name() })
        assertEquals(View.GONE, tall.findViewById<View>(R.id.table_end).visibility)
        assertEquals(1, rows(tall).count { it.background != null })
        val ranges = rows(tall).associate { it.name() to it.text(R.id.row_time) }
        assertEquals(
            "every prayer has a start and an end, sunrise and midnight one moment",
            setOf("Sunrise", "Midnight"), ranges.filterValues { " – " !in it }.keys,
        )
        assertEquals("Fajr ends at sunrise", ranges.getValue("Sunrise"), ranges.getValue("Fajr").substringAfter(" – "))
        assertEquals("Asr starts when Dhuhr ends", ranges.getValue("Asr").substringBefore(" – "), ranges.getValue("Dhuhr").substringAfter(" – "))
        assertEquals("Qiyam ends at Fajr, as Isha does", ranges.getValue("Isha").substringAfter(" – "), ranges.getValue("Qiyam").substringAfter(" – "))
        assertEquals(listOf("Duha", "Qiyam"), rows(tall).filter { it.mark() == "ring" }.map { it.name() })
        assertEquals(
            listOf("Dhuhr", "Midnight"),
            rows(tall).filter { it.findViewById<View>(R.id.row_bell).visibility == View.VISIBLE }.map { it.name() },
        )
        assertEquals(View.GONE, tall.findViewById<View>(R.id.widget_footer).visibility)
        picture("widget-times-tall", tall, 250, 270)

        // Tall and wide: when each prayer ends, and the alarm and the method at the bottom.
        val full = PrayerWidgets.timesViews(app, state, belled, size(330, 350)).apply(app, FrameLayout(app))
        val table = rows(full, R.id.table_start)
        assertEquals(listOf("", "Begins", "Ends"), listOf(R.id.row_name, R.id.row_time, R.id.row_ends).map { table.first().text(it) })
        assertEquals(names, table.drop(1).map { it.name() })
        val ends = table.drop(1).associate { it.name() to it.text(R.id.row_ends) }
        assertEquals("Fajr ends at sunrise", table[2].text(R.id.row_time), ends["Fajr"])
        assertEquals("Dhuhr ends at Asr", table[5].text(R.id.row_time), ends["Dhuhr"])
        assertEquals("Maghrib ends at Isha", table[7].text(R.id.row_time), ends["Maghrib"])
        assertTrue("Isha ends at the next Fajr", ends.getValue("Isha").endsWith("AM"))
        assertEquals("sunrise and midnight are single moments", listOf("", ""), listOf("Sunrise", "Midnight").map(ends::getValue))
        assertTrue("Duha ends before noon", ends.getValue("Duha").endsWith("AM"))
        assertEquals("Qiyam ends at the next Fajr", ends.getValue("Isha"), ends.getValue("Qiyam"))
        assertEquals(listOf("none", "dot", "none", "ring"), table.take(4).map { it.mark() })
        val footer = full.findViewById<TextView>(R.id.widget_footer)
        assertEquals(View.VISIBLE, footer.visibility)
        assertTrue(footer.text.toString(), footer.text.startsWith("Alarm: ") && footer.text.endsWith(" · Muslim World League"))
        picture("widget-times-full", full, 330, 350)

        // In the afternoon Asr is gold, what came before it is faded, and Maghrib is named as next.
        fun drawn(hour: Int, minute: Int = 0) =
            PrayerWidgets.timesViews(app, at(hour, minute), beirut, size(300, 200)).apply(app, FrameLayout(app))
        fun View.faded() = nameColor() == app.getColor(R.color.widget_passed)
        val afternoon = drawn(15)
        assertEquals(listOf("Asr"), rows(afternoon).filter { it.background != null }.map { it.name() })
        assertEquals(listOf("Fajr", "Sunrise", "Duha", "Dhuhr"), rows(afternoon).filter { it.faded() }.map { it.name() })
        assertEquals(View.VISIBLE, afternoon.findViewById<View>(R.id.widget_next).visibility)
        assertTrue(afternoon.text(R.id.widget_next).startsWith("Next: Maghrib "))
        // In the morning no prayer is current: Dhuhr is gold and the headline already names it.
        val morning = drawn(9)
        assertEquals("Dhuhr starts in", morning.text(R.id.widget_headline))
        assertEquals(listOf("Dhuhr"), rows(morning).filter { it.background != null }.map { it.name() })
        assertEquals(View.GONE, morning.findViewById<View>(R.id.widget_next).visibility)
        // Late at night the table is already the new day's: Fajr is gold and nothing has passed.
        val night = drawn(3, 30)
        assertEquals("Isha ends in", night.text(R.id.widget_headline))
        assertEquals(listOf("Fajr"), rows(night).filter { it.background != null }.map { it.name() })
        assertTrue(rows(night).none { it.faded() })

        // Every size the home screen is offered draws all nine times.
        PrayerWidgets.TIMES_SIZES.forEach { size ->
            val view = PrayerWidgets.timesViews(app, state, beirut, size).apply(app, FrameLayout(app))
            assertEquals("$size", names, rows(view).map { it.name() }.filter { it.isNotEmpty() })
        }

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
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun `the current prayer widget grows into the date and then the table`() {
        val state = now()
        fun draw(width: Int, height: Int) =
            PrayerWidgets.currentViews(app, state, beirut, size(width, height)).apply(app, FrameLayout(app))
        fun View.shows(id: Int) = findViewById<View>(id).visibility == View.VISIBLE

        // One cell tall and squeezed: the prayer and the countdown.
        val tiny = draw(150, 50)
        assertTrue(tiny.shows(R.id.widget_countdown))
        assertFalse(tiny.shows(R.id.widget_next) || tiny.shows(R.id.widget_date) || tiny.shows(R.id.widget_table))

        val small = draw(156, 84)
        assertTrue(small.shows(R.id.widget_next) && !small.shows(R.id.widget_date) && !small.shows(R.id.widget_table))
        assertTrue(small.findViewById<TextView>(R.id.widget_next).text.startsWith("Next: "))
        picture("widget-current", small, 156, 84)

        val square = draw(156, 180)
        assertTrue(square.shows(R.id.widget_date) && !square.shows(R.id.widget_table))
        assertTrue(rows(square).isEmpty())
        picture("widget-current-square", square, 156, 180)

        val wide = draw(330, 200)
        assertTrue(wide.shows(R.id.widget_table) && wide.shows(R.id.table_end))
        assertEquals("the next prayer is named only where the headline doesn't", state.current != null, wide.shows(R.id.widget_next))
        assertEquals(5, rows(wide, R.id.table_start).size)
        assertEquals(5, rows(wide, R.id.table_end).size)
        picture("widget-current-wide", wide, 330, 200)

        val tall = draw(200, 320)
        assertTrue(tall.shows(R.id.widget_table) && !tall.shows(R.id.table_end))
        assertEquals(9, rows(tall, R.id.table_start).size)
        assertEquals(1, rows(tall).count { it.background != null })
        picture("widget-current-tall", tall, 200, 320)
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-420dpi")
    fun `the widgets speak Arabic and say so when there is no location`() {
        val names = listOf("الفجر", "الشروق", "الضحى", "الظهر", "العصر", "المغرب", "العشاء", "منتصف الليل", "قيام الليل")
        val view = PrayerWidgets.timesViews(app, now(), beirut, size(300, 150)).apply(app, FrameLayout(app))
        assertEquals(names.take(5), rows(view, R.id.table_start).map { it.name() })
        assertEquals(names.drop(5) + "", rows(view, R.id.table_end).map { it.name() })
        picture("ar-widget-times", view, 300, 150)

        val full = PrayerWidgets.timesViews(app, now(), beirut, size(330, 350)).apply(app, FrameLayout(app))
        assertEquals(names, rows(full).drop(1).map { it.name() })
        assertTrue(texts(full).containsAll(listOf("البداية", "النهاية")))
        assertEquals(listOf("الضحى", "قيام الليل"), rows(full).filter { it.mark() == "ring" }.map { it.name() })
        assertTrue(full.findViewById<TextView>(R.id.widget_footer).text.startsWith("المنبّه: "))
        picture("ar-widget-times-full", full, 330, 350)

        val empty = PrayerWidgets.currentViews(app, null, beirut, size(330, 350)).apply(app, FrameLayout(app))
        assertEquals(listOf("افتح مواقيت الصلاة لتحديد موقعك"), texts(empty).filter { it.isNotEmpty() })
        assertEquals(View.GONE, empty.findViewById<View>(R.id.widget_countdown).visibility)
        assertEquals(View.GONE, empty.findViewById<View>(R.id.widget_table).visibility)
        val emptyTimes = PrayerWidgets.timesViews(app, null, beirut, size(330, 350)).apply(app, FrameLayout(app))
        assertEquals(listOf("افتح مواقيت الصلاة لتحديد موقعك"), texts(emptyTimes).filter { it.isNotEmpty() })
        assertTrue(rows(emptyTimes).isEmpty())
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

    @Test
    fun `a widget made bigger on the home screen is drawn again with more in it`() {
        store.save(beirut)
        val manager = AppWidgetManager.getInstance(app)
        val widgets = shadowOf(manager)
        val id = widgets.createWidget(PrayerTimesWidget::class.java, R.layout.widget_times)
        assertEquals("two columns to begin with", 5, rows(widgets.getViewFor(id), R.id.table_end).size)

        // A home screen that only says how big the widget now is.
        val bigger = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 330)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 600)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 350)
        }
        assertEquals(size(330, 350), PrayerWidgets.sizeOf(app, bigger))
        manager.updateAppWidgetOptions(id, bigger)
        PrayerTimesWidget().onAppWidgetOptionsChanged(app, manager, id, bigger)
        val table = rows(widgets.getViewFor(id), R.id.table_start)
        assertEquals("one column under its headings", 10, table.size)
        assertEquals("Ends", table.first().text(R.id.row_ends))
        assertEquals(View.VISIBLE, widgets.getViewFor(id).findViewById<View>(R.id.widget_footer).visibility)

        // One from Android 12 lists the sizes and picks among the drawings itself.
        val listed = Bundle(bigger).apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(size(330, 350), size(600, 200)))
        }
        assertTrue(PrayerWidgets.choosesSize(listed))
        manager.updateAppWidgetOptions(id, listed)
        PrayerTicker.refresh(app)
        assertTrue(texts(widgets.getViewFor(id)).containsAll(listOf("Fajr", "Qiyam")))
    }

    private fun size(width: Int, height: Int) = SizeF(width.toFloat(), height.toFloat())

    /** The rows of a widget's table: of one of its columns, or of both. */
    private fun rows(view: View, column: Int? = null): List<View> =
        (if (column != null) listOf(column) else listOf(R.id.table_start, R.id.table_end))
            .map { view.findViewById<ViewGroup>(it) }
            .flatMap { group -> (0 until group.childCount).map(group::getChildAt) }

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    private fun View.name() = text(R.id.row_name)

    /** The shape before a row's name: "dot" for an obligatory prayer, "ring" for a voluntary one, else "none". */
    private val rings = setOf(R.drawable.widget_mark_ring, R.drawable.widget_mark_ring_passed)

    private fun View.mark(): String {
        val mark = findViewById<ImageView>(R.id.row_mark)
        return when {
            mark.visibility != View.VISIBLE -> "none"
            shadowOf(mark.drawable).createdFromResId in rings -> "ring"
            else -> "dot"
        }
    }

    private fun View.nameColor() = findViewById<TextView>(R.id.row_name).currentTextColor

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

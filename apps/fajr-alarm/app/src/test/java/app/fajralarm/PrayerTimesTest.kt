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
import app.fajralarm.core.AlertSound
import app.fajralarm.core.AsrEnd
import app.fajralarm.core.IshaEnd
import app.fajralarm.core.PrayerClock
import app.fajralarm.core.PrayerMoment
import app.fajralarm.core.PrayerNow
import app.fajralarm.core.PrayerTime
import app.fajralarm.core.WidgetColors
import app.fajralarm.core.WidgetTheme
import app.fajralarm.data.SettingsStore
import app.fajralarm.prayer.PrayerTicker
import app.fajralarm.ui.MainActivity
import app.fajralarm.ui.PrayerAlertActivity
import app.fajralarm.widget.CurrentPrayerWidget
import app.fajralarm.widget.PrayerTimesWidget
import app.fajralarm.widget.PrayerWidgets
import app.fajralarm.widget.WidgetPalette
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
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
    private fun at(hour: Int, minute: Int, settings: AlarmSettings = beirut): PrayerNow = PrayerClock.at(
        ZonedDateTime.of(LocalDate.of(2026, 11, 25), LocalTime.of(hour, minute), ZoneId.of("Asia/Beirut")),
        settings.calcParams()!!,
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
            compose.onNodeWithText("MOON").assertIsDisplayed()
            listOf("Imsak", "Shuruq", "Duha", "Dhuhr", "Asr", "Maghrib", "Isha", "Midnight", "Qiyam (last third)").forEach {
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
            // The switch for full-screen alerts is under the list.
            compose.onNodeWithText("Full-screen alert").performScrollTo().performClick()
            compose.waitForIdle()
            assertTrue(store.load().fullScreenAlerts)
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
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun `with full-screen alerts on a time opens over the whole screen`() {
        store.save(beirut.copy(alerts = setOf(PrayerTime.ASR)))
        store.awaitedPrayer = PrayerTime.ASR to System.currentTimeMillis() - 1_000
        PrayerTicker.refresh(app)
        assertNull("a plain notification by default", notifications.single().fullScreenIntent)

        store.save(beirut.copy(alerts = setOf(PrayerTime.ASR), fullScreenAlerts = true))
        val came = System.currentTimeMillis() - 1_000
        store.awaitedPrayer = PrayerTime.ASR to came
        PrayerTicker.refresh(app)
        val alert = notifications.single { it.channelId == FajrAlarmApp.CHANNEL_PRAYER }
        val screen = shadowOf(alert.fullScreenIntent).savedIntent
        assertEquals(PrayerAlertActivity::class.java.name, screen.component?.className)

        // It is heard the way it was set: by sound, by vibration, or not at all.
        fun channelFor(sound: AlertSound): String {
            store.save(beirut.copy(alerts = setOf(PrayerTime.ASR), fullScreenAlerts = true, alertSound = sound))
            store.awaitedPrayer = PrayerTime.ASR to System.currentTimeMillis() - 1_000
            PrayerTicker.refresh(app)
            return notifications.single { it.fullScreenIntent != null }.channelId
        }
        val manager = app.getSystemService(NotificationManager::class.java)
        assertEquals(FajrAlarmApp.CHANNEL_PRAYER, channelFor(AlertSound.SOUND))
        manager.getNotificationChannel(channelFor(AlertSound.VIBRATE)).let {
            assertEquals(FajrAlarmApp.CHANNEL_PRAYER_VIBRATE, it.id)
            assertNull(it.sound)
            assertTrue(it.shouldVibrate())
        }
        manager.getNotificationChannel(channelFor(AlertSound.SILENT)).let {
            assertEquals(FajrAlarmApp.CHANNEL_PRAYER_SILENT, it.id)
            assertNull(it.sound)
            assertFalse(it.shouldVibrate())
            assertEquals("still able to open over the lock screen", NotificationManager.IMPORTANCE_HIGH, it.importance)
        }
        assertEquals(AlertSound.SILENT, store.load().alertSound)

        // The screen names the prayer and its time, and answering it takes the notification down too.
        ActivityScenario.launch<PrayerAlertActivity>(screen).use { scenario ->
            compose.waitForIdle()
            compose.onNodeWithText("Asr").assertIsDisplayed()
            compose.onNodeWithText("It's time to pray.").assertIsDisplayed()
            captureScreenRoboImage("build/screens/en-prayer-alert.png")
            compose.onNodeWithText("OK").performClick()
            compose.waitForIdle()
            assertTrue(notifications.none { it.fullScreenIntent != null })
            scenario.onActivity { assertTrue(it.isFinishing) }
        }
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

    private val names = listOf("Imsak", "Fajr", "Shuruq", "Duha", "Dhuhr", "Asr", "Maghrib", "Isha", "Midnight", "Qiyam")

    private fun palette(theme: WidgetTheme) = WidgetPalette.of(beirut.copy(widgetTheme = theme))

    /** Whether a row of the table is the highlighted one. */
    private fun View.gold() = findViewById<View>(R.id.row_highlight)?.visibility == View.VISIBLE

    private fun times(state: PrayerNow?, width: Int, height: Int, settings: AlarmSettings = beirut): View =
        PrayerWidgets.timesViews(app, state, settings, size(width, height)).apply(app, FrameLayout(app))

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun `the prayer times widget is a table of all nine times with the current prayer marked`() {
        val afternoon = at(15, 0)
        val night = WidgetPalette.of(beirut)

        // At its smallest: the table in two columns of five, read down the first and then the second.
        val small = times(afternoon, 300, 120)
        assertEquals(names.take(5), rows(small, R.id.table_start).map { it.name() })
        assertEquals(names.drop(5), rows(small, R.id.table_end).map { it.name() })
        assertEquals("Asr ends in", small.text(R.id.widget_headline))
        assertEquals(listOf("Asr"), rows(small).filter { it.gold() }.map { it.name() })
        assertEquals(
            "what came before Asr is faded",
            names.take(5), rows(small).filter { it.nameColor() == night.passed.light }.map { it.name() },
        )
        assertEquals(View.GONE, small.findViewById<View>(R.id.widget_next_line).visibility)
        assertEquals(View.GONE, small.findViewById<View>(R.id.widget_footer).visibility)
        assertTrue("no room for AM and PM", rows(small).none { it.text(R.id.row_time).endsWith("M") })
        assertTrue("nor for a countdown in the row", rows(small).none { it.findViewById<View>(R.id.row_countdown) != null })
        // Each prayer has its icon before its name; Imsak, Shuruq and midnight, which are not prayers, have none.
        assertEquals(
            mapOf(
                "Imsak" to null, "Fajr" to "ic_prayer_fajr", "Shuruq" to null, "Duha" to "ic_prayer_duha",
                "Dhuhr" to "ic_prayer_dhuhr", "Asr" to "ic_prayer_asr", "Maghrib" to "ic_prayer_maghrib",
                "Isha" to "ic_prayer_isha", "Midnight" to null, "Qiyam" to "ic_prayer_qiyam",
            ),
            rows(small).associate { it.name() to it.icon() },
        )

        // Four cells by two: the current prayer's row is twice as tall and counts down to its end.
        val counting = times(afternoon, 300, 150)
        val gold = rows(counting).single { it.gold() }
        assertEquals("Asr", gold.name())
        assertEquals("ends in", gold.text(R.id.row_count_label))
        assertTrue(gold.findViewById<View>(R.id.row_countdown) is Chronometer)
        assertEquals("only the gold row counts down", 1, rows(counting).count { it.findViewById<View>(R.id.row_countdown) != null })
        assertEquals("an empty row keeps the columns level", names.take(5) + "", rows(counting, R.id.table_start).map { it.name() })
        assertEquals(names.drop(5), rows(counting, R.id.table_end).map { it.name() })
        // With no prayer current the gold row is the next one, counting down to its start.
        val morning = times(at(9, 0), 300, 150)
        assertEquals("Dhuhr starts in", morning.text(R.id.widget_headline))
        rows(morning).single { it.gold() }.let {
            assertEquals("Dhuhr", it.name())
            assertEquals("starts in", it.text(R.id.row_count_label))
        }
        // There the gold row is in the first column, so the second gets the empty row.
        assertEquals(names.take(5), rows(morning, R.id.table_start).map { it.name() })
        assertEquals(names.drop(5) + "", rows(morning, R.id.table_end).map { it.name() })
        // Late at night the table is already the new day's: Fajr is gold and nothing has passed.
        val late = times(at(3, 30), 300, 150)
        assertEquals("Isha ends in", late.text(R.id.widget_headline))
        assertEquals("Fajr", rows(late).single { it.gold() }.name())
        assertTrue(rows(late).none { it.nameColor() == night.passed.light })

        // A little taller: a second countdown, to the next prayer, where the current one ends before it.
        val dawn = times(at(5, 30), 300, 200)
        assertEquals("Fajr ends in", dawn.text(R.id.widget_headline))
        assertEquals(View.VISIBLE, dawn.findViewById<View>(R.id.widget_next_line).visibility)
        assertEquals("Next: Dhuhr in", dawn.text(R.id.widget_next))
        fun nextLine(state: PrayerNow, settings: AlarmSettings = beirut) =
            times(state, 300, 200, settings).findViewById<View>(R.id.widget_next_line).visibility
        assertEquals("Asr ends when Maghrib starts: one countdown says both", View.GONE, nextLine(afternoon))
        assertEquals("the headline already counts down to Dhuhr", View.GONE, nextLine(at(9, 0)))
        val yellow = beirut.copy(asrEnd = AsrEnd.SUN_YELLOWS)
        assertEquals("Asr shown ending before Maghrib", View.VISIBLE, nextLine(at(15, 0, yellow), yellow))

        // Taller still: one column, each prayer with its start and end, and a bell beside the times that notify.
        val belled = beirut.copy(alerts = setOf(PrayerTime.DHUHR, PrayerTime.MIDNIGHT))
        val tall = times(afternoon, 250, 270, belled)
        assertEquals(names, rows(tall, R.id.table_start).map { it.name() })
        assertEquals(View.GONE, tall.findViewById<View>(R.id.table_end).visibility)
        assertEquals("ends in", rows(tall).single { it.gold() }.text(R.id.row_count_label))
        val ranges = rows(tall).associate { it.name() to it.text(R.id.row_time) }
        assertEquals(
            "every prayer has a start and an end; Imsak, Shuruq and midnight are one moment",
            setOf("Imsak", "Shuruq", "Midnight"), ranges.filterValues { " – " !in it }.keys,
        )
        assertEquals("Fajr ends at sunrise", ranges.getValue("Shuruq"), ranges.getValue("Fajr").substringAfter(" – "))
        assertEquals("Asr starts when Dhuhr ends", ranges.getValue("Asr").substringBefore(" – "), ranges.getValue("Dhuhr").substringAfter(" – "))
        assertEquals("Qiyam ends at Fajr, as Isha does", ranges.getValue("Isha").substringAfter(" – "), ranges.getValue("Qiyam").substringAfter(" – "))
        assertEquals(
            listOf("Dhuhr", "Midnight"),
            rows(tall).filter { it.findViewById<View>(R.id.row_bell).visibility == View.VISIBLE }.map { it.name() },
        )
        assertEquals(View.GONE, tall.findViewById<View>(R.id.widget_footer).visibility)

        // Tall and wide: when each prayer ends, and the alarm and the method at the bottom.
        val full = times(afternoon, 330, 350, belled)
        val table = rows(full, R.id.table_start)
        assertEquals(listOf("", "Begins", "Ends"), listOf(R.id.row_name, R.id.row_time, R.id.row_ends).map { table.first().text(it) })
        assertEquals(names, table.drop(1).map { it.name() })
        val ends = table.drop(1).associate { it.name() to it.text(R.id.row_ends) }
        val begins = table.drop(1).associate { it.name() to it.text(R.id.row_time) }
        assertEquals("Fajr ends at sunrise", begins["Shuruq"], ends["Fajr"])
        assertEquals("Dhuhr ends at Asr", begins["Asr"], ends["Dhuhr"])
        assertEquals("Asr ends at Maghrib", begins["Maghrib"], ends["Asr"])
        assertEquals("Maghrib ends at Isha", begins["Isha"], ends["Maghrib"])
        assertEquals("Imsak, Shuruq and midnight are single moments", listOf("", "", ""), listOf("Imsak", "Shuruq", "Midnight").map(ends::getValue))
        assertEquals("Qiyam ends at the next Fajr", ends.getValue("Isha"), ends.getValue("Qiyam"))
        val footer = full.findViewById<TextView>(R.id.widget_footer)
        assertEquals(View.VISIBLE, footer.visibility)
        assertTrue(footer.text.toString(), footer.text.startsWith("Alarm: ") && footer.text.endsWith(" · Muslim World League"))

        // The end a school sets inside a prayer's time is the one the table shows.
        val school = beirut.copy(asrEnd = AsrEnd.SHADOW_TWICE, ishaEnd = IshaEnd.FIRST_THIRD)
        val chosen = rows(times(at(15, 0, school), 330, 350, school), R.id.table_start).drop(1)
            .associate { it.name() to it.text(R.id.row_ends) }
        assertTrue("Asr no longer ends at Maghrib: $chosen", chosen["Asr"] != ends["Asr"] && chosen["Asr"] != "")
        assertTrue("Isha no longer ends at Fajr: $chosen", chosen["Isha"] != ends["Isha"] && chosen["Isha"] != "")
        assertEquals("the others are as they were", ends - "Asr" - "Isha", chosen - "Asr" - "Isha")

        // Every size the home screen is offered draws all nine times.
        PrayerWidgets.TIMES_SIZES.forEach { size ->
            val view = PrayerWidgets.timesViews(app, afternoon, beirut, size).apply(app, FrameLayout(app))
            assertEquals("$size", names, rows(view).map { it.name() }.filter { it.isNotEmpty() })
        }

        // Pictures of this moment, so that the countdowns in them make sense.
        picture("widget-times", times(now(), 300, 150), 300, 150)
        picture("widget-times-medium", times(now(), 300, 200), 300, 200)
        picture("widget-times-tall", times(now(), 250, 270, belled), 250, 270)
        picture("widget-times-full", times(now(), 330, 350, belled), 330, 350)
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
    fun `the widgets take the colours of the chosen theme`() {
        val state = at(15, 0)
        fun drawn(theme: WidgetTheme) = times(state, 300, 200, beirut.copy(widgetTheme = theme))
        fun View.color(id: Int) = findViewById<TextView>(id).currentTextColor

        for (theme in WidgetTheme.values()) {
            val palette = palette(theme)
            val view = drawn(theme)
            assertEquals("$theme headline", palette.accent.light, view.color(R.id.widget_headline))
            assertEquals("$theme countdown", palette.text.light, view.color(R.id.widget_countdown))
            assertEquals("$theme date", palette.muted.light, view.color(R.id.widget_date))
            assertEquals("$theme background", palette.backgroundAlpha, view.findViewById<ImageView>(R.id.widget_background).imageAlpha)
            val gold = rows(view).single { it.gold() }
            assertEquals("$theme gold row", palette.onGold.light, gold.nameColor())
            assertEquals("$theme gold row countdown", palette.onGold.light, gold.color(R.id.row_countdown))
            assertEquals("$theme passed", palette.passed.light, rows(view).first().nameColor())
            assertEquals("$theme to come", palette.text.light, rows(view).single { it.name() == "Maghrib" }.nameColor())
            picture("widget-theme-${theme.name.lowercase()}", times(now(), 300, 200, beirut.copy(widgetTheme = theme)), 300, 200)
        }
        assertEquals("the default is see-through", WidgetTheme.GLASS, AlarmSettings().widgetTheme)
        assertTrue("it shows the wallpaper", palette(WidgetTheme.GLASS).backgroundAlpha < 128)

        // Your own colours: the three you pick, and how solid the background is.
        val mine = beirut.copy(
            widgetTheme = WidgetTheme.CUSTOM,
            widgetColors = WidgetColors(background = 0x1F3D2B, backgroundOpacity = 40, text = 0xFBF8F1, highlight = 0x8AB4F8),
        )
        val own = times(state, 300, 200, mine)
        assertEquals(0xFFFBF8F1.toInt(), own.color(R.id.widget_countdown))
        assertEquals(0xFF8AB4F8.toInt(), own.color(R.id.widget_headline))
        assertEquals(102, own.findViewById<ImageView>(R.id.widget_background).imageAlpha)
        assertEquals("dark text on a pale highlight", 0xFF1A2340.toInt(), rows(own).single { it.gold() }.nameColor())
        val dark = times(state, 300, 200, mine.copy(widgetColors = mine.widgetColors.copy(highlight = 0x4A1F2A)))
        assertEquals("white text on a dark one", 0xFFFFFFFF.toInt(), rows(dark).single { it.gold() }.nameColor())
        picture("widget-theme-custom-green", times(now(), 300, 200, mine), 300, 200)

        // "Follow the phone" is the light theme by day and the night theme once the phone goes dark.
        assertEquals(palette(WidgetTheme.LIGHT).text.light, drawn(WidgetTheme.SYSTEM).color(R.id.widget_countdown))
        RuntimeEnvironment.setQualifiers("+night")
        assertEquals(palette(WidgetTheme.NIGHT).text.light, drawn(WidgetTheme.SYSTEM).color(R.id.widget_countdown))
        assertEquals("the others stay as they are", palette(WidgetTheme.LIGHT).text.light, drawn(WidgetTheme.LIGHT).color(R.id.widget_countdown))

        // The choice is kept.
        store.save(mine.copy(asrEnd = AsrEnd.SUN_YELLOWS, ishaEnd = IshaEnd.MIDNIGHT, imsakMinutes = 15, fullScreenAlerts = true))
        store.load().let {
            assertEquals(WidgetTheme.CUSTOM, it.widgetTheme)
            assertEquals(mine.widgetColors, it.widgetColors)
            assertEquals(15, it.imsakMinutes)
            assertTrue(it.fullScreenAlerts)
            assertEquals(AsrEnd.SUN_YELLOWS, it.asrEnd)
            assertEquals(IshaEnd.MIDNIGHT, it.ishaEnd)
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun `the current prayer widget grows into the date and then the table`() {
        val state = at(15, 0)
        fun draw(width: Int, height: Int, of: PrayerNow = state) =
            PrayerWidgets.currentViews(app, of, beirut, size(width, height)).apply(app, FrameLayout(app))
        fun View.shows(id: Int) = findViewById<View>(id).visibility == View.VISIBLE

        // One cell: the prayer's name over the countdown.
        val cell = draw(60, 60)
        assertEquals("Asr", cell.text(R.id.widget_headline))
        assertTrue(cell.shows(R.id.widget_countdown))
        assertFalse(cell.shows(R.id.widget_next) || cell.shows(R.id.widget_date) || cell.shows(R.id.widget_table))
        assertEquals("with no prayer current it is the next one", "Dhuhr", draw(60, 60, at(9, 0)).text(R.id.widget_headline))

        // One cell tall and two wide, squeezed: the sentence and the countdown.
        val tiny = draw(150, 50)
        assertEquals("Asr ends in", tiny.text(R.id.widget_headline))
        assertTrue(tiny.shows(R.id.widget_countdown))
        assertFalse(tiny.shows(R.id.widget_next) || tiny.shows(R.id.widget_date) || tiny.shows(R.id.widget_table))

        val small = draw(156, 84)
        assertTrue(small.shows(R.id.widget_next) && !small.shows(R.id.widget_date) && !small.shows(R.id.widget_table))
        assertTrue(small.text(R.id.widget_next).startsWith("Next: Maghrib "))

        val square = draw(156, 180)
        assertTrue(square.shows(R.id.widget_date) && !square.shows(R.id.widget_table))
        assertTrue(rows(square).isEmpty())

        val wide = draw(330, 200)
        assertTrue(wide.shows(R.id.widget_table) && wide.shows(R.id.table_end) && wide.shows(R.id.widget_next))
        assertEquals(5, rows(wide, R.id.table_start).size)
        assertEquals(5, rows(wide, R.id.table_end).size)
        assertTrue("the big countdown is enough", rows(wide).none { it.findViewById<View>(R.id.row_countdown) != null })
        assertFalse("the headline already names the next prayer", draw(330, 200, at(9, 0)).shows(R.id.widget_next))

        val tall = draw(200, 320)
        assertTrue(tall.shows(R.id.widget_table) && !tall.shows(R.id.table_end))
        assertEquals(names, rows(tall, R.id.table_start).map { it.name() })
        assertEquals(1, rows(tall).count { it.gold() })

        fun shot(name: String, width: Int, height: Int) = picture(
            name, PrayerWidgets.currentViews(app, now(), beirut, size(width, height)).apply(app, FrameLayout(app)), width, height,
        )
        shot("widget-current-cell", 72, 72)
        shot("widget-current", 156, 84)
        shot("widget-current-square", 156, 180)
        shot("widget-current-wide", 330, 200)
        shot("widget-current-tall", 200, 320)
    }

    @Test
    @Config(qualifiers = "ar-rLB-w411dp-h891dp-420dpi")
    fun `the widgets speak Arabic and say so when there is no location`() {
        val arabic = listOf("الإمساك", "الفجر", "الشروق", "الضحى", "الظهر", "العصر", "المغرب", "العشاء", "منتصف الليل", "قيام الليل")
        val view = times(at(15, 0), 300, 150)
        assertEquals(arabic.take(5) + "", rows(view, R.id.table_start).map { it.name() })
        assertEquals(arabic.drop(5), rows(view, R.id.table_end).map { it.name() })
        assertEquals("ينتهي بعد", rows(view).single { it.gold() }.text(R.id.row_count_label))
        picture("ar-widget-times", times(now(), 300, 150), 300, 150)

        val full = times(at(15, 0), 330, 350)
        assertEquals(arabic, rows(full).drop(1).map { it.name() })
        assertTrue(texts(full).containsAll(listOf("البداية", "النهاية")))
        assertTrue(full.findViewById<TextView>(R.id.widget_footer).text.startsWith("المنبّه: "))
        picture("ar-widget-times-full", times(now(), 330, 350), 330, 350)

        val empty = PrayerWidgets.currentViews(app, null, beirut, size(330, 350)).apply(app, FrameLayout(app))
        assertEquals(listOf("افتح مواقيت الصلاة لتحديد موقعك"), texts(empty).filter { it.isNotEmpty() })
        assertEquals(View.GONE, empty.findViewById<View>(R.id.widget_countdown).visibility)
        assertEquals(View.GONE, empty.findViewById<View>(R.id.widget_table).visibility)
        val emptyTimes = times(null, 330, 350)
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
        assertEquals("one column under its headings", 11, table.size)
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

    private fun View.nameColor() = findViewById<TextView>(R.id.row_name).currentTextColor

    /** The icon before a row's name, by its file's name; null where the row has none. */
    private fun View.icon(): String? {
        val mark = findViewById<ImageView>(R.id.row_mark)
        return if (mark.visibility != View.VISIBLE) null else app.resources.getResourceEntryName(shadowOf(mark.drawable).createdFromResId)
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

package app.fajralarm

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Context
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
import androidx.compose.ui.test.assert
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
import app.fajralarm.core.LockScreenCountdown
import app.fajralarm.core.PrayerAlert
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
    fun `the main screen lists every time of the day and its icon sets what the phone does`() {
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
            // Fajr has the alarm, not a notice of its own: its icon says the alarm rings.
            compose.onNodeWithContentDescription("Notify at Fajr").assertDoesNotExist()
            compose.onNodeWithContentDescription("The alarm rings").assertIsDisplayed()

            // The icon beside a time opens the choice of what the phone does when it arrives.
            fun choose(what: String) {
                compose.onNodeWithText(what).performClick()
                compose.waitForIdle()
            }
            val dhuhr = compose.onNodeWithContentDescription("Notify at Dhuhr")
            dhuhr.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Nothing"))
            dhuhr.performScrollTo().performClick()
            compose.waitForIdle()
            listOf("Nothing", "Notification", "Full-screen notice", "Sound", "Vibration only", "Silent").forEach {
                compose.onNodeWithText(it).assertIsDisplayed()
            }
            choose("Notification")
            assertEquals(mapOf(PrayerTime.DHUHR to PrayerAlert(fullScreen = false, sound = AlertSound.SOUND)), store.load().alerts)
            // The menu stays open, so the sound and the kind of notice are set in one go.
            choose("Vibration only")
            choose("Full-screen notice")
            assertEquals(mapOf(PrayerTime.DHUHR to PrayerAlert(fullScreen = true, sound = AlertSound.VIBRATE)), store.load().alerts)
            dhuhr.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Full-screen notice, Vibration only"))
            captureScreenRoboImage("build/screens/en-alert-menu.png")
            choose("Nothing")
            assertEquals(emptyMap<PrayerTime, PrayerAlert>(), store.load().alerts)
            compose.onNodeWithText("Vibration only").assertDoesNotExist()

            // A few set, for the picture.
            store.save(
                beirut.copy(
                    alerts = mapOf(
                        PrayerTime.DHUHR to PrayerAlert(),
                        PrayerTime.ASR to PrayerAlert(sound = AlertSound.VIBRATE),
                        PrayerTime.MAGHRIB to PrayerAlert(fullScreen = true),
                        PrayerTime.ISHA to PrayerAlert(fullScreen = true, sound = AlertSound.SILENT),
                    ),
                ),
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
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
        store.save(beirut.copy(alerts = bells(belled.prayer)))
        PrayerTicker.refresh(app)
        assertEquals(belled.prayer to belled.millis(), store.awaitedPrayer)
        assertEquals(listOf(belled.millis() to true), queued())
    }

    @Test
    fun `a bell queues the next time of the day and it gets its notification when it comes`() {
        store.save(beirut.copy(alerts = bells(*PrayerTime.values())))
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
    fun `the lock screen notice can be kept for the last quarter of an hour only`() {
        store.save(beirut.copy(lockScreenCountdown = LockScreenCountdown.ENDING))
        // What the countdown runs to right now: the end of the current prayer, or the start of the next one.
        val target = now().let { it.currentEnds ?: it.next.at }.toInstant().toEpochMilli()
        fun minutesBefore(minutes: Long) = target - minutes * 60_000

        // Twenty minutes before: nothing is up, and the phone will be woken to put it up at fifteen.
        PrayerTicker.refresh(app, minutesBefore(20))
        assertTrue(notifications.none { it.channelId == FajrAlarmApp.CHANNEL_NOW })
        assertEquals(listOf(minutesBefore(15) to true), queued())

        // Nine minutes before: it is up, silent, counting down to that moment.
        PrayerTicker.refresh(app, minutesBefore(9))
        val notice = notifications.single { it.channelId == FajrAlarmApp.CHANNEL_NOW }
        assertTrue(notice.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue("counts down to $target, was ${notice.`when`}", abs(notice.`when` - target) < 2_000)
        assertEquals("woken when it runs out, to take it down", listOf(target to true), queued())

        // Once that prayer's time is over it comes down until the next one is ending.
        PrayerTicker.refresh(app, target + 60_000)
        assertTrue(notifications.none { it.channelId == FajrAlarmApp.CHANNEL_NOW })

        // An on/off switch saved by an earlier version still means "always".
        app.getSharedPreferences("fajr_alarm", Context.MODE_PRIVATE).edit()
            .remove("lock_screen_notice").putBoolean("lock_screen_countdown", true).apply()
        assertEquals(LockScreenCountdown.ALWAYS, store.load().lockScreenCountdown)
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun `with full-screen alerts on a time opens over the whole screen`() {
        fun arrive(alert: PrayerAlert, prayer: PrayerTime = PrayerTime.ASR) {
            store.save(beirut.copy(alerts = mapOf(PrayerTime.ASR to alert, PrayerTime.MAGHRIB to PrayerAlert())))
            store.awaitedPrayer = prayer to System.currentTimeMillis() - 1_000
            PrayerTicker.refresh(app)
        }
        arrive(PrayerAlert())
        assertNull("a plain notification by default", notifications.single().fullScreenIntent)

        arrive(PrayerAlert(fullScreen = true))
        val alert = notifications.single { it.channelId == FajrAlarmApp.CHANNEL_PRAYER }
        val screen = shadowOf(alert.fullScreenIntent).savedIntent
        assertEquals(PrayerAlertActivity::class.java.name, screen.component?.className)
        // Each time has its own: Maghrib, set to a plain notification, stays one.
        arrive(PrayerAlert(fullScreen = true), PrayerTime.MAGHRIB)
        assertNull(notifications.single().fullScreenIntent)

        // It is heard the way it was set: by sound, by vibration, or not at all.
        fun channelFor(sound: AlertSound): String {
            arrive(PrayerAlert(fullScreen = true, sound = sound))
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
        assertEquals(PrayerAlert(fullScreen = true, sound = AlertSound.SILENT), store.load().alerts[PrayerTime.ASR])

        // Version 1.75 kept one switch and one sound for every time: they carry over to each bell.
        app.getSharedPreferences("fajr_alarm", Context.MODE_PRIVATE).edit().clear()
            .putString("lat", "33.8938").putString("lng", "35.5018")
            .putStringSet("prayer_alerts", setOf("DHUHR", "ISHA"))
            .putBoolean("full_screen_alerts", true).putString("alert_sound", "VIBRATE").apply()
        assertEquals(
            mapOf(PrayerTime.DHUHR to PrayerAlert(true, AlertSound.VIBRATE), PrayerTime.ISHA to PrayerAlert(true, AlertSound.VIBRATE)),
            store.load().alerts,
        )
        arrive(PrayerAlert(fullScreen = true))

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
        store.save(beirut.copy(alerts = bells(PrayerTime.DHUHR)))
        store.awaitedPrayer = PrayerTime.ASR to System.currentTimeMillis() - 1_000
        PrayerTicker.refresh(app)
        assertTrue(notifications.isEmpty())

        store.awaitedPrayer = PrayerTime.DHUHR to System.currentTimeMillis() - 20 * 60_000
        PrayerTicker.refresh(app)
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun `the lock screen notice counts down to the end of the current prayer`() {
        store.save(beirut.copy(lockScreenCountdown = LockScreenCountdown.ALWAYS))
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

    /** These times with their bell on: a plain notification with its sound. */
    private fun bells(vararg times: PrayerTime) = times.associateWith { PrayerAlert() }

    /** Whether a row of the table shows the time left beside the name. */
    private fun View.counts() = findViewById<View>(R.id.row_left)?.visibility == View.VISIBLE

    private fun palette(theme: WidgetTheme) = WidgetPalette.of(beirut.copy(widgetTheme = theme))

    /** Whether a row of the table is the highlighted one. */
    private fun View.gold() = findViewById<View>(R.id.row_highlight)?.visibility == View.VISIBLE

    private fun times(state: PrayerNow?, width: Int, height: Int, settings: AlarmSettings = beirut): View =
        PrayerWidgets.timesViews(app, state, settings, size(width, height)).apply(app, FrameLayout(app))

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
    fun `the prayer times widget is a table of the day's times with the current prayer marked`() {
        val afternoon = at(15, 0)
        val night = WidgetPalette.of(beirut)

        // At its smallest: the table in two columns of five, read down the first and then the second.
        val small = times(afternoon, 300, 120)
        assertEquals(names.take(5), rows(small, R.id.table_start).map { it.name() })
        assertEquals(names.drop(5), rows(small, R.id.table_end).map { it.name() })
        // The top line is the two dates; the time left is in the gold row and nowhere else.
        assertEquals("15 Jumada al-Akhirah 1448", small.text(R.id.widget_headline))
        assertEquals("Wed 25 Nov", small.text(R.id.widget_date))
        assertEquals("narrow: without the year", "15 Jumada al-Akhirah", times(afternoon, 250, 120).text(R.id.widget_headline))
        assertNull(small.findViewById<View>(R.id.widget_countdown))
        assertEquals("after Maghrib it is the next Hijri day", "16 Jumada al-Akhirah 1448", times(at(17, 0), 300, 120).text(R.id.widget_headline))
        assertEquals(
            "a day added where the month was announced a day later",
            "16 Jumada al-Akhirah 1448", times(afternoon, 300, 120, beirut.copy(hijriAdjustDays = 1)).text(R.id.widget_headline),
        )
        assertEquals(listOf("Asr"), rows(small).filter { it.gold() }.map { it.name() })
        assertEquals(
            "what came before Asr is faded",
            names.take(5), rows(small).filter { it.nameColor() == night.passed.light }.map { it.name() },
        )
        assertEquals(View.GONE, small.findViewById<View>(R.id.widget_next_line).visibility)
        assertEquals(View.GONE, small.findViewById<View>(R.id.widget_footer).visibility)
        assertTrue("no room for AM and PM", rows(small).none { it.text(R.id.row_time).endsWith("M") })
        // The gold row has the time left at its right, as big as the clock times; in a row this tight it takes the clock time's place.
        rows(small).single { it.gold() }.let {
            assertTrue(it.counts())
            assertTrue(it.findViewById<View>(R.id.row_countdown) is Chronometer)
            assertEquals("", it.text(R.id.row_time))
            assertEquals(
                rows(small).first().findViewById<TextView>(R.id.row_time).textSize,
                it.findViewById<TextView>(R.id.row_countdown).textSize,
            )
            assertTrue("on a filled pill", it.findViewById<ImageView>(R.id.row_left_frame).drawable != null)
            val row = it.findViewById<View>(R.id.row_left).parent as ViewGroup
            assertTrue("after the name, not beside it", row.indexOfChild(it.findViewById(R.id.row_left)) > row.indexOfChild(it.findViewById(R.id.row_name)))
        }
        assertEquals("only the gold row counts down", 1, rows(small).count { it.counts() })
        // Each prayer has its icon before its name; Imsak, Shuruq and midnight, which are not prayers, have none.
        assertEquals(
            mapOf(
                "Imsak" to null, "Fajr" to "ic_prayer_fajr", "Shuruq" to null, "Duha" to "ic_prayer_duha",
                "Dhuhr" to "ic_prayer_dhuhr", "Asr" to "ic_prayer_asr", "Maghrib" to "ic_prayer_maghrib",
                "Isha" to "ic_prayer_isha", "Midnight" to null, "Qiyam" to "ic_prayer_qiyam",
            ),
            rows(small).associate { it.name() to it.icon() },
        )

        // With room to spare the clock time stays beside it.
        val roomy = times(afternoon, 370, 150)
        rows(roomy).single { it.gold() }.let {
            assertEquals("Asr", it.name())
            assertTrue(it.counts())
            assertTrue(it.text(R.id.row_time).isNotEmpty())
        }
        // With no prayer current the gold row is the next one, counting down to its start.
        val morning = times(at(9, 0), 300, 150)
        rows(morning).single { it.gold() }.let {
            assertEquals("Dhuhr", it.name())
            assertTrue(it.counts())
        }
        // Every row is one line tall again, so the columns are level without any filling.
        assertEquals(names.take(5), rows(morning, R.id.table_start).map { it.name() })
        assertEquals(names.drop(5), rows(morning, R.id.table_end).map { it.name() })
        // Late at night the table is already the new day's: Fajr is gold and nothing has passed.
        val late = times(at(3, 30), 300, 150)
        assertEquals("Fajr", rows(late).single { it.gold() }.name())
        assertTrue(rows(late).none { it.nameColor() == night.passed.light })

        // A little taller: a second countdown, to the next prayer, where the current one ends before it.
        val dawn = times(at(5, 30), 300, 200)
        assertEquals("Fajr", rows(dawn).single { it.gold() }.name())
        assertEquals(View.VISIBLE, dawn.findViewById<View>(R.id.widget_next_line).visibility)
        assertEquals("Next: Dhuhr in", dawn.text(R.id.widget_next))
        fun nextLine(state: PrayerNow, settings: AlarmSettings = beirut) =
            times(state, 300, 200, settings).findViewById<View>(R.id.widget_next_line).visibility
        assertEquals("Asr ends when Maghrib starts: one countdown says both", View.GONE, nextLine(afternoon))
        assertEquals("the headline already counts down to Dhuhr", View.GONE, nextLine(at(9, 0)))
        val yellow = beirut.copy(asrEnd = AsrEnd.SUN_YELLOWS)
        assertEquals("Asr shown ending before Maghrib", View.VISIBLE, nextLine(at(15, 0, yellow), yellow))

        // Taller still: one column, each prayer with its start and end, and a bell beside the times that notify.
        val belled = beirut.copy(alerts = mapOf(PrayerTime.DHUHR to PrayerAlert(), PrayerTime.MIDNIGHT to PrayerAlert(fullScreen = true, sound = AlertSound.VIBRATE), PrayerTime.DUHA to PrayerAlert(sound = AlertSound.VIBRATE)))
        val tall = times(afternoon, 250, 270, belled)
        assertEquals(names, rows(tall, R.id.table_start).map { it.name() })
        assertEquals(View.GONE, tall.findViewById<View>(R.id.table_end).visibility)
        assertTrue("tight: the time left takes the range's place", rows(tall).single { it.gold() }.let { it.counts() && it.text(R.id.row_time) == "" })
        assertTrue(rows(times(afternoon, 280, 270, belled)).single { it.gold() }.let { it.counts() && " – " in it.text(R.id.row_time) })
        val ranges = rows(tall).associate { it.name() to it.text(R.id.row_time) }
        assertEquals(
            "every prayer has a start and an end; Imsak, Shuruq and midnight are one moment, and Asr shows its time left",
            setOf("Imsak", "Shuruq", "Midnight", "Asr"), ranges.filterValues { " – " !in it }.keys,
        )
        assertEquals("Fajr ends at sunrise", ranges.getValue("Shuruq"), ranges.getValue("Fajr").substringAfter(" – "))
        assertEquals("Maghrib starts when Asr... ends: Isha starts when Maghrib ends", ranges.getValue("Isha").substringBefore(" – "), ranges.getValue("Maghrib").substringAfter(" – "))
        assertEquals("Qiyam ends at Fajr, as Isha does", ranges.getValue("Isha").substringAfter(" – "), ranges.getValue("Qiyam").substringAfter(" – "))
        // Beside each time that does something, the icon of what: a bell, a shaking phone, or the four corners of the screen.
        assertEquals(
            mapOf("Duha" to "ic_alert_vibrate", "Dhuhr" to "ic_alert_sound", "Midnight" to "ic_alert_screen"),
            rows(tall).filter { it.findViewById<View>(R.id.row_bell).visibility == View.VISIBLE }.associate {
                it.name() to app.resources.getResourceEntryName(shadowOf(it.findViewById<ImageView>(R.id.row_bell).drawable).createdFromResId)
            },
        )
        assertEquals(View.GONE, tall.findViewById<View>(R.id.widget_footer).visibility)

        // Tall and wide: the range with AM and PM, with no heading over it, and the next alarm at the bottom.
        val full = times(afternoon, 350, 350, belled)
        val table = rows(full, R.id.table_start)
        assertEquals("no heading row", names, table.map { it.name() })
        fun ends(view: View) = rows(view, R.id.table_start).associate { it.name() to it.text(R.id.row_time).substringAfter(" – ", "") }
        val begins = table.associate { it.name() to it.text(R.id.row_time).substringBefore(" – ") }
        val ends = ends(full)
        assertTrue("with AM and PM", begins.getValue("Fajr").endsWith("AM") && ends.getValue("Maghrib").endsWith("PM"))
        assertEquals("Fajr ends at sunrise", begins["Shuruq"], ends["Fajr"])
        assertEquals("Dhuhr ends at Asr", begins["Asr"], ends["Dhuhr"])
        assertEquals("Asr ends at Maghrib", begins["Maghrib"], ends["Asr"])
        assertEquals("Maghrib ends at Isha", begins["Isha"], ends["Maghrib"])
        assertEquals("Imsak, Shuruq and midnight are single moments", listOf("", "", ""), listOf("Imsak", "Shuruq", "Midnight").map(ends::getValue))
        assertEquals("Qiyam ends at the next Fajr", ends.getValue("Isha"), ends.getValue("Qiyam"))
        assertTrue("room for the time left and the range", table.single { it.gold() }.let { it.counts() && " – " in it.text(R.id.row_time) })
        val footer = full.findViewById<TextView>(R.id.widget_footer)
        assertEquals(View.VISIBLE, footer.visibility)
        assertTrue("the alarm, and not the method again: ${footer.text}", footer.text.startsWith("Alarm: ") && " · " !in footer.text)

        // The end a school sets inside a prayer's time is the one the table shows.
        val school = beirut.copy(asrEnd = AsrEnd.SHADOW_TWICE, ishaEnd = IshaEnd.FIRST_THIRD)
        val chosen = ends(times(at(15, 0, school), 350, 350, school))
        assertTrue("Asr no longer ends at Maghrib: $chosen", chosen["Asr"] != ends["Asr"] && chosen["Asr"] != "")
        assertTrue("Isha no longer ends at Fajr: $chosen", chosen["Isha"] != ends["Isha"] && chosen["Isha"] != "")
        assertEquals("the others are as they were", ends - "Asr" - "Isha", chosen - "Asr" - "Isha")

        // Only the times chosen for the widgets are listed, in two columns as even as they come.
        val five = beirut.copy(widgetTimes = setOf(PrayerTime.FAJR, PrayerTime.DHUHR, PrayerTime.ASR, PrayerTime.MAGHRIB, PrayerTime.ISHA))
        val fewer = times(afternoon, 300, 150, five)
        assertEquals(listOf("Fajr", "Dhuhr", "Asr"), rows(fewer, R.id.table_start).map { it.name() })
        assertEquals(listOf("Maghrib", "Isha", ""), rows(fewer, R.id.table_end).map { it.name() })
        assertEquals("Asr", rows(fewer).single { it.gold() }.name())
        val noAsr = times(afternoon, 300, 150, five.copy(widgetTimes = five.widgetTimes - PrayerTime.ASR))
        assertEquals("the current prayer isn't listed: the next one is gold", "Maghrib", rows(noAsr).single { it.gold() }.name())
        // With Fajr current but not listed, the gold row is Dhuhr and counts down to Dhuhr, not to sunrise.
        val dawnNoFajr = times(at(5, 30), 300, 150, five.copy(widgetTimes = five.widgetTimes - PrayerTime.FAJR))
        assertEquals("Dhuhr", rows(dawnNoFajr).single { it.gold() }.name())
        assertTrue(rows(dawnNoFajr).single { it.gold() }.counts())
        val obligatoryOff = times(afternoon, 300, 150, beirut.copy(widgetTimes = setOf(PrayerTime.SUNRISE, PrayerTime.MIDNIGHT)))
        assertTrue("neither is listed: no gold row", rows(obligatoryOff).none { it.gold() })
        assertTrue(rows(times(afternoon, 300, 150, beirut.copy(widgetTimes = emptySet()))).isEmpty())
        store.save(five)
        assertEquals(five.widgetTimes, store.load().widgetTimes)

        // Every size the home screen is offered draws all the times.
        PrayerWidgets.TIMES_SIZES.forEach { size ->
            val view = PrayerWidgets.timesViews(app, afternoon, beirut, size).apply(app, FrameLayout(app))
            assertEquals("$size", names, rows(view).map { it.name() }.filter { it.isNotEmpty() })
        }

        // Pictures of this moment, so that the countdowns in them make sense.
        picture("widget-times", times(now(), 300, 150), 300, 150)
        picture("widget-times-wide", times(now(), 370, 150, belled), 370, 150)
        picture("widget-times-five", times(now(), 300, 150, five), 300, 150)
        picture("widget-times-medium", times(now(), 300, 200), 300, 200)
        picture("widget-times-tall", times(now(), 250, 270, belled), 250, 270)
        picture("widget-times-full", times(now(), 350, 350, belled), 350, 350)
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
            assertEquals("$theme times to come", palette.text.light, rows(view).single { it.name() == "Maghrib" }.color(R.id.row_time))
            assertEquals("$theme date", palette.muted.light, view.color(R.id.widget_date))
            assertEquals("$theme background", palette.backgroundAlpha, view.findViewById<ImageView>(R.id.widget_background).imageAlpha)
            val gold = rows(view).single { it.gold() }
            assertEquals("$theme gold row", palette.onGold.light, gold.nameColor())
            assertEquals("$theme time left, on its pill", palette.highlight.light, gold.color(R.id.row_countdown))
            assertTrue(gold.counts())
            assertEquals("$theme passed", palette.passed.light, rows(view).first().nameColor())
            assertEquals("$theme to come", palette.text.light, rows(view).single { it.name() == "Maghrib" }.nameColor())
            picture("widget-theme-${theme.name.lowercase()}", times(now(), 300, 200, beirut.copy(widgetTheme = theme)), 300, 200)
        }
        assertEquals("the default is white with blue", WidgetTheme.FROST, AlarmSettings().widgetTheme)
        assertTrue("see-through shows the wallpaper", palette(WidgetTheme.GLASS).backgroundAlpha < 200)
        // A passed time is quieter than one to come, but still far enough from the background to read.
        for (theme in WidgetTheme.values().filter { it != WidgetTheme.GLASS && it != WidgetTheme.CUSTOM }) {
            val p = palette(theme)
            fun contrast(a: Int, b: Int): Double {
                fun lum(c: Int): Double = listOf(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)
                    .map { it / 255.0 }.map { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
                    .let { (r, g, b) -> 0.2126 * r + 0.7152 * g + 0.0722 * b }
                val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
                return (hi + 0.05) / (lo + 0.05)
            }
            assertTrue("$theme passed", contrast(p.passed.light, p.background.light) >= 3.5)
            assertTrue("$theme text", contrast(p.text.light, p.background.light) >= 9.0)
            assertTrue("$theme highlight", contrast(p.onGold.light, p.highlight.light) >= 4.5)
        }

        // Your own colours: the three you pick, and how solid the background is.
        val mine = beirut.copy(
            widgetTheme = WidgetTheme.CUSTOM,
            widgetColors = WidgetColors(background = 0x1F3D2B, backgroundOpacity = 40, text = 0xFBF8F1, highlight = 0x8AB4F8),
        )
        val own = times(state, 300, 200, mine)
        assertEquals(0xFFFBF8F1.toInt(), rows(own).single { it.name() == "Maghrib" }.color(R.id.row_time))
        assertEquals(0xFF8AB4F8.toInt(), own.color(R.id.widget_headline))
        assertEquals(102, own.findViewById<ImageView>(R.id.widget_background).imageAlpha)
        assertEquals("dark text on a pale highlight", 0xFF1A2340.toInt(), rows(own).single { it.gold() }.nameColor())
        val clear = times(state, 300, 200, mine.copy(widgetColors = mine.widgetColors.copy(backgroundOpacity = 0)))
        assertEquals("no background at all", 0, clear.findViewById<ImageView>(R.id.widget_background).imageAlpha)
        picture("widget-theme-transparent", times(now(), 300, 200, beirut.copy(widgetTheme = WidgetTheme.CUSTOM, widgetColors = WidgetColors(backgroundOpacity = 0))), 300, 200)
        val dark = times(state, 300, 200, mine.copy(widgetColors = mine.widgetColors.copy(highlight = 0x4A1F2A)))
        assertEquals("white text on a dark one", 0xFFFFFFFF.toInt(), rows(dark).single { it.gold() }.nameColor())
        picture("widget-theme-custom-green", times(now(), 300, 200, mine), 300, 200)

        // "Follow the phone" is the light theme by day and the night theme once the phone goes dark.
        fun View.toCome() = rows(this).single { it.name() == "Maghrib" }.color(R.id.row_time)
        assertEquals(palette(WidgetTheme.LIGHT).text.light, drawn(WidgetTheme.SYSTEM).toCome())
        RuntimeEnvironment.setQualifiers("+night")
        assertEquals(palette(WidgetTheme.NIGHT).text.light, drawn(WidgetTheme.SYSTEM).toCome())
        assertEquals("the others stay as they are", palette(WidgetTheme.LIGHT).text.light, drawn(WidgetTheme.LIGHT).toCome())

        // The choice is kept.
        store.save(mine.copy(asrEnd = AsrEnd.SUN_YELLOWS, ishaEnd = IshaEnd.MIDNIGHT, imsakMinutes = 15))
        store.load().let {
            assertEquals(WidgetTheme.CUSTOM, it.widgetTheme)
            assertEquals(mine.widgetColors, it.widgetColors)
            assertEquals(15, it.imsakMinutes)
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
        assertTrue("the big countdown is enough", rows(wide).none { it.counts() })
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
        assertEquals(arabic.take(5), rows(view, R.id.table_start).map { it.name() })
        assertEquals(arabic.drop(5), rows(view, R.id.table_end).map { it.name() })
        assertTrue(rows(view).single { it.gold() }.counts())
        picture("ar-widget-times", times(now(), 300, 150), 300, 150)

        val full = times(at(15, 0), 330, 350)
        assertEquals(arabic, rows(full).map { it.name() })
        assertTrue(full.text(R.id.widget_headline), full.text(R.id.widget_headline).contains("جمادى الآخرة"))
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
        assertEquals("one column", 10, table.size)
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

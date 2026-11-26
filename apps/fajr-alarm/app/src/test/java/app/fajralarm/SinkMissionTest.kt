package app.fajralarm

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fajralarm.alarm.AlarmService
import app.fajralarm.core.AlarmPlanner
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.DismissMission
import app.fajralarm.data.SettingsStore
import app.fajralarm.data.StopReason
import app.fajralarm.ui.AlarmActivity
import app.fajralarm.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The sink mission on the JVM: only a sink turns the alarm off. It snoozes like any
 * alarm until sunrise is close, and an alarm nobody answers stops ringing at sunrise.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
class SinkMissionTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val store get() = SettingsStore(app)
    private var service: ServiceController<AlarmService>? = null

    @After
    fun tearDown() {
        service?.destroy()
    }

    /**
     * Sets the sink mission up somewhere on the equator where the sun rises [minutes]
     * from now (to within half a minute), since the clock here can't be moved.
     */
    private fun sunriseIn(minutes: Long, mission: DismissMission = DismissMission.SINK_PHOTO) {
        val now = ZonedDateTime.now()
        val base = AlarmSettings(latitude = 0.0, snoozeMinutes = 5, maxSnoozes = 3, dismissMission = mission)
        store.save(
            (-1800 until 1800).map { base.copy(longitude = it / 10.0) }.minBy { place ->
                val sunrise = AlarmPlanner.nextSunrise(place, now) ?: return@minBy Long.MAX_VALUE
                abs(Duration.between(now, sunrise).seconds - minutes * 60)
            },
        )
    }

    private fun ring() {
        service = Robolectric.buildService(AlarmService::class.java, AlarmService.startIntent(app)).create().startCommand(0, 1)
        assertTrue(AlarmService.ringing.value)
    }

    private fun send(action: String, reason: StopReason? = null) {
        service!!.withIntent(AlarmService.commandIntent(app, action, reason)).startCommand(0, 2)
    }

    private fun pass(minutes: Long) = shadowOf(Looper.getMainLooper()).idleFor(minutes, TimeUnit.MINUTES)

    @Test
    fun `it snoozes while sunrise is far off`() {
        sunriseIn(300)
        ring()
        assertTrue(AlarmService.canSnooze(app))
        send(AlarmService.ACTION_SNOOZE)
        assertFalse(AlarmService.ringing.value)
        assertEquals(StopReason.SNOOZED, store.lastAlarm()?.reason)
    }

    @Test
    fun `close to sunrise a snooze is not on offer and a snooze tap changes nothing`() {
        // A 5-minute snooze would leave 7 minutes to pray; it has to leave 15.
        sunriseIn(12)
        ring()
        assertFalse(AlarmService.canSnooze(app))
        assertTrue(AlarmService.sunriseStopsSnooze(app))
        send(AlarmService.ACTION_SNOOZE)
        assertTrue(AlarmService.ringing.value)
        assertEquals(0L, store.snoozeUntil)
    }

    @Test
    fun `the sum mission snoozes right up to sunrise`() {
        sunriseIn(12, DismissMission.MATH)
        ring()
        assertTrue(AlarmService.canSnooze(app))
        send(AlarmService.ACTION_SNOOZE)
        assertFalse(AlarmService.ringing.value)
    }

    @Test
    fun `only the sink turns it off`() {
        sunriseIn(300)
        ring()
        send(AlarmService.ACTION_DISMISS)
        send(AlarmService.ACTION_DISMISS, StopReason.BUTTON)
        assertTrue(AlarmService.ringing.value)

        send(AlarmService.ACTION_DISMISS, StopReason.SINK)
        assertFalse(AlarmService.ringing.value)
        assertEquals(StopReason.SINK, store.lastAlarm()?.reason)
    }

    @Test
    fun `an alarm nobody answers rings for an hour and gives up, snoozes left or not`() {
        sunriseIn(300)
        ring()
        pass(59)
        assertTrue(AlarmService.ringing.value)
        pass(2)
        assertFalse(AlarmService.ringing.value)
        assertEquals(StopReason.TIMED_OUT, store.lastAlarm()?.reason)
        assertEquals(0L, store.snoozeUntil)
    }

    @Test
    fun `with sunrise inside the hour it rings up to sunrise only`() {
        sunriseIn(30)
        ring()
        pass(28)
        assertTrue(AlarmService.ringing.value)
        pass(3)
        assertFalse(AlarmService.ringing.value)
        // The clock here stands still, so to the alarm sunrise is still half an hour off:
        // time for one more round, which is the rest-and-ring-again path.
        assertEquals(StopReason.TIMED_OUT_SNOOZED, store.lastAlarm()?.reason)
        assertTrue("it is due to ring again", store.snoozeUntil > System.currentTimeMillis())
    }

    @Test
    fun `the alarm screen drops the snooze button when sunrise is close`() {
        // Nothing is ringing here, and the alarm screen closes itself when it notices.
        compose.mainClock.autoAdvance = false
        sunriseIn(300)
        ActivityScenario.launch(AlarmActivity::class.java).use {
            compose.waitForIdle()
            // No camera on the JVM, so the screen falls back to the sum.
            compose.onNodeWithText("Solve to turn off the alarm").assertIsDisplayed()
            compose.onNodeWithText("Snooze 5 min").assertIsDisplayed()
            compose.onNodeWithText("No snooze now: sunrise is close.").assertDoesNotExist()
        }
        sunriseIn(12)
        ActivityScenario.launch(AlarmActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithText("Snooze 5 min").assertDoesNotExist()
            compose.onNodeWithText("No snooze now: sunrise is close.").assertIsDisplayed()
        }
    }

    @Test
    fun `the settings keep the snooze options and say when it turns off`() {
        sunriseIn(300)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithText("Fajr alarm").performClick()
            compose.onNodeWithText("SOUND & WAKE-UP").performScrollTo().performClick()
            compose.onNodeWithText("Max snoozes").assertExists()
            compose.onNodeWithText("TO TURN THE ALARM OFF").performScrollTo().performClick()
            compose.onNodeWithText("Snooze turns off once sunrise is close", substring = true).assertExists()
        }
    }
}

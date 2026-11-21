package app.fajralarm

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fajralarm.alarm.AlarmService
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * The sink mission on the JVM: nothing short of a sink turns the alarm off. Snooze is
 * neither offered nor obeyed, and an alarm nobody answers rests and rings again.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w411dp-h891dp-420dpi")
class SinkMissionTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val store get() = SettingsStore(app)
    private var service: ServiceController<AlarmService>? = null

    /** The sink mission, with a snooze left over from before it was picked. */
    @Before
    fun setUp() {
        store.save(AlarmSettings(snoozeMinutes = 5, maxSnoozes = 3, dismissMission = DismissMission.SINK_PHOTO))
    }

    @After
    fun tearDown() {
        service?.destroy()
    }

    private fun ring() {
        service?.destroy()
        service = Robolectric.buildService(AlarmService::class.java, AlarmService.startIntent(app)).create().startCommand(0, 1)
        assertTrue(AlarmService.ringing.value)
    }

    private fun send(action: String, reason: StopReason? = null) {
        service!!.withIntent(AlarmService.commandIntent(app, action, reason)).startCommand(0, 2)
    }

    private fun ringOut() = shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MINUTES)

    @Test
    fun `a snooze is not on offer and a snooze tap changes nothing`() {
        ring()
        assertFalse(AlarmService.canSnooze(app))
        send(AlarmService.ACTION_SNOOZE)
        assertTrue(AlarmService.ringing.value)
        assertEquals(0L, store.snoozeUntil)
    }

    @Test
    fun `only the sink turns it off`() {
        ring()
        send(AlarmService.ACTION_DISMISS)
        send(AlarmService.ACTION_DISMISS, StopReason.BUTTON)
        assertTrue(AlarmService.ringing.value)

        send(AlarmService.ACTION_DISMISS, StopReason.SINK)
        assertFalse(AlarmService.ringing.value)
        assertEquals(StopReason.SINK, store.lastAlarm()?.reason)
    }

    @Test
    fun `an alarm nobody answers rests and rings again, and gives up after four rounds`() {
        ring()
        ringOut()
        assertFalse(AlarmService.ringing.value)
        assertEquals(StopReason.TIMED_OUT_SNOOZED, store.lastAlarm()?.reason)
        assertTrue("it is due to ring again", store.snoozeUntil > System.currentTimeMillis())

        // The second and third rests are the same; the fourth round is the last.
        store.snoozeCount = 3
        ring()
        ringOut()
        assertFalse(AlarmService.ringing.value)
        assertEquals(StopReason.TIMED_OUT, store.lastAlarm()?.reason)
        assertEquals(0L, store.snoozeUntil)
    }

    @Test
    fun `the sum mission still snoozes`() {
        store.save(store.load().copy(dismissMission = DismissMission.MATH))
        ring()
        assertTrue(AlarmService.canSnooze(app))
        send(AlarmService.ACTION_SNOOZE)
        assertFalse(AlarmService.ringing.value)
        assertEquals(StopReason.SNOOZED, store.lastAlarm()?.reason)
    }

    @Test
    fun `the alarm screen has no snooze button`() {
        // Nothing is ringing here, and the alarm screen closes itself when it notices.
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(AlarmActivity::class.java).use {
            compose.waitForIdle()
            // No camera on the JVM, so the screen falls back to the sum; still no snooze.
            compose.onNodeWithText("Solve to turn off the alarm").assertIsDisplayed()
            compose.onNodeWithText("Snooze 5 min").assertDoesNotExist()
        }
        store.save(store.load().copy(dismissMission = DismissMission.MATH))
        ActivityScenario.launch(AlarmActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithText("Snooze 5 min").assertIsDisplayed()
        }
    }

    @Test
    fun `the settings say there is no snooze and hide its options`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithText("No snooze with this mission", substring = true).assertExists()
            compose.onNodeWithText("Max snoozes").assertDoesNotExist()
        }
        store.save(store.load().copy(dismissMission = DismissMission.MATH))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            compose.onNodeWithText("Max snoozes").assertExists()
            compose.onNodeWithText("No snooze with this mission", substring = true).assertDoesNotExist()
        }
    }
}

package app.fajralarm.core

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmSettingsTest {
    private val sink = AlarmSettings(snoozeMinutes = 5, dismissMission = DismissMission.SINK_PHOTO)

    private fun minutes(n: Long) = Duration.ofMinutes(n)

    @Test
    fun snoozeIsAllowedWhenItHasALength() {
        assertTrue(AlarmSettings(snoozeMinutes = 5).snoozeAllowed(null))
        assertFalse(AlarmSettings(snoozeMinutes = 0).snoozeAllowed(null))
        assertFalse(sink.copy(snoozeMinutes = 0).snoozeAllowed(minutes(90)))
    }

    @Test
    fun theSinkMissionSnoozesUntilSunriseIsClose() {
        assertTrue(sink.snoozeAllowed(minutes(90)))
        // A 5-minute snooze has to leave 15 minutes to pray.
        assertTrue(sink.snoozeAllowed(minutes(20)))
        assertFalse(sink.snoozeAllowed(minutes(19)))
        assertFalse(sink.snoozeAllowed(minutes(0)))
        // Without a known sunrise there is nothing to hold it back.
        assertTrue(sink.snoozeAllowed(null))
    }

    @Test
    fun otherMissionsSnoozeRightUpToSunrise() {
        assertTrue(sink.copy(dismissMission = DismissMission.MATH).snoozeAllowed(minutes(3)))
        assertTrue(sink.copy(dismissMission = DismissMission.NONE).snoozeAllowed(minutes(3)))
    }

    @Test
    fun anUnansweredSinkAlarmRingsForAnHourButNotPastSunrise() {
        assertEquals(minutes(60), SinkRinging.round(null))
        assertEquals(minutes(60), SinkRinging.round(minutes(95)))
        assertEquals(minutes(25), SinkRinging.round(minutes(25)))
        // Too short a ring would be easy to miss.
        assertEquals(minutes(10), SinkRinging.round(minutes(3)))
    }

    @Test
    fun itRingsAgainOnlyIfThatRoundEndsAtSunrise() {
        assertTrue(SinkRinging.ringsAgain(minutes(30)))
        assertTrue(SinkRinging.ringsAgain(minutes(65)))
        assertFalse(SinkRinging.ringsAgain(minutes(66)))
        assertFalse(SinkRinging.ringsAgain(minutes(14)))
        assertFalse(SinkRinging.ringsAgain(minutes(0)))
        assertFalse(SinkRinging.ringsAgain(null))
    }

    @Test
    fun findsTheNextSunrise() {
        val beirut = AlarmSettings(latitude = 33.8938, longitude = 35.5018)
        val zone = ZoneId.of("Asia/Beirut")
        val before = AlarmPlanner.nextSunrise(beirut, ZonedDateTime.of(2026, 11, 21, 4, 0, 0, 0, zone))!!
        assertEquals(21, before.dayOfMonth)
        assertEquals(6, before.hour)
        val after = AlarmPlanner.nextSunrise(beirut, ZonedDateTime.of(2026, 11, 21, 9, 0, 0, 0, zone))!!
        assertEquals(22, after.dayOfMonth)
        assertNull(AlarmPlanner.nextSunrise(AlarmSettings(), ZonedDateTime.now()))
    }
}

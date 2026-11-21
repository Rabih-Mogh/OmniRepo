package app.fajralarm.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlarmSettingsTest {

    @Test
    fun snoozeIsAllowedWhenItHasALength() {
        assertTrue(AlarmSettings(snoozeMinutes = 5).snoozeAllowed)
        assertTrue(AlarmSettings(snoozeMinutes = 5, dismissMission = DismissMission.MATH).snoozeAllowed)
        assertFalse(AlarmSettings(snoozeMinutes = 0).snoozeAllowed)
    }

    @Test
    fun theSinkMissionNeverSnoozes() {
        assertFalse(AlarmSettings(snoozeMinutes = 5, dismissMission = DismissMission.SINK_PHOTO).snoozeAllowed)
    }
}

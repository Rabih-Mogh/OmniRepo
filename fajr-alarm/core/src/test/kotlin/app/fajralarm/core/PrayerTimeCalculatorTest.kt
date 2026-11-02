package app.fajralarm.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrayerTimeCalculatorTest {

    private fun assertNear(expected: LocalTime, actual: ZonedDateTime?, toleranceMin: Long = 2) {
        assertNotNull(actual)
        val diff = Duration.between(expected, actual.toLocalTime()).abs().toMinutes()
        assertTrue(diff <= toleranceMin, "expected ~$expected but was ${actual.toLocalTime()}")
    }

    @Test
    fun julianDayOfJ2000() {
        assertEquals(2451544.5, PrayerTimeCalculator.julianDay(LocalDate.of(2000, 1, 1)))
    }

    @Test
    fun londonSummerSolsticeSunriseAndHighLatitudeFajr() {
        val zone = ZoneId.of("Europe/London")
        val t = PrayerTimeCalculator.compute(
            LocalDate.of(2024, 6, 21), zone,
            PrayerCalcParams(51.5074, -0.1278, 18.0, HighLatitudeRule.ANGLE_BASED),
        )
        assertNear(LocalTime.of(4, 43), t.sunrise)
        // The sun never reaches 18° below the horizon, so the high-latitude rule applies.
        val fajr = assertNotNull(t.fajr)
        assertTrue(fajr.isBefore(t.sunrise))
        assertTrue(fajr.toLocalTime().isAfter(LocalTime.of(1, 30)), "fajr=$fajr")
    }

    @Test
    fun londonWithoutHighLatitudeRuleHasNoFajrInMidsummer() {
        val t = PrayerTimeCalculator.compute(
            LocalDate.of(2024, 6, 21), ZoneId.of("Europe/London"),
            PrayerCalcParams(51.5074, -0.1278, 18.0, HighLatitudeRule.NONE),
        )
        assertNull(t.fajr)
        assertNotNull(t.sunrise)
    }

    @Test
    fun newYorkEquinox() {
        val t = PrayerTimeCalculator.compute(
            LocalDate.of(2024, 3, 20), ZoneId.of("America/New_York"),
            PrayerCalcParams(40.7128, -74.0060, 15.0),
        )
        // Reference values cross-checked against the independent `astral` library.
        assertNear(LocalTime.of(6, 59), t.sunrise)
        assertNear(LocalTime.of(5, 43), t.fajr)
    }

    @Test
    fun makkahWinter() {
        val t = PrayerTimeCalculator.compute(
            LocalDate.of(2024, 1, 15), ZoneId.of("Asia/Riyadh"),
            PrayerCalcParams(21.4225, 39.8262, 18.5),
        )
        assertNear(LocalTime.of(7, 1), t.sunrise)
        assertNear(LocalTime.of(5, 40), t.fajr)
    }

    @Test
    fun sydneyEasternLongitude() {
        val t = PrayerTimeCalculator.compute(
            LocalDate.of(2024, 12, 21), ZoneId.of("Australia/Sydney"),
            PrayerCalcParams(-33.8688, 151.2093, 18.0),
        )
        assertNear(LocalTime.of(5, 41), t.sunrise)
        assertNear(LocalTime.of(3, 56), t.fajr)
        assertEquals(LocalDate.of(2024, 12, 21), t.fajr?.toLocalDate())
    }

    @Test
    fun dateLineZoneStaysOnRequestedDay() {
        val date = LocalDate.of(2024, 5, 1)
        val t = PrayerTimeCalculator.compute(
            date, ZoneId.of("Pacific/Kiritimati"),
            PrayerCalcParams(1.87, -157.4, 18.0),
        )
        assertEquals(date, t.fajr?.toLocalDate())
        assertEquals(date, t.sunrise?.toLocalDate())
    }

    @Test
    fun polarDayHasNoTimes() {
        val t = PrayerTimeCalculator.compute(
            LocalDate.of(2024, 6, 21), ZoneId.of("Europe/Oslo"),
            PrayerCalcParams(69.6492, 18.9553, 18.0),
        )
        assertNull(t.sunrise)
        assertNull(t.fajr)
    }

    @Test
    fun fajrAdjustmentIsApplied() {
        val base = PrayerCalcParams(21.4225, 39.8262, 18.5)
        val date = LocalDate.of(2024, 1, 15)
        val zone = ZoneId.of("Asia/Riyadh")
        val a = PrayerTimeCalculator.compute(date, zone, base).fajr!!
        val b = PrayerTimeCalculator.compute(date, zone, base.copy(fajrAdjustMinutes = 3)).fajr!!
        assertEquals(3, Duration.between(a, b).toMinutes())
    }
}

class AlarmPlannerTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    private val base = AlarmSettings(enabled = true, latitude = 21.4225, longitude = 39.8262)

    @Test
    fun disabledOrMissingLocationGivesNothing() {
        val now = ZonedDateTime.of(2024, 1, 15, 1, 0, 0, 0, zone)
        assertNull(AlarmPlanner.nextAlarm(base.copy(enabled = false), now))
        assertNull(AlarmPlanner.nextAlarm(base.copy(latitude = null), now))
        assertNull(AlarmPlanner.nextAlarm(base.copy(days = emptySet()), now))
    }

    @Test
    fun picksTodayBeforeFajrAndTomorrowAfter() {
        val early = ZonedDateTime.of(2024, 1, 15, 1, 0, 0, 0, zone)
        val a = assertNotNull(AlarmPlanner.nextAlarm(base, early))
        assertEquals(LocalDate.of(2024, 1, 15), a.triggerAt.toLocalDate())

        val late = ZonedDateTime.of(2024, 1, 15, 9, 0, 0, 0, zone)
        val b = assertNotNull(AlarmPlanner.nextAlarm(base, late))
        assertEquals(LocalDate.of(2024, 1, 16), b.triggerAt.toLocalDate())
    }

    @Test
    fun offsetAndAnchorAreApplied() {
        val now = ZonedDateTime.of(2024, 1, 15, 1, 0, 0, 0, zone)
        val atFajr = AlarmPlanner.nextAlarm(base, now)!!
        val before = AlarmPlanner.nextAlarm(base.copy(offsetMinutes = -20), now)!!
        assertEquals(-20, Duration.between(atFajr.triggerAt, before.triggerAt).toMinutes())

        val sunrise = AlarmPlanner.nextAlarm(base.copy(anchor = AlarmAnchor.SUNRISE, offsetMinutes = -30), now)!!
        assertEquals(sunrise.times.sunrise!!.minusMinutes(30), sunrise.triggerAt)
    }

    @Test
    fun skipsDisabledDays() {
        // 2024-01-15 is a Monday.
        val now = ZonedDateTime.of(2024, 1, 15, 1, 0, 0, 0, zone)
        val a = AlarmPlanner.nextAlarm(base.copy(days = setOf(DayOfWeek.FRIDAY)), now)!!
        assertEquals(LocalDate.of(2024, 1, 19), a.triggerAt.toLocalDate())
    }

    @Test
    fun earlyOffsetCanFallOnPreviousEvening() {
        // An alarm 6h before Fajr on Tuesday rings on Monday evening.
        val now = ZonedDateTime.of(2024, 1, 15, 20, 0, 0, 0, zone)
        val a = AlarmPlanner.nextAlarm(base.copy(offsetMinutes = -360), now)!!
        assertEquals(LocalDate.of(2024, 1, 15), a.triggerAt.toLocalDate())
        assertEquals(LocalDate.of(2024, 1, 16), a.times.date)
    }

    @Test
    fun challengeAnswerIsConsistent() {
        repeat(50) {
            val c = MathChallenge.random()
            assertTrue(c.answer > 0)
        }
    }
}

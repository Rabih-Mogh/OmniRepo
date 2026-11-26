package app.fajralarm.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
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

    // The five prayers below are checked against NOAA's solar equations, worked separately.

    @Test
    fun makkahAllFivePrayers() {
        val t = PrayerTimeCalculator.compute(
            LocalDate.of(2024, 1, 15), ZoneId.of("Asia/Riyadh"),
            AlarmSettings(method = CalculationMethod.UMM_AL_QURA, latitude = 21.4225, longitude = 39.8262).calcParams()!!,
        )
        assertNear(LocalTime.of(12, 29), t.dhuhr)
        assertNear(LocalTime.of(15, 36), t.asr)
        assertNear(LocalTime.of(17, 58), t.maghrib)
        // Umm al-Qura puts Isha 90 minutes after Maghrib.
        assertEquals(90, Duration.between(t.maghrib, t.isha).toMinutes())
    }

    @Test
    fun darAlFatwaMatchesTheOfficialBeirutTimes() {
        // Dar al-Fatwa's published times for Beirut on 25 Nov 2026, to the minute.
        val t = PrayerTimeCalculator.compute(
            LocalDate.of(2026, 11, 25), ZoneId.of("Asia/Beirut"),
            AlarmSettings(method = CalculationMethod.DAR_AL_FATWA_LEBANON, latitude = 33.8938, longitude = 35.5018).calcParams()!!,
        )
        fun minute(at: ZonedDateTime?) = at!!.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        assertEquals(LocalTime.of(4, 43), minute(t.fajr))
        assertEquals(LocalTime.of(6, 19), minute(t.sunrise))
        assertEquals(LocalTime.of(11, 24), minute(t.dhuhr))
        assertEquals(LocalTime.of(14, 11), minute(t.asr))
        assertEquals(LocalTime.of(16, 35), minute(t.maghrib))
        assertEquals(LocalTime.of(17, 56), minute(t.isha))
    }

    @Test
    fun everyPrayerCanBeAdjustedByMinutes() {
        val zone = ZoneId.of("Asia/Beirut")
        val date = LocalDate.of(2026, 11, 25)
        val plain = AlarmSettings(latitude = 33.8938, longitude = 35.5018)
        val moved = plain.copy(
            fajrAdjustMinutes = 2,
            adjustMinutes = mapOf(PrayerTime.DHUHR to 3, PrayerTime.ASR to -1, PrayerTime.MAGHRIB to 4, PrayerTime.ISHA to -2),
        )
        val a = PrayerTimeCalculator.compute(date, zone, plain.calcParams()!!)
        val b = PrayerTimeCalculator.compute(date, zone, moved.calcParams()!!)
        assertEquals(listOf(2L, 0L, 0L, 3L, -1L, 4L, -2L), a.moments().zip(b.moments()).take(7).map { (x, y) -> Duration.between(x.at, y.at).toMinutes() })

        // Your minutes go on top of the method's own.
        val official = plain.copy(method = CalculationMethod.DAR_AL_FATWA_LEBANON)
        val nudged = official.copy(adjustMinutes = mapOf(PrayerTime.MAGHRIB to -2))
        assertEquals(
            -2,
            Duration.between(
                PrayerTimeCalculator.compute(date, zone, official.calcParams()!!).maghrib,
                PrayerTimeCalculator.compute(date, zone, nudged.calcParams()!!).maghrib,
            ).toMinutes(),
        )
    }

    @Test
    fun hanafiAsrIsLater() {
        val params = PrayerCalcParams(21.4225, 39.8262, 18.5, asrRule = AsrRule.HANAFI)
        val t = PrayerTimeCalculator.compute(LocalDate.of(2024, 1, 15), ZoneId.of("Asia/Riyadh"), params)
        assertNear(LocalTime.of(16, 22), t.asr)
    }

    @Test
    fun newYorkAndSydneyByAngle() {
        val ny = PrayerTimeCalculator.compute(
            LocalDate.of(2024, 3, 20), ZoneId.of("America/New_York"),
            PrayerCalcParams(40.7128, -74.0060, 15.0, ishaAngle = 15.0),
        )
        assertNear(LocalTime.of(13, 4), ny.dhuhr)
        assertNear(LocalTime.of(16, 29), ny.asr)
        assertNear(LocalTime.of(19, 9), ny.maghrib)
        assertNear(LocalTime.of(20, 24), ny.isha)

        val sydney = PrayerTimeCalculator.compute(
            LocalDate.of(2024, 12, 21), ZoneId.of("Australia/Sydney"),
            PrayerCalcParams(-33.8688, 151.2093, 18.0, ishaAngle = 17.0),
        )
        assertNear(LocalTime.of(12, 53), sydney.dhuhr)
        assertNear(LocalTime.of(16, 38), sydney.asr)
        assertNear(LocalTime.of(20, 6), sydney.maghrib)
        assertNear(LocalTime.of(21, 43), sydney.isha)
    }

    @Test
    fun tehranMaghribComesAfterSunset() {
        val date = LocalDate.of(2024, 3, 20)
        val zone = ZoneId.of("Asia/Tehran")
        val atSunset = PrayerTimeCalculator.compute(date, zone, PrayerCalcParams(35.6892, 51.3890, 17.7, ishaAngle = 14.0))
        val tehran = PrayerTimeCalculator.compute(
            date, zone, PrayerCalcParams(35.6892, 51.3890, 17.7, ishaAngle = 14.0, maghribAngle = 4.5),
        )
        val later = Duration.between(atSunset.maghrib, tehran.maghrib).toMinutes()
        assertTrue(later in 14..22, "maghrib is $later min after sunset")
    }

    @Test
    fun londonMidsummerIshaFollowsTheHighLatitudeRule() {
        val date = LocalDate.of(2024, 6, 21)
        val zone = ZoneId.of("Europe/London")
        val t = PrayerTimeCalculator.compute(date, zone, PrayerCalcParams(51.5074, -0.1278, 18.0, ishaAngle = 17.0))
        assertNear(LocalTime.of(13, 2), t.dhuhr)
        assertNear(LocalTime.of(17, 25), t.asr)
        assertNear(LocalTime.of(21, 21), t.maghrib)
        // The sun never gets 17° down: Isha is 17/60 of the night after sunset.
        val isha = assertNotNull(t.isha)
        assertTrue(isha.isAfter(t.maghrib))
        assertTrue(Duration.between(t.maghrib, isha).toMinutes() in 100..140, "isha=$isha")

        val none = PrayerTimeCalculator.compute(
            date, zone, PrayerCalcParams(51.5074, -0.1278, 18.0, HighLatitudeRule.NONE, ishaAngle = 17.0),
        )
        assertNull(none.isha)
    }

    @Test
    fun theOptionalTimesSitWhereTheyShould() {
        val zone = ZoneId.of("Asia/Beirut")
        val params = PrayerCalcParams(33.8938, 35.5018, 18.0)
        val date = LocalDate.of(2026, 11, 25)
        val t = PrayerTimeCalculator.compute(date, zone, params)
        val nextFajr = PrayerTimeCalculator.compute(date.plusDays(1), zone, params).fajr!!

        assertEquals(t.sunrise!!.plusMinutes(15), t.duha)
        // The night is sunset to the next Fajr: midnight is half of it, the last third two thirds in.
        val night = Duration.between(t.maghrib, nextFajr)
        assertEquals(t.maghrib!!.plus(night.dividedBy(2)), t.midnight)
        assertTrue(Duration.between(nextFajr.minus(night.dividedBy(3)), t.lastThird).abs().seconds <= 1)
        assertEquals(date.plusDays(1), t.lastThird!!.toLocalDate())

        // Every time exists, and they come in the order they are listed.
        val moments = t.moments()
        assertEquals(PrayerTime.values().toList(), moments.map { it.prayer })
        assertEquals(moments.sortedBy { it.at }, moments)
    }
}

class PrayerClockTest {
    private val zone = ZoneId.of("Asia/Beirut")
    private val params = PrayerCalcParams(33.8938, 35.5018, 18.0)
    private val date = LocalDate.of(2026, 11, 25)
    private val today = PrayerTimeCalculator.compute(date, zone, params)

    private fun at(hour: Int, minute: Int = 0, day: LocalDate = date) =
        assertNotNull(PrayerClock.at(ZonedDateTime.of(day, LocalTime.of(hour, minute), zone), params))

    @Test
    fun inTheAfternoonAsrRunsUntilMaghrib() {
        val now = at(15)
        assertEquals(PrayerMoment(PrayerTime.ASR, today.asr!!), now.current)
        assertEquals(today.maghrib, now.currentEnds)
        assertEquals(PrayerMoment(PrayerTime.MAGHRIB, today.maghrib!!), now.next)
        assertEquals(now.next, now.nextEvent)
        assertEquals(date, now.day.date)
    }

    @Test
    fun duhaAndTheLastThirdAreTheVoluntaryPrayers() {
        assertEquals(listOf(PrayerTime.DUHA, PrayerTime.LAST_THIRD), PrayerTime.values().filter { it.nafl })
        assertTrue(PrayerTime.values().none { it.nafl && it.obligatory })
    }

    @Test
    fun theDayListsWhenEachPrayerEnds() {
        val tomorrow = PrayerTimeCalculator.compute(date.plusDays(1), zone, params)
        assertEquals(
            mapOf(
                PrayerTime.FAJR to today.sunrise,
                PrayerTime.DUHA to today.dhuhr!!.minusMinutes(10),
                PrayerTime.DHUHR to today.asr,
                PrayerTime.ASR to today.maghrib,
                PrayerTime.MAGHRIB to today.isha,
                PrayerTime.ISHA to tomorrow.fajr,
                PrayerTime.LAST_THIRD to tomorrow.fajr,
            ),
            at(15).dayEnds,
        )
    }

    @Test
    fun fajrEndsAtSunriseAndThenNoPrayerIsCurrent() {
        val dawn = at(5, 30)
        assertEquals(PrayerTime.FAJR, dawn.current?.prayer)
        assertEquals(today.sunrise, dawn.currentEnds)
        assertEquals(PrayerTime.SUNRISE, dawn.nextEvent.prayer)
        assertEquals(PrayerTime.DHUHR, dawn.next.prayer)

        val morning = at(9)
        assertNull(morning.current)
        assertNull(morning.currentEnds)
        assertEquals(PrayerMoment(PrayerTime.DHUHR, today.dhuhr!!), morning.next)
    }

    @Test
    fun ishaRunsThroughTheNightUntilTheNextFajr() {
        val tomorrow = PrayerTimeCalculator.compute(date.plusDays(1), zone, params)
        val evening = at(22)
        assertEquals(PrayerMoment(PrayerTime.ISHA, today.isha!!), evening.current)
        assertEquals(tomorrow.fajr, evening.currentEnds)
        assertEquals(PrayerTime.MIDNIGHT, evening.nextEvent.prayer)

        // After midnight on the clock, the list still shows the day whose night it is.
        val small = at(0, 30, date.plusDays(1))
        assertEquals(PrayerMoment(PrayerTime.ISHA, today.isha!!), small.current)
        assertEquals(PrayerTime.LAST_THIRD, small.nextEvent.prayer)
        assertEquals(date, small.day.date)

        // Once the last third has begun, the next day's list takes over.
        val late = at(3, 0, date.plusDays(1))
        assertEquals(PrayerTime.FAJR, late.nextEvent.prayer)
        assertEquals(PrayerMoment(PrayerTime.ISHA, today.isha!!), late.current)
        assertEquals(date.plusDays(1), late.day.date)
    }

    @Test
    fun polarDayHasNoClock() {
        val oslo = ZoneId.of("Europe/Oslo")
        assertNull(PrayerClock.at(ZonedDateTime.of(2024, 6, 21, 12, 0, 0, 0, oslo), PrayerCalcParams(69.6492, 18.9553, 18.0)))
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

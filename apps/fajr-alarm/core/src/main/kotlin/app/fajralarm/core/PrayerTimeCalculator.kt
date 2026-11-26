package app.fajralarm.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Twilight angles used by the common calculation authorities: how far the sun is below
 * the horizon at Fajr and at Isha.
 */
enum class CalculationMethod(
    val fajrAngle: Double,
    val ishaAngle: Double,
    /** Isha this many minutes after Maghrib instead of by angle; 0 = by angle. */
    val ishaMinutes: Int = 0,
    /** Maghrib once the sun is this far below the horizon; 0 = at sunset. */
    val maghribAngle: Double = 0.0,
    /** Minutes the authority's timetable adds to what the sun gives, for the times it moves. */
    val offsets: Map<PrayerTime, Int> = emptyMap(),
) {
    /**
     * Lebanon's official Sunni timetable. Maghrib is 5 minutes after sunset; Fajr a minute
     * early and Isha a minute late reproduce the published Beirut times for 25 Nov 2026,
     * the one day they have been checked against.
     */
    DAR_AL_FATWA_LEBANON(
        19.5, 17.5,
        offsets = mapOf(PrayerTime.FAJR to -1, PrayerTime.MAGHRIB to 5, PrayerTime.ISHA to 1),
    ),
    MUSLIM_WORLD_LEAGUE(18.0, 17.0),
    ISNA(15.0, 15.0),
    EGYPTIAN(19.5, 17.5),
    UMM_AL_QURA(18.5, 0.0, ishaMinutes = 90),
    KARACHI(18.0, 18.0),
    TEHRAN(17.7, 14.0, maghribAngle = 4.5),
    DUBAI(18.2, 18.2),
    GULF(19.5, 0.0, ishaMinutes = 90),
    KUWAIT(18.0, 17.5),
    QATAR(18.0, 0.0, ishaMinutes = 90),
    SINGAPORE(20.0, 18.0),
    TURKEY(18.0, 17.0),
    FRANCE_UOIF(12.0, 12.0),
    CUSTOM(18.0, 17.0),
}

/** When Asr starts: once an object's shadow is this many times its height longer than at noon. */
enum class AsrRule(val shadow: Int) {
    STANDARD(1),
    HANAFI(2),
}

/**
 * When Asr's time is shown as ending. Its time runs to Maghrib in every school; the
 * others are earlier points that schools set within it.
 */
enum class AsrEnd {
    /** Maghrib: the end of its time. */
    MAGHRIB,

    /** When a shadow is twice its object's height, plus its length at noon: the end of the preferred time. */
    SHADOW_TWICE,

    /** [PrayerClock.ASR_YELLOW_BEFORE_SUNSET_MINUTES] before sunset, about when the sun turns yellow: delaying past it is disliked. */
    SUN_YELLOWS,
}

/** When Isha's time is shown as ending. Its time runs to Fajr; the others are earlier points within it. */
enum class IshaEnd {
    FAJR,

    /** Halfway from sunset to Fajr. */
    MIDNIGHT,

    /** A third of the way from sunset to Fajr. */
    FIRST_THIRD,
}

/** The times the app shows for a day, in the order they come. */
enum class PrayerTime(val obligatory: Boolean) {
    /** When someone fasting stops eating and drinking, a little before Fajr. */
    IMSAK(false),
    FAJR(true),
    SUNRISE(false),
    DUHA(false),
    DHUHR(true),
    ASR(true),
    MAGHRIB(true),
    ISHA(true),
    MIDNIGHT(false),
    LAST_THIRD(false),
    ;

    /**
     * The start of a voluntary prayer's time: Duha, and the night prayer in the last
     * third. Imsak, sunrise and midnight are not: they are moments, not prayers.
     */
    val nafl: Boolean get() = this == DUHA || this == LAST_THIRD
}

/**
 * How to place Fajr and Isha when the sun never gets deep enough below the horizon
 * (summer at high latitudes) or when the night is very short.
 */
enum class HighLatitudeRule {
    ANGLE_BASED,
    ONE_SEVENTH,
    MIDDLE_OF_NIGHT,
    NONE,
}

data class PrayerCalcParams(
    val latitude: Double,
    val longitude: Double,
    val fajrAngle: Double,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.ANGLE_BASED,
    /** Manual correction added to Fajr, in minutes. */
    val fajrAdjustMinutes: Int = 0,
    val ishaAngle: Double = 17.0,
    /** Isha this many minutes after Maghrib instead of by angle; 0 = by angle. */
    val ishaMinutes: Int = 0,
    /** Maghrib once the sun is this far below the horizon; 0 = at sunset. */
    val maghribAngle: Double = 0.0,
    val asrRule: AsrRule = AsrRule.STANDARD,
    /** Minutes added to Fajr, Sunrise, Dhuhr, Asr, Maghrib or Isha, on top of [fajrAdjustMinutes]. */
    val adjustMinutes: Map<PrayerTime, Int> = emptyMap(),
    val asrEnd: AsrEnd = AsrEnd.MAGHRIB,
    val ishaEnd: IshaEnd = IshaEnd.FAJR,
    /** Imsak this many minutes before Fajr; 0 leaves it out. */
    val imsakMinutes: Int = DEFAULT_IMSAK_MINUTES,
) {
    companion object {
        const val DEFAULT_IMSAK_MINUTES = 10
    }
}

/** One time on one day. */
data class PrayerMoment(val prayer: PrayerTime, val at: ZonedDateTime)

/**
 * Times for one local calendar day; null when they don't exist (polar day/night).
 * [midnight] and [lastThird] belong to the night that starts at this day's sunset, so
 * they usually fall on the next calendar date.
 */
data class DailyTimes(
    val date: LocalDate,
    val fajr: ZonedDateTime?,
    val sunrise: ZonedDateTime?,
    /** The start of the Duha prayer's time, once the sun has cleared the horizon. */
    val duha: ZonedDateTime? = null,
    val dhuhr: ZonedDateTime? = null,
    val asr: ZonedDateTime? = null,
    val maghrib: ZonedDateTime? = null,
    val isha: ZonedDateTime? = null,
    /** Halfway from sunset to the next Fajr. */
    val midnight: ZonedDateTime? = null,
    /** The start of the last third of the night, the time for the night prayer (Qiyam, Tahajjud). */
    val lastThird: ZonedDateTime? = null,
    /** The sun's upper edge on the horizon, whatever the method does with Maghrib. Not one of the listed times. */
    val sunset: ZonedDateTime? = null,
    /** When a shadow is twice its object's height longer than at noon. Not one of the listed times. */
    val asrShadowTwice: ZonedDateTime? = null,
    /** The end of the first third of the night. Not one of the listed times. */
    val firstThird: ZonedDateTime? = null,
    /** A set number of minutes before [fajr]; null where the settings leave it out. */
    val imsak: ZonedDateTime? = null,
) {
    fun at(prayer: PrayerTime): ZonedDateTime? = when (prayer) {
        PrayerTime.IMSAK -> imsak
        PrayerTime.FAJR -> fajr
        PrayerTime.SUNRISE -> sunrise
        PrayerTime.DUHA -> duha
        PrayerTime.DHUHR -> dhuhr
        PrayerTime.ASR -> asr
        PrayerTime.MAGHRIB -> maghrib
        PrayerTime.ISHA -> isha
        PrayerTime.MIDNIGHT -> midnight
        PrayerTime.LAST_THIRD -> lastThird
    }

    /** The day's times that exist. */
    fun moments(): List<PrayerMoment> =
        PrayerTime.values().mapNotNull { p -> at(p)?.let { PrayerMoment(p, it) } }
}

/**
 * Astronomical prayer-time calculation for the five prayers, sunrise and the optional
 * times around them.
 *
 * Uses the standard low-precision solar position formulas (accurate to well
 * under a minute for prayer purposes) and refines each time by re-evaluating
 * the sun's position at the estimated moment.
 */
object PrayerTimeCalculator {

    private const val SUNRISE_ANGLE = 0.833 // refraction + solar semi-diameter

    /** The Duha prayer's time starts this long after sunrise. */
    const val DUHA_AFTER_SUNRISE_MINUTES = 15L

    fun compute(date: LocalDate, zone: ZoneId, params: PrayerCalcParams): DailyTimes {
        val day = solarDay(date, zone, params) ?: return DailyTimes(date, null, null)
        fun adjusted(hours: Double, prayer: PrayerTime, from: SolarDay = day): ZonedDateTime? =
            from.at(hours)?.plusMinutes((params.adjustMinutes[prayer] ?: 0).toLong())

        val fajr = adjusted(day.fajr, PrayerTime.FAJR)?.plusMinutes(params.fajrAdjustMinutes.toLong())
        val sunset = day.at(day.sunset)
        // The night runs from sunset to the next day's Fajr.
        val nextFajr = solarDay(date.plusDays(1), zone, params)
            ?.let { adjusted(it.fajr, PrayerTime.FAJR, it) }?.plusMinutes(params.fajrAdjustMinutes.toLong())
        val night = if (sunset != null && nextFajr != null) Duration.between(sunset, nextFajr) else null
        val sunrise = adjusted(day.sunrise, PrayerTime.SUNRISE)
        return DailyTimes(
            date = date,
            fajr = fajr,
            sunrise = sunrise,
            duha = sunrise?.plusMinutes(DUHA_AFTER_SUNRISE_MINUTES),
            dhuhr = adjusted(day.noon, PrayerTime.DHUHR),
            asr = adjusted(day.asr, PrayerTime.ASR),
            maghrib = adjusted(day.maghrib, PrayerTime.MAGHRIB),
            isha = adjusted(day.isha, PrayerTime.ISHA),
            midnight = night?.let { sunset!!.plus(it.dividedBy(2)) },
            lastThird = night?.let { sunset!!.plus(it.multipliedBy(2).dividedBy(3)) },
            sunset = sunset,
            asrShadowTwice = adjusted(day.asrTwice, PrayerTime.ASR),
            firstThird = night?.let { sunset!!.plus(it.dividedBy(3)) },
            imsak = fajr?.takeIf { params.imsakMinutes > 0 }?.minusMinutes(params.imsakMinutes.toLong()),
        )
    }

    /** A day's times in local hours from [midnight]; NaN where one doesn't exist. */
    private class SolarDay(
        val midnight: Instant,
        val zone: ZoneId,
        val fajr: Double,
        val sunrise: Double,
        val noon: Double,
        val asr: Double,
        val asrTwice: Double,
        val sunset: Double,
        val maghrib: Double,
        val isha: Double,
    ) {
        fun at(hours: Double): ZonedDateTime? =
            if (hours.isNaN()) null
            else midnight.plusSeconds((hours * 3600.0).roundToLong()).atZone(zone)
    }

    /** Null in polar day or polar night, when there is no sunrise to hang the day on. */
    private fun solarDay(date: LocalDate, zone: ZoneId, params: PrayerCalcParams): SolarDay? {
        // Work in local hours from local midnight, using the offset in force at noon.
        val offset = zone.rules.getOffset(date.atTime(12, 0))
        val offsetHours = offset.totalSeconds / 3600.0
        val midnight: Instant = date.atStartOfDay().toInstant(offset)
        val jdBase = julianDay(date) - offsetHours / 24.0

        val lat = params.latitude
        val lng = params.longitude
        // Difference between clock time and local mean solar time, folded into
        // (-12, 12] so that zones far from their longitude (e.g. UTC+14 at 157°W)
        // still get the solar day that belongs to this calendar date.
        val rawLag = offsetHours - lng / 15.0
        val zoneLag = rawLag - 24.0 * round(rawLag / 24.0)

        fun below(angle: Double, morning: Boolean, estimate: Double) =
            timeForAngle(jdBase, zoneLag, lat, morning, estimate) { angle }

        val sunrise = below(SUNRISE_ANGLE, morning = true, estimate = 6.0)
        val sunset = below(SUNRISE_ANGLE, morning = false, estimate = 18.0)
        var fajr = below(params.fajrAngle, morning = true, estimate = 5.0)
        if (sunrise.isNaN()) return null

        var noon = 12.0 + zoneLag
        repeat(2) { noon = solarNoon(jdBase, zoneLag, noon) }
        // Asr: the sun is as high as makes a shadow `shadow` times an object's height
        // longer than its shadow at noon.
        fun shadow(times: Int) = timeForAngle(jdBase, zoneLag, lat, morning = false, estimate = 15.0) { decl ->
            -dAtan(1.0 / (times + dTan(abs(lat - decl))))
        }
        val asr = shadow(params.asrRule.shadow)
        val asrTwice = shadow(2)
        val maghrib = if (params.maghribAngle > 0.0) {
            below(params.maghribAngle, morning = false, estimate = 18.3).takeUnless { it.isNaN() } ?: sunset
        } else {
            sunset
        }
        var isha = if (params.ishaMinutes > 0) {
            maghrib + params.ishaMinutes / 60.0
        } else {
            below(params.ishaAngle, morning = false, estimate = 19.5)
        }

        if (params.highLatitudeRule != HighLatitudeRule.NONE && !sunset.isNaN()) {
            val night = 24.0 - (sunset - sunrise)
            fun portion(angle: Double) = when (params.highLatitudeRule) {
                HighLatitudeRule.ANGLE_BASED -> angle / 60.0
                HighLatitudeRule.ONE_SEVENTH -> 1.0 / 7.0
                HighLatitudeRule.MIDDLE_OF_NIGHT -> 0.5
                HighLatitudeRule.NONE -> 0.0
            } * night
            if (fajr.isNaN() || sunrise - fajr > portion(params.fajrAngle)) {
                fajr = sunrise - portion(params.fajrAngle)
            }
            if (params.ishaMinutes == 0 && (isha.isNaN() || isha - sunset > portion(params.ishaAngle))) {
                isha = sunset + portion(params.ishaAngle)
            }
        }
        return SolarDay(midnight, zone, fajr, sunrise, noon, asr, asrTwice, sunset, maghrib, isha)
    }

    /** Julian day at 0h UT of the given calendar date. */
    internal fun julianDay(date: LocalDate): Double {
        var y = date.year
        var m = date.monthValue
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + date.dayOfMonth + b - 1524.5
    }

    private class SunPosition(val declination: Double, val equationOfTime: Double)

    private fun sunPosition(jd: Double): SunPosition {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dSin(g) + 0.020 * dSin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(dAtan2(dCos(e) * dSin(l), dCos(l)) / 15.0)
        var eqt = q / 15.0 - ra
        // Normalise to (-12, 12] hours.
        while (eqt > 12) eqt -= 24
        while (eqt <= -12) eqt += 24
        return SunPosition(dAsin(dSin(e) * dSin(l)), eqt)
    }

    /** Local solar noon in local hours, using the sun's position at local time [t]. */
    private fun solarNoon(jdBase: Double, zoneLag: Double, t: Double): Double {
        val eqt = sunPosition(jdBase + t / 24.0).equationOfTime
        return 12.0 + zoneLag - eqt
    }

    /**
     * Local time (hours from local midnight) when the sun is [angle] degrees below
     * the horizon, before ([morning]) or after solar noon. NaN if it never happens.
     * [angle] is given the sun's declination, which the Asr angle depends on.
     */
    private fun timeForAngle(
        jdBase: Double,
        zoneLag: Double,
        lat: Double,
        morning: Boolean,
        estimate: Double,
        angle: (declination: Double) -> Double,
    ): Double {
        // Shift the generic estimate by the zone/longitude difference so the first
        // guess lands near the right part of the day.
        var t = estimate + zoneLag
        repeat(3) {
            val decl = sunPosition(jdBase + t / 24.0).declination
            val noon = solarNoon(jdBase, zoneLag, t)
            val cosH = (-dSin(angle(decl)) - dSin(lat) * dSin(decl)) / (dCos(lat) * dCos(decl))
            if (cosH < -1.0 || cosH > 1.0) return Double.NaN
            val h = dAcos(cosH) / 15.0
            t = if (morning) noon - h else noon + h
        }
        return t
    }

    private fun dSin(d: Double) = sin(Math.toRadians(d))
    private fun dCos(d: Double) = cos(Math.toRadians(d))
    private fun dTan(d: Double) = tan(Math.toRadians(d))
    private fun dAtan(x: Double) = Math.toDegrees(atan(x))
    private fun dAsin(x: Double) = Math.toDegrees(asin(x))
    private fun dAcos(x: Double) = Math.toDegrees(acos(x))
    private fun dAtan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun fixAngle(a: Double) = a - 360.0 * floor(a / 360.0)
    private fun fixHour(h: Double) = h - 24.0 * floor(h / 24.0)
}

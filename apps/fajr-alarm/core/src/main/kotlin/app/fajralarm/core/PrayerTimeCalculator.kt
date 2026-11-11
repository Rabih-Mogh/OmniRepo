package app.fajralarm.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToLong
import kotlin.math.sin

/** Fajr twilight angles used by the common calculation authorities. */
enum class CalculationMethod(val displayName: String, val fajrAngle: Double) {
    MUSLIM_WORLD_LEAGUE("Muslim World League", 18.0),
    ISNA("ISNA (North America)", 15.0),
    EGYPTIAN("Egyptian General Authority", 19.5),
    UMM_AL_QURA("Umm al-Qura, Makkah", 18.5),
    KARACHI("Univ. of Islamic Sciences, Karachi", 18.0),
    TEHRAN("Institute of Geophysics, Tehran", 17.7),
    DUBAI("Dubai", 18.2),
    GULF("Gulf Region", 19.5),
    KUWAIT("Kuwait", 18.0),
    QATAR("Qatar", 18.0),
    SINGAPORE("Singapore (MUIS)", 20.0),
    TURKEY("Diyanet, Turkey", 18.0),
    FRANCE_UOIF("UOIF, France", 12.0),
    CUSTOM("Custom angle", 18.0),
}

/**
 * How to place Fajr when the sun never gets deep enough below the horizon
 * (summer at high latitudes) or when the night is very short.
 */
enum class HighLatitudeRule(val displayName: String) {
    ANGLE_BASED("Angle-based (recommended)"),
    ONE_SEVENTH("One-seventh of the night"),
    MIDDLE_OF_NIGHT("Middle of the night"),
    NONE("None"),
}

data class PrayerCalcParams(
    val latitude: Double,
    val longitude: Double,
    val fajrAngle: Double,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.ANGLE_BASED,
    /** Manual correction added to Fajr, in minutes. */
    val fajrAdjustMinutes: Int = 0,
)

/** Times for one local calendar day; null when they don't exist (polar day/night). */
data class DailyTimes(
    val date: LocalDate,
    val fajr: ZonedDateTime?,
    val sunrise: ZonedDateTime?,
)

/**
 * Astronomical prayer-time calculation for Fajr and sunrise.
 *
 * Uses the standard low-precision solar position formulas (accurate to well
 * under a minute for prayer purposes) and refines each time by re-evaluating
 * the sun's position at the estimated moment.
 */
object PrayerTimeCalculator {

    private const val SUNRISE_ANGLE = 0.833 // refraction + solar semi-diameter

    fun compute(date: LocalDate, zone: ZoneId, params: PrayerCalcParams): DailyTimes {
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

        val sunrise = timeForAngle(jdBase, zoneLag, lat, SUNRISE_ANGLE, morning = true, estimate = 6.0)
        val sunset = timeForAngle(jdBase, zoneLag, lat, SUNRISE_ANGLE, morning = false, estimate = 18.0)
        var fajr = timeForAngle(jdBase, zoneLag, lat, params.fajrAngle, morning = true, estimate = 5.0)

        if (sunrise.isNaN()) {
            // Polar day or polar night: no meaningful Fajr/sunrise.
            return DailyTimes(date, null, null)
        }

        if (params.highLatitudeRule != HighLatitudeRule.NONE && !sunset.isNaN()) {
            val night = 24.0 - (sunset - sunrise)
            val portion = when (params.highLatitudeRule) {
                HighLatitudeRule.ANGLE_BASED -> params.fajrAngle / 60.0
                HighLatitudeRule.ONE_SEVENTH -> 1.0 / 7.0
                HighLatitudeRule.MIDDLE_OF_NIGHT -> 0.5
                HighLatitudeRule.NONE -> 0.0
            } * night
            if (fajr.isNaN() || sunrise - fajr > portion) {
                fajr = sunrise - portion
            }
        }

        fun toZoned(hours: Double): ZonedDateTime? =
            if (hours.isNaN()) null
            else midnight.plusSeconds((hours * 3600.0).roundToLong()).atZone(zone)

        return DailyTimes(
            date = date,
            fajr = toZoned(fajr)?.plusMinutes(params.fajrAdjustMinutes.toLong()),
            sunrise = toZoned(sunrise),
        )
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
     */
    private fun timeForAngle(
        jdBase: Double,
        zoneLag: Double,
        lat: Double,
        angle: Double,
        morning: Boolean,
        estimate: Double,
    ): Double {
        // Shift the generic estimate by the zone/longitude difference so the first
        // guess lands near the right part of the day.
        var t = estimate + zoneLag
        repeat(3) {
            val decl = sunPosition(jdBase + t / 24.0).declination
            val noon = solarNoon(jdBase, zoneLag, t)
            val cosH = (-dSin(angle) - dSin(lat) * dSin(decl)) / (dCos(lat) * dCos(decl))
            if (cosH < -1.0 || cosH > 1.0) return Double.NaN
            val h = dAcos(cosH) / 15.0
            t = if (morning) noon - h else noon + h
        }
        return t
    }

    private fun dSin(d: Double) = sin(Math.toRadians(d))
    private fun dCos(d: Double) = cos(Math.toRadians(d))
    private fun dAsin(x: Double) = Math.toDegrees(asin(x))
    private fun dAcos(x: Double) = Math.toDegrees(acos(x))
    private fun dAtan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun fixAngle(a: Double) = a - 360.0 * floor(a / 360.0)
    private fun fixHour(h: Double) = h - 24.0 * floor(h / 24.0)
}

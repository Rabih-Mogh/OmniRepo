package app.fajralarm.core

import java.time.DayOfWeek
import java.time.Duration

/** What you have to do before the alarm will turn off. The app holds the names it shows. */
enum class DismissMission {
    NONE,
    MATH,
    // Stored by name, so it keeps its old name even though no photo is taken any more.
    SINK_PHOTO,
}

/** What the alarm time is measured from. */
enum class AlarmAnchor {
    FAJR,
    SUNRISE,
}

data class AlarmSettings(
    /** On from the first launch; it starts ringing as soon as a location is set. */
    val enabled: Boolean = true,
    val anchor: AlarmAnchor = AlarmAnchor.FAJR,
    /** Minutes relative to the anchor; negative = before, positive = after. */
    val offsetMinutes: Int = 0,
    val days: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    val method: CalculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
    val customFajrAngle: Double = 18.0,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.ANGLE_BASED,
    val fajrAdjustMinutes: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String = "",
    /** Content URI of the alarm sound, or null for the system default alarm sound. */
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val gradualVolume: Boolean = true,
    /** 0 disables snooze. */
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 3,
    val dismissMission: DismissMission = DismissMission.NONE,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null

    /**
     * Whether a snooze may start with [untilSunrise] left (null = sunrise isn't known).
     * The sink mission stops snoozing once the alarm would ring again with less than
     * [PRAYER_MINUTES] before sunrise.
     */
    fun snoozeAllowed(untilSunrise: Duration?): Boolean = when {
        snoozeMinutes <= 0 -> false
        dismissMission != DismissMission.SINK_PHOTO || untilSunrise == null -> true
        else -> untilSunrise >= Duration.ofMinutes(snoozeMinutes + PRAYER_MINUTES)
    }

    val fajrAngle: Double
        get() = if (method == CalculationMethod.CUSTOM) customFajrAngle else method.fajrAngle

    fun calcParams(): PrayerCalcParams? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        return PrayerCalcParams(lat, lng, fajrAngle, highLatitudeRule, fajrAdjustMinutes)
    }

    companion object {
        /** Time kept free before sunrise to wash and pray. */
        const val PRAYER_MINUTES = 15L
    }
}

/**
 * How long a sink-mission alarm rings when nobody answers. A round lasts an hour and
 * never runs past sunrise. One more round follows only if it can end at sunrise, so the
 * alarm is quiet after about two hours at most.
 */
object SinkRinging {
    private val ROUND: Duration = Duration.ofMinutes(60)
    private val SHORTEST_ROUND: Duration = Duration.ofMinutes(10)
    const val REST_MINUTES = 5

    /** How long a round that starts with [untilSunrise] left rings for (null = sunrise isn't known). */
    fun round(untilSunrise: Duration?): Duration = when {
        untilSunrise == null || untilSunrise > ROUND -> ROUND
        untilSunrise < SHORTEST_ROUND -> SHORTEST_ROUND
        else -> untilSunrise
    }

    /** Whether a round that rang out with [untilSunrise] left is followed by another after a rest. */
    fun ringsAgain(untilSunrise: Duration?): Boolean {
        if (untilSunrise == null) return false
        val afterRest = untilSunrise.minusMinutes(REST_MINUTES.toLong())
        return afterRest >= SHORTEST_ROUND && afterRest <= ROUND
    }
}

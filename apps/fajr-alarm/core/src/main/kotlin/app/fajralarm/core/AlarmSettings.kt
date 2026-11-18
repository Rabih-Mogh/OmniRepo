package app.fajralarm.core

import java.time.DayOfWeek

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

    val fajrAngle: Double
        get() = if (method == CalculationMethod.CUSTOM) customFajrAngle else method.fajrAngle

    fun calcParams(): PrayerCalcParams? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        return PrayerCalcParams(lat, lng, fajrAngle, highLatitudeRule, fajrAdjustMinutes)
    }
}

package app.fajralarm.core

import java.time.DayOfWeek

/** What you have to do before the alarm will turn off. */
enum class DismissMission(val displayName: String, val description: String) {
    NONE("None", "Tap to dismiss"),
    MATH("Solve a sum", "Solve a quick sum to turn it off"),
    SINK_PHOTO("Photo of your sink", "Get up and photograph your sink to turn it off"),
}

/** What the alarm time is measured from. */
enum class AlarmAnchor(val displayName: String) {
    FAJR("Fajr"),
    SUNRISE("Sunrise"),
}

data class AlarmSettings(
    val enabled: Boolean = false,
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
    /** How closely the wake-up photo must match the reference sink photos. */
    val photoStrictness: MatchStrictness = MatchStrictness.NORMAL,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null

    val fajrAngle: Double
        get() = if (method == CalculationMethod.CUSTOM) customFajrAngle else method.fajrAngle

    fun calcParams(): PrayerCalcParams? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        return PrayerCalcParams(lat, lng, fajrAngle, highLatitudeRule, fajrAdjustMinutes)
    }

    /** e.g. "15 min before Fajr", "At sunrise". */
    fun describeOffset(): String {
        val name = anchor.displayName
        return when {
            offsetMinutes == 0 -> "At $name"
            offsetMinutes < 0 -> "${-offsetMinutes} min before $name"
            else -> "$offsetMinutes min after $name"
        }
    }
}

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

/** When the silent notification with the current prayer's countdown is up. */
enum class LockScreenCountdown {
    OFF,
    ALWAYS,

    /** Only for the last [ENDING_MINUTES] before the prayer's time runs out, or before the next prayer starts. */
    ENDING,
    ;

    companion object {
        const val ENDING_MINUTES = 15L
    }
}

/** What the phone does when a time arrives. */
data class PrayerAlert(
    /** Shown over the whole screen, lock screen included, rather than as a plain notification. */
    val fullScreen: Boolean = false,
    val sound: AlertSound = AlertSound.SOUND,
)

/** How a prayer time's notification makes itself heard. */
enum class AlertSound {
    /** The phone's notification sound. */
    SOUND,
    VIBRATE,

    /** Neither: it only shows. */
    SILENT,
}

/** How the home screen widgets are coloured. The app holds the colours. */
enum class WidgetTheme {
    NIGHT,
    BLACK,
    LIGHT,

    /** See-through, for a wallpaper to show behind it. */
    GLASS,

    /** [LIGHT] while the phone is in light mode, [NIGHT] in dark mode. */
    SYSTEM,

    /** The colours picked in [AlarmSettings.widgetColors]. */
    CUSTOM,
}

/** Your own widget colours, each as 0xRRGGBB, and how solid the background is. */
data class WidgetColors(
    val background: Int = 0x17223F,
    /** 0 = no background at all, 100 = solid. */
    val backgroundOpacity: Int = 60,
    val text: Int = 0xFFFFFF,
    /** The current prayer's row and the headline. */
    val highlight: Int = 0xFFD27A,
)

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
    val customIshaAngle: Double = 17.0,
    val asrRule: AsrRule = AsrRule.STANDARD,
    /** When Asr and Isha are shown as ending, for the schools that set a point inside their time. */
    val asrEnd: AsrEnd = AsrEnd.MAGHRIB,
    val ishaEnd: IshaEnd = IshaEnd.FAJR,
    /** Imsak this many minutes before Fajr; 0 leaves it out of the list. */
    val imsakMinutes: Int = PrayerCalcParams.DEFAULT_IMSAK_MINUTES,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.ANGLE_BASED,
    val fajrAdjustMinutes: Int = 0,
    /** The same manual correction for Dhuhr, Asr, Maghrib and Isha, in minutes. */
    val adjustMinutes: Map<PrayerTime, Int> = emptyMap(),
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
    /** What the phone does when each time arrives; a time that isn't here does nothing. Fajr has the alarm instead. */
    val alerts: Map<PrayerTime, PrayerAlert> = emptyMap(),
    /** Keep the current prayer and the time it has left in a silent notification, for the lock screen. */
    val lockScreenCountdown: LockScreenCountdown = LockScreenCountdown.OFF,
    val widgetTheme: WidgetTheme = WidgetTheme.GLASS,
    /** The colours of [WidgetTheme.CUSTOM]. */
    val widgetColors: WidgetColors = WidgetColors(),
    /** The times the widgets' table lists. */
    val widgetTimes: Set<PrayerTime> = PrayerTime.values().toSet(),
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

    val ishaAngle: Double
        get() = if (method == CalculationMethod.CUSTOM) customIshaAngle else method.ishaAngle

    fun calcParams(): PrayerCalcParams? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        return PrayerCalcParams(
            lat, lng, fajrAngle, highLatitudeRule, fajrAdjustMinutes,
            ishaAngle = ishaAngle,
            ishaMinutes = method.ishaMinutes,
            maghribAngle = method.maghribAngle,
            asrRule = asrRule,
            asrEnd = asrEnd,
            ishaEnd = ishaEnd,
            imsakMinutes = imsakMinutes,
            // The method's own minutes, then yours on top.
            adjustMinutes = (method.offsets.keys + adjustMinutes.keys)
                .associateWith { (method.offsets[it] ?: 0) + (adjustMinutes[it] ?: 0) },
        )
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

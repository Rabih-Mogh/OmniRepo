package app.fajralarm.ui

import android.content.Context
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.fajralarm.R
import app.fajralarm.core.AlarmAnchor
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.AlertSound
import app.fajralarm.core.AsrEnd
import app.fajralarm.core.AsrRule
import app.fajralarm.core.CalculationMethod
import app.fajralarm.core.DismissMission
import app.fajralarm.core.HighLatitudeRule
import app.fajralarm.core.IshaEnd
import app.fajralarm.core.LockScreenCountdown
import app.fajralarm.core.MoonPhaseName
import app.fajralarm.core.PrayerAlert
import app.fajralarm.core.PrayerTime
import app.fajralarm.core.WidgetTheme
import app.fajralarm.data.StopReason
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Date

// The text for every settings value, in the phone's language. The values themselves
// (in the core module and in storage) carry no text.

@get:StringRes
val AlarmAnchor.label: Int
    get() = when (this) {
        AlarmAnchor.FAJR -> R.string.anchor_fajr
        AlarmAnchor.SUNRISE -> R.string.anchor_sunrise
    }

@get:StringRes
val DismissMission.label: Int
    get() = when (this) {
        DismissMission.NONE -> R.string.mission_none
        DismissMission.MATH -> R.string.mission_sum
        DismissMission.SINK_PHOTO -> R.string.mission_sink
    }

@get:StringRes
val DismissMission.summary: Int
    get() = when (this) {
        DismissMission.NONE -> R.string.mission_none_summary
        DismissMission.MATH -> R.string.mission_sum_summary
        DismissMission.SINK_PHOTO -> R.string.mission_sink_summary
    }

@get:StringRes
val CalculationMethod.label: Int
    get() = when (this) {
        CalculationMethod.DAR_AL_FATWA_LEBANON -> R.string.method_dar_al_fatwa
        CalculationMethod.MUSLIM_WORLD_LEAGUE -> R.string.method_mwl
        CalculationMethod.ISNA -> R.string.method_isna
        CalculationMethod.EGYPTIAN -> R.string.method_egyptian
        CalculationMethod.UMM_AL_QURA -> R.string.method_umm_al_qura
        CalculationMethod.KARACHI -> R.string.method_karachi
        CalculationMethod.TEHRAN -> R.string.method_tehran
        CalculationMethod.DUBAI -> R.string.method_dubai
        CalculationMethod.GULF -> R.string.method_gulf
        CalculationMethod.KUWAIT -> R.string.method_kuwait
        CalculationMethod.QATAR -> R.string.method_qatar
        CalculationMethod.SINGAPORE -> R.string.method_singapore
        CalculationMethod.TURKEY -> R.string.method_turkey
        CalculationMethod.FRANCE_UOIF -> R.string.method_france
        CalculationMethod.CUSTOM -> R.string.method_custom
    }

@get:StringRes
val HighLatitudeRule.label: Int
    get() = when (this) {
        HighLatitudeRule.ANGLE_BASED -> R.string.high_latitude_angle
        HighLatitudeRule.ONE_SEVENTH -> R.string.high_latitude_seventh
        HighLatitudeRule.MIDDLE_OF_NIGHT -> R.string.high_latitude_middle
        HighLatitudeRule.NONE -> R.string.high_latitude_none
    }

@get:StringRes
val AsrRule.label: Int
    get() = when (this) {
        AsrRule.STANDARD -> R.string.asr_standard
        AsrRule.HANAFI -> R.string.asr_hanafi
    }

@get:StringRes
val PrayerTime.label: Int
    get() = when (this) {
        PrayerTime.FAJR -> R.string.anchor_fajr
        PrayerTime.IMSAK -> R.string.prayer_imsak
        PrayerTime.SUNRISE -> R.string.anchor_sunrise
        PrayerTime.DUHA -> R.string.prayer_duha
        PrayerTime.DHUHR -> R.string.prayer_dhuhr
        PrayerTime.ASR -> R.string.prayer_asr
        PrayerTime.MAGHRIB -> R.string.prayer_maghrib
        PrayerTime.ISHA -> R.string.prayer_isha
        PrayerTime.MIDNIGHT -> R.string.prayer_midnight
        PrayerTime.LAST_THIRD -> R.string.prayer_last_third
    }

/** The picture that goes with a prayer; Imsak, sunrise and midnight are not prayers and have none. */
@get:DrawableRes
val PrayerTime.icon: Int?
    get() = when (this) {
        PrayerTime.FAJR -> R.drawable.ic_prayer_fajr
        PrayerTime.DUHA -> R.drawable.ic_prayer_duha
        PrayerTime.DHUHR -> R.drawable.ic_prayer_dhuhr
        PrayerTime.ASR -> R.drawable.ic_prayer_asr
        PrayerTime.MAGHRIB -> R.drawable.ic_prayer_maghrib
        PrayerTime.ISHA -> R.drawable.ic_prayer_isha
        PrayerTime.LAST_THIRD -> R.drawable.ic_prayer_qiyam
        PrayerTime.IMSAK, PrayerTime.SUNRISE, PrayerTime.MIDNIGHT -> null
    }

@get:StringRes
val MoonPhaseName.label: Int
    get() = when (this) {
        MoonPhaseName.NEW -> R.string.moon_new
        MoonPhaseName.WAXING_CRESCENT -> R.string.moon_waxing_crescent
        MoonPhaseName.FIRST_QUARTER -> R.string.moon_first_quarter
        MoonPhaseName.WAXING_GIBBOUS -> R.string.moon_waxing_gibbous
        MoonPhaseName.FULL -> R.string.moon_full
        MoonPhaseName.WANING_GIBBOUS -> R.string.moon_waning_gibbous
        MoonPhaseName.LAST_QUARTER -> R.string.moon_last_quarter
        MoonPhaseName.WANING_CRESCENT -> R.string.moon_waning_crescent
    }

@get:StringRes
val AsrEnd.label: Int
    get() = when (this) {
        AsrEnd.MAGHRIB -> R.string.asr_end_maghrib
        AsrEnd.SHADOW_TWICE -> R.string.asr_end_shadow_twice
        AsrEnd.SUN_YELLOWS -> R.string.asr_end_sun_yellows
    }

@get:StringRes
val IshaEnd.label: Int
    get() = when (this) {
        IshaEnd.FAJR -> R.string.isha_end_fajr
        IshaEnd.MIDNIGHT -> R.string.isha_end_midnight
        IshaEnd.FIRST_THIRD -> R.string.isha_end_first_third
    }

@get:StringRes
val LockScreenCountdown.label: Int
    get() = when (this) {
        LockScreenCountdown.OFF -> R.string.lock_notice_off
        LockScreenCountdown.ALWAYS -> R.string.lock_notice_always
        LockScreenCountdown.ENDING -> R.string.lock_notice_ending
    }

/** The picture for what the phone does at a time: nothing when [this] is null. */
@get:DrawableRes
val PrayerAlert?.soundIcon: Int
    get() = when (this?.sound) {
        null -> R.drawable.ic_alert_off
        AlertSound.SOUND -> R.drawable.ic_alert_sound
        AlertSound.VIBRATE -> R.drawable.ic_alert_vibrate
        AlertSound.SILENT -> R.drawable.ic_alert_silent
    }

@get:StringRes
val AlertSound.label: Int
    get() = when (this) {
        AlertSound.SOUND -> R.string.alert_sound_sound
        AlertSound.VIBRATE -> R.string.alert_sound_vibrate
        AlertSound.SILENT -> R.string.alert_sound_silent
    }

@get:StringRes
val WidgetTheme.label: Int
    get() = when (this) {
        WidgetTheme.NIGHT -> R.string.widget_theme_night
        WidgetTheme.BLACK -> R.string.widget_theme_black
        WidgetTheme.LIGHT -> R.string.widget_theme_light
        WidgetTheme.GLASS -> R.string.widget_theme_glass
        WidgetTheme.FROST -> R.string.widget_theme_frost
        WidgetTheme.COBALT -> R.string.widget_theme_cobalt
        WidgetTheme.SYSTEM -> R.string.widget_theme_system
        WidgetTheme.CUSTOM -> R.string.widget_theme_custom
    }

/** The name where there is little room, as in a widget. */
@get:StringRes
val PrayerTime.shortLabel: Int
    get() = if (this == PrayerTime.LAST_THIRD) R.string.prayer_last_third_short else label

/** What the notification says when this time arrives. */
@get:StringRes
val PrayerTime.alertText: Int
    get() = when (this) {
        PrayerTime.IMSAK -> R.string.alert_imsak
        PrayerTime.SUNRISE -> R.string.alert_sunrise
        PrayerTime.DUHA -> R.string.alert_duha
        PrayerTime.MIDNIGHT -> R.string.alert_midnight
        PrayerTime.LAST_THIRD -> R.string.alert_last_third
        else -> R.string.alert_pray
    }

/** A clock time the way the phone shows times, e.g. "4:31 PM" or "16:31". */
fun ZonedDateTime.clockTime(context: Context): String =
    android.text.format.DateFormat.getTimeFormat(context).format(Date.from(toInstant()))

/** e.g. "1 h 12 min", "12 min". Rounded up, so it never says 0 min before the time has come. */
fun Duration.describe(context: Context): String {
    val minutes = (seconds + 59) / 60
    return if (minutes >= 60) {
        context.getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    } else {
        context.getString(R.string.duration_minutes, minutes)
    }
}

/** The whole "Last alarm" sentence for an alarm that stopped this way. */
@get:StringRes
val StopReason.sentence: Int
    get() = when (this) {
        StopReason.SINK -> R.string.last_sink
        StopReason.SUM -> R.string.last_sum
        StopReason.BUTTON -> R.string.last_button
        StopReason.NOTIFICATION -> R.string.last_notification
        StopReason.SNOOZED -> R.string.last_snoozed
        StopReason.TIMED_OUT_SNOOZED -> R.string.last_timed_out_snoozed
        StopReason.TIMED_OUT -> R.string.last_timed_out
        StopReason.STOPPED_BY_ANDROID -> R.string.last_stopped_by_android
    }

/** e.g. "15 min before Fajr", "At Sunrise". */
fun AlarmSettings.describeOffset(context: Context): String {
    val name = context.getString(anchor.label)
    return when {
        offsetMinutes == 0 -> context.getString(R.string.offset_at, name)
        offsetMinutes < 0 ->
            context.resources.getQuantityString(R.plurals.offset_before, -offsetMinutes, -offsetMinutes, name)
        else -> context.resources.getQuantityString(R.plurals.offset_after, offsetMinutes, offsetMinutes, name)
    }
}

/**
 * Where the alarm is set for, e.g. "Current location". The label is stored as the
 * English text older versions saved, so it is matched here rather than shown as it is.
 */
fun AlarmSettings.locationName(context: Context): String = context.getString(
    when (locationLabel) {
        LOCATION_CURRENT -> R.string.location_current
        LOCATION_MANUAL -> R.string.location_manual
        else -> R.string.location_saved
    },
)

const val LOCATION_CURRENT = "Current location"
const val LOCATION_MANUAL = "Manual"

/**
 * Keeps left-to-right text, such as coordinates or a signed number, in one piece and
 * in its own order when it sits inside right-to-left text. Does nothing in English.
 */
fun String.leftToRight(): String = BidiFormatter.getInstance().unicodeWrap(this, TextDirectionHeuristics.LTR)

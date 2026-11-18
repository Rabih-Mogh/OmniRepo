package app.fajralarm.ui

import android.content.Context
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import androidx.annotation.StringRes
import app.fajralarm.R
import app.fajralarm.core.AlarmAnchor
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.CalculationMethod
import app.fajralarm.core.DismissMission
import app.fajralarm.core.HighLatitudeRule
import app.fajralarm.data.StopReason

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

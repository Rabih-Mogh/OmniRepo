package app.fajralarm.data

import android.content.Context
import android.content.SharedPreferences
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.DismissMission
import java.time.DayOfWeek

/** Persists [AlarmSettings] and a little runtime state in SharedPreferences. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("fajr_alarm", Context.MODE_PRIVATE)

    fun load(): AlarmSettings {
        val d = AlarmSettings()
        return AlarmSettings(
            enabled = prefs.getBoolean(K_ENABLED, d.enabled),
            anchor = enumOr(prefs.getString(K_ANCHOR, null), d.anchor),
            offsetMinutes = prefs.getInt(K_OFFSET, d.offsetMinutes),
            days = daysFromMask(prefs.getInt(K_DAYS, maskFromDays(d.days))),
            method = enumOr(prefs.getString(K_METHOD, null), d.method),
            customFajrAngle = prefs.getFloat(K_CUSTOM_ANGLE, d.customFajrAngle.toFloat()).toDouble(),
            highLatitudeRule = enumOr(prefs.getString(K_HIGH_LAT, null), d.highLatitudeRule),
            fajrAdjustMinutes = prefs.getInt(K_ADJUST, d.fajrAdjustMinutes),
            latitude = if (prefs.contains(K_LAT)) prefs.getString(K_LAT, null)?.toDoubleOrNull() else null,
            longitude = if (prefs.contains(K_LNG)) prefs.getString(K_LNG, null)?.toDoubleOrNull() else null,
            locationLabel = prefs.getString(K_LOCATION_LABEL, d.locationLabel) ?: "",
            soundUri = prefs.getString(K_SOUND, null),
            vibrate = prefs.getBoolean(K_VIBRATE, d.vibrate),
            gradualVolume = prefs.getBoolean(K_GRADUAL, d.gradualVolume),
            snoozeMinutes = prefs.getInt(K_SNOOZE, d.snoozeMinutes),
            maxSnoozes = prefs.getInt(K_MAX_SNOOZES, d.maxSnoozes),
            dismissMission = enumOr(
                prefs.getString(K_MISSION, null),
                // Earlier versions stored a simple "math challenge" on/off switch.
                if (prefs.getBoolean(K_CHALLENGE, false)) DismissMission.MATH else d.dismissMission,
            ),
            photoStrictness = enumOr(prefs.getString(K_PHOTO_STRICTNESS, null), d.photoStrictness),
        )
    }

    fun save(s: AlarmSettings) {
        prefs.edit().apply {
            putBoolean(K_ENABLED, s.enabled)
            putString(K_ANCHOR, s.anchor.name)
            putInt(K_OFFSET, s.offsetMinutes)
            putInt(K_DAYS, maskFromDays(s.days))
            putString(K_METHOD, s.method.name)
            putFloat(K_CUSTOM_ANGLE, s.customFajrAngle.toFloat())
            putString(K_HIGH_LAT, s.highLatitudeRule.name)
            putInt(K_ADJUST, s.fajrAdjustMinutes)
            if (s.latitude != null) putString(K_LAT, s.latitude.toString()) else remove(K_LAT)
            if (s.longitude != null) putString(K_LNG, s.longitude.toString()) else remove(K_LNG)
            putString(K_LOCATION_LABEL, s.locationLabel)
            if (s.soundUri != null) putString(K_SOUND, s.soundUri) else remove(K_SOUND)
            putBoolean(K_VIBRATE, s.vibrate)
            putBoolean(K_GRADUAL, s.gradualVolume)
            putInt(K_SNOOZE, s.snoozeMinutes)
            putInt(K_MAX_SNOOZES, s.maxSnoozes)
            putString(K_MISSION, s.dismissMission.name)
            putString(K_PHOTO_STRICTNESS, s.photoStrictness.name)
            remove(K_CHALLENGE)
        }.apply()
    }

    /** Epoch millis of the next scheduled alarm (0 = none). */
    var nextAlarmAt: Long
        get() = prefs.getLong(K_NEXT_AT, 0L)
        set(value) = prefs.edit().putLong(K_NEXT_AT, value).apply()

    /** Epoch millis of a pending snooze (0 = none). */
    var snoozeUntil: Long
        get() = prefs.getLong(K_SNOOZE_UNTIL, 0L)
        set(value) = prefs.edit().putLong(K_SNOOZE_UNTIL, value).apply()

    /** How many times the current alarm has been snoozed. */
    var snoozeCount: Int
        get() = prefs.getInt(K_SNOOZE_COUNT, 0)
        set(value) = prefs.edit().putInt(K_SNOOZE_COUNT, value).apply()

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback

    private fun maskFromDays(days: Set<DayOfWeek>): Int =
        days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

    private fun daysFromMask(mask: Int): Set<DayOfWeek> =
        DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

    private companion object {
        const val K_ENABLED = "enabled"
        const val K_ANCHOR = "anchor"
        const val K_OFFSET = "offset_minutes"
        const val K_DAYS = "days_mask"
        const val K_METHOD = "method"
        const val K_CUSTOM_ANGLE = "custom_angle"
        const val K_HIGH_LAT = "high_lat_rule"
        const val K_ADJUST = "fajr_adjust"
        const val K_LAT = "lat"
        const val K_LNG = "lng"
        const val K_LOCATION_LABEL = "location_label"
        const val K_SOUND = "sound_uri"
        const val K_VIBRATE = "vibrate"
        const val K_GRADUAL = "gradual_volume"
        const val K_SNOOZE = "snooze_minutes"
        const val K_MAX_SNOOZES = "max_snoozes"
        const val K_CHALLENGE = "challenge" // legacy
        const val K_MISSION = "dismiss_mission"
        const val K_PHOTO_STRICTNESS = "photo_strictness"
        const val K_NEXT_AT = "next_alarm_at"
        const val K_SNOOZE_UNTIL = "snooze_until"
        const val K_SNOOZE_COUNT = "snooze_count"
    }
}


package app.fajralarm.data

import android.content.Context
import android.content.SharedPreferences
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.AlertSound
import app.fajralarm.core.DismissMission
import app.fajralarm.core.LockScreenCountdown
import app.fajralarm.core.PrayerAlert
import app.fajralarm.core.PrayerTime
import app.fajralarm.core.WidgetColors
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
            customIshaAngle = prefs.getFloat(K_CUSTOM_ISHA, d.customIshaAngle.toFloat()).toDouble(),
            asrRule = enumOr(prefs.getString(K_ASR, null), d.asrRule),
            asrEnd = enumOr(prefs.getString(K_ASR_END, null), d.asrEnd),
            ishaEnd = enumOr(prefs.getString(K_ISHA_END, null), d.ishaEnd),
            imsakMinutes = prefs.getInt(K_IMSAK, d.imsakMinutes),
            hijriAdjustDays = prefs.getInt(K_HIJRI_ADJUST, d.hijriAdjustDays),
            highLatitudeRule = enumOr(prefs.getString(K_HIGH_LAT, null), d.highLatitudeRule),
            fajrAdjustMinutes = prefs.getInt(K_ADJUST, d.fajrAdjustMinutes),
            adjustMinutes = ADJUSTABLE.associateWith { prefs.getInt(adjustKey(it), 0) }.filterValues { it != 0 },
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
            alerts = prefs.getStringSet(K_ALERTS, null).orEmpty()
                .mapNotNull { n -> PrayerTime.values().firstOrNull { it.name == n } }
                .associateWith { prayer ->
                    // Version 1.75 had one switch and one sound for all of them; they stand in where a time has none of its own.
                    PrayerAlert(
                        fullScreen = prefs.getBoolean(K_ALERT_FULL + prayer.name, prefs.getBoolean(K_FULL_SCREEN_ALERTS, false)),
                        sound = enumOr(
                            prefs.getString(K_ALERT_SOUND_OF + prayer.name, null),
                            enumOr(prefs.getString(K_ALERT_SOUND, null), AlertSound.SOUND),
                        ),
                    )
                },
            lockScreenCountdown = enumOr(
                prefs.getString(K_LOCK_NOTICE, null),
                // Earlier versions stored it as an on/off switch.
                if (prefs.getBoolean(K_LOCK_SCREEN, false)) LockScreenCountdown.ALWAYS else d.lockScreenCountdown,
            ),
            widgetTheme = enumOr(prefs.getString(K_WIDGET_THEME, null), d.widgetTheme),
            widgetTimes = prefs.getStringSet(K_WIDGET_TIMES, null)
                ?.mapNotNull { n -> PrayerTime.values().firstOrNull { it.name == n } }?.toSet() ?: d.widgetTimes,
            widgetColors = WidgetColors(
                background = prefs.getInt(K_WIDGET_BACKGROUND, d.widgetColors.background),
                backgroundOpacity = prefs.getInt(K_WIDGET_OPACITY, d.widgetColors.backgroundOpacity),
                text = prefs.getInt(K_WIDGET_TEXT, d.widgetColors.text),
                highlight = prefs.getInt(K_WIDGET_HIGHLIGHT, d.widgetColors.highlight),
            ),
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
            putFloat(K_CUSTOM_ISHA, s.customIshaAngle.toFloat())
            putString(K_ASR, s.asrRule.name)
            putString(K_ASR_END, s.asrEnd.name)
            putString(K_ISHA_END, s.ishaEnd.name)
            putInt(K_IMSAK, s.imsakMinutes)
            putInt(K_HIJRI_ADJUST, s.hijriAdjustDays)
            putString(K_HIGH_LAT, s.highLatitudeRule.name)
            putInt(K_ADJUST, s.fajrAdjustMinutes)
            ADJUSTABLE.forEach { putInt(adjustKey(it), s.adjustMinutes[it] ?: 0) }
            if (s.latitude != null) putString(K_LAT, s.latitude.toString()) else remove(K_LAT)
            if (s.longitude != null) putString(K_LNG, s.longitude.toString()) else remove(K_LNG)
            putString(K_LOCATION_LABEL, s.locationLabel)
            if (s.soundUri != null) putString(K_SOUND, s.soundUri) else remove(K_SOUND)
            putBoolean(K_VIBRATE, s.vibrate)
            putBoolean(K_GRADUAL, s.gradualVolume)
            putInt(K_SNOOZE, s.snoozeMinutes)
            putInt(K_MAX_SNOOZES, s.maxSnoozes)
            putString(K_MISSION, s.dismissMission.name)
            remove(K_CHALLENGE)
            putStringSet(K_ALERTS, s.alerts.keys.map { it.name }.toSet())
            PrayerTime.values().forEach { prayer ->
                val alert = s.alerts[prayer]
                if (alert != null) {
                    putBoolean(K_ALERT_FULL + prayer.name, alert.fullScreen)
                    putString(K_ALERT_SOUND_OF + prayer.name, alert.sound.name)
                } else {
                    remove(K_ALERT_FULL + prayer.name)
                    remove(K_ALERT_SOUND_OF + prayer.name)
                }
            }
            remove(K_FULL_SCREEN_ALERTS)
            remove(K_ALERT_SOUND)
            putStringSet(K_WIDGET_TIMES, s.widgetTimes.map { it.name }.toSet())
            putString(K_LOCK_NOTICE, s.lockScreenCountdown.name)
            remove(K_LOCK_SCREEN)
            putString(K_WIDGET_THEME, s.widgetTheme.name)
            putInt(K_WIDGET_BACKGROUND, s.widgetColors.background)
            putInt(K_WIDGET_OPACITY, s.widgetColors.backgroundOpacity)
            putInt(K_WIDGET_TEXT, s.widgetColors.text)
            putInt(K_WIDGET_HIGHLIGHT, s.widgetColors.highlight)
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

    /**
     * The time of the day the app is waiting for, and when it comes (epoch millis), so a
     * notification can be posted for it when it does. Null with nothing queued.
     */
    var awaitedPrayer: Pair<PrayerTime, Long>?
        get() {
            val prayer = prefs.getString(K_AWAITED, null)?.let { n -> PrayerTime.values().firstOrNull { it.name == n } }
            return prayer?.let { it to prefs.getLong(K_AWAITED_AT, 0L) }
        }
        set(value) = prefs.edit().apply {
            if (value == null) remove(K_AWAITED).remove(K_AWAITED_AT)
            else putString(K_AWAITED, value.first.name).putLong(K_AWAITED_AT, value.second)
        }.apply()

    /** The latest ringing, or null if the alarm has never rung. */
    fun lastAlarm(): LastAlarm? {
        val rang = prefs.getLong(K_LAST_RANG, 0L)
        if (rang == 0L) return null
        return LastAlarm(
            rangAt = rang,
            stoppedAt = prefs.getLong(K_LAST_STOPPED, 0L),
            reason = prefs.getString(K_LAST_REASON, null)?.let { n -> StopReason.values().firstOrNull { it.name == n } },
        )
    }

    fun recordRinging(at: Long) {
        // commit(), not apply(): if Android kills the app mid-ring, this must already be on disk.
        prefs.edit().putLong(K_LAST_RANG, at).remove(K_LAST_STOPPED).remove(K_LAST_REASON).commit()
    }

    fun recordStop(reason: StopReason, at: Long) {
        prefs.edit().putLong(K_LAST_STOPPED, at).putString(K_LAST_REASON, reason.name).commit()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback

    private fun maskFromDays(days: Set<DayOfWeek>): Int =
        days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

    private fun daysFromMask(mask: Int): Set<DayOfWeek> =
        DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

    private fun adjustKey(prayer: PrayerTime) = "adjust_" + prayer.name.lowercase()

    private companion object {
        /** The prayers with a stored minute correction besides Fajr, which has [K_ADJUST]. */
        val ADJUSTABLE = listOf(PrayerTime.DHUHR, PrayerTime.ASR, PrayerTime.MAGHRIB, PrayerTime.ISHA)
        const val K_ENABLED = "enabled"
        const val K_ANCHOR = "anchor"
        const val K_OFFSET = "offset_minutes"
        const val K_DAYS = "days_mask"
        const val K_METHOD = "method"
        const val K_CUSTOM_ANGLE = "custom_angle"
        const val K_CUSTOM_ISHA = "custom_isha_angle"
        const val K_ASR = "asr_rule"
        const val K_ASR_END = "asr_end"
        const val K_ISHA_END = "isha_end"
        const val K_WIDGET_THEME = "widget_theme"
        const val K_WIDGET_BACKGROUND = "widget_background"
        const val K_WIDGET_OPACITY = "widget_background_opacity"
        const val K_WIDGET_TEXT = "widget_text"
        const val K_WIDGET_HIGHLIGHT = "widget_highlight"
        const val K_IMSAK = "imsak_minutes"
        const val K_HIJRI_ADJUST = "hijri_adjust_days"
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
        const val K_ALERTS = "prayer_alerts"
        const val K_LOCK_SCREEN = "lock_screen_countdown" // legacy
        const val K_LOCK_NOTICE = "lock_screen_notice"
        const val K_FULL_SCREEN_ALERTS = "full_screen_alerts" // legacy: one switch for every time
        const val K_ALERT_SOUND = "alert_sound" // legacy: one sound for every time
        const val K_ALERT_FULL = "alert_full_" // + the time's name
        const val K_ALERT_SOUND_OF = "alert_sound_" // + the time's name
        const val K_WIDGET_TIMES = "widget_times"
        const val K_AWAITED = "awaited_prayer"
        const val K_AWAITED_AT = "awaited_prayer_at"
        const val K_NEXT_AT = "next_alarm_at"
        const val K_SNOOZE_UNTIL = "snooze_until"
        const val K_SNOOZE_COUNT = "snooze_count"
        const val K_LAST_RANG = "last_rang_at"
        const val K_LAST_STOPPED = "last_stopped_at"
        const val K_LAST_REASON = "last_stop_reason"
    }
}


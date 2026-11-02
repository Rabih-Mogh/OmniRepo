# Fajr Alarm (Android)

A focused Android alarm that wakes you for **Fajr**. Instead of a fixed clock time, it
calculates Fajr (or sunrise) for your location every day and rings relative to it, so the
alarm follows the prayer time as it moves through the year.

## Features

- **Rings relative to Fajr or sunrise**: at the time, or anywhere from 2 h before to 1 h after (5-min steps).
- **Recalculated daily**: after each alarm the next one is computed fresh, and again after a reboot, app update, clock change or timezone change.
- **Pick which days it rings.**
- **Location** from the device (coarse, no Google Play services needed) or typed in manually.
- **Calculation methods**: MWL, ISNA, Egypt, Umm al-Qura, Karachi, Tehran, Dubai, Gulf, Kuwait, Qatar, Singapore, Diyanet, UOIF, or a custom angle. A ± minute Fajr adjustment lets you match your mosque's timetable.
- **High-latitude rules** (angle-based, one-seventh, middle of the night) for places where twilight never ends in summer.
- **Ringing**:
  - full-screen alarm over the lock screen
  - plays on the alarm stream with any alarm sound or ringtone on the phone (pick your own adhan file if you have one)
  - optional fade-in and vibration
- **Snooze** with a configurable length and a cap on how many times you can snooze.
- **Wake-up challenge**: solve a quick sum before it turns off. There's no notification shortcut to dismiss it, so you can't switch it off half-asleep.
- **Auto-stop** after 10 minutes. It snoozes if snoozes are left, otherwise it stops.
- **"Test the alarm now"** button.

## Project layout

```
fajr-alarm/
├── core/   Pure Kotlin/JVM: prayer-time astronomy, alarm planning (unit-tested)
└── app/    Android app (Kotlin, Jetpack Compose, Material 3)
    ├── alarm/  AlarmScheduler (AlarmManager.setAlarmClock), receivers, ringing service
    ├── data/   SharedPreferences settings, one-shot location
    └── ui/     Settings screen, lock-screen alarm screen, theme
```

## Build & install

Requirements: Android Studio (Ladybug or newer) or the Android SDK with platform 35, and JDK 17+.

```bash
cd fajr-alarm
./gradlew :core:test          # prayer-time unit tests (no Android SDK needed)
./gradlew :app:assembleDebug  # → app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open `fajr-alarm/` in Android Studio and press Run. Min Android version: 8.0 (API 26).

## Permissions and why

| Permission | Why |
|---|---|
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | Ring at the exact minute, even in Doze |
| `POST_NOTIFICATIONS` | The ringing notification (Android 13+) |
| `USE_FULL_SCREEN_INTENT` | Show the alarm over the lock screen |
| `FOREGROUND_SERVICE(_MEDIA_PLAYBACK)` | Keep playing the alarm sound |
| `RECEIVE_BOOT_COMPLETED` | Re-queue the alarm after a restart |
| `ACCESS_COARSE_LOCATION` | Optional; you can type coordinates instead |
| `WAKE_LOCK`, `VIBRATE` | Ringing |

The app shows a banner if notifications, exact alarms or full-screen alarms are blocked,
with a button that opens the right settings page. On some phones (Xiaomi, Huawei,
Samsung "deep sleep") you may also need to exempt the app from battery optimisation.

## Accuracy

Times come from the standard solar-position formulas. The tests check them against
independently computed values (the `astral` library) for London, New York, Makkah and Sydney,
within 2 minutes. They also cover a zone far from its longitude (Kiritimati, UTC+14) and
polar day. Local mosque timetables often add a few minutes of caution. Use "Fajr adjustment"
to match yours.

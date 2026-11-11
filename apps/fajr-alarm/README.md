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
- **Dismiss missions**: choose what it takes to turn the alarm off.
  - **Find a sink**: the alarm only stops once the live camera recognises a real sink. See below.
  - **Solve a sum**.
  - **None**: a normal dismiss button.

  With a mission on, the notification has no dismiss button, so the alarm screen is the only way to turn it off.
- **Auto-stop**: after 10 minutes normally, or 60 minutes with the sink mission, so a phone left at home doesn't ring all day. When it auto-stops it snoozes if snoozes are left, otherwise it stops.
- **Last alarm**: the main screen shows when the alarm last rang and how it stopped (sink, sum, snooze, ringing out, or Android closing the app).
- **"Test the alarm now"** button.

## Sink mission

1. In **To turn the alarm off → Mission**, pick **Find a sink**. No photos are needed. **Test it** opens a dry run where you can check that your sink is recognised.
2. When the alarm rings, it keeps ringing while you walk to a sink: any bathroom or kitchen sink works. Point the in-app camera at it, with the basin in view, and keep it there for about 2 seconds. The alarm stops on its own.

How it works and its limits:
- **On-phone recognition**: a bundled object-detection model (EfficientDet-Lite0, trained on the 80 everyday COCO objects, "sink" among them) runs on the phone through LiteRT, 3 times a second. Nothing leaves the phone. The one frame that turned the alarm off is kept on the phone and shown under **Last alarm**, so a wrong call can be seen.
- **Why no photos**: there's nothing to set up, so there's no way to register a bedside object as "the sink".
- **Cheating checks**: nothing counts until the phone has been picked up (tilted at least 25° from where it lay, measured with the accelerometer; the alarm's vibration doesn't count). A sink that sits inside a detected phone, TV or laptop screen doesn't count. The sink must stay in view for 2 seconds while the camera moves by at least 5% of the frame, as it does in a hand. Dark frames reset the check.
- **Accuracy**: works best with the light on and most of the basin in view. A close-up of just the tap, or a very dark room, may not be recognised. The thresholds haven't been tuned on many real sinks yet.
- **Fallback**: if the camera permission is off or the detector can't start, the alarm falls back to the sum, so you're never stuck with an alarm you can't stop.
- **Not tamper-proof**: a determined person can still snooze (set Snooze to Off), show the camera a printed photo of a sink, or force-stop the app from system settings.

## Project layout

```
fajr-alarm/
├── core/   Pure Kotlin/JVM: prayer-time astronomy, alarm planning, sink check logic (unit-tested)
└── app/    Android app (Kotlin, Jetpack Compose, Material 3)
    ├── alarm/  AlarmScheduler (AlarmManager.setAlarmClock), receivers, ringing service
    ├── data/   SharedPreferences settings, one-shot location
    ├── detect/ On-phone sink detector (LiteRT + bundled model in assets/)
    └── ui/     Settings screen, lock-screen alarm screen, theme
```

## Build & install

Requirements: Android Studio (Ladybug or newer) or the Android SDK with platform 35, and JDK 17+.

```bash
cd apps/fajr-alarm
./gradlew :core:test          # prayer-time unit tests (no Android SDK needed)
./gradlew :app:assembleDebug  # → app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

The APKs are built for 64-bit ARM phones only (`arm64-v8a`), which keeps the detection runtime
to one copy. Remove the `abiFilters` line in `app/build.gradle.kts` to build for every phone.

Or open `apps/fajr-alarm/` in Android Studio and press Run. Min Android version: 8.0 (API 26).

## Permissions and why

| Permission | Why |
|---|---|
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | Ring at the exact minute, even in Doze |
| `POST_NOTIFICATIONS` | The ringing notification (Android 13+) |
| `USE_FULL_SCREEN_INTENT` | Show the alarm over the lock screen |
| `FOREGROUND_SERVICE(_MEDIA_PLAYBACK)` | Keep playing the alarm sound |
| `RECEIVE_BOOT_COMPLETED` | Re-queue the alarm after a restart |
| `ACCESS_COARSE_LOCATION` | Optional; you can type coordinates instead |
| `CAMERA` | Sink mission (optional) |
| `WAKE_LOCK`, `VIBRATE` | Ringing |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | One-tap fix when battery optimisation could put the alarm to sleep |

The app shows a banner, on the main screen and on the sink test screen, if notifications, exact alarms
or full-screen alarms are blocked or battery optimisation is on, with a button that opens the
right settings page. On Samsung phones it also reminds you once to add the app to
**Never sleeping apps**, since apps can't check that list. Other brands (Xiaomi, Huawei, Oppo)
have similar autostart and background switches in the app's system settings.

## Accuracy

Times come from the standard solar-position formulas. The tests check them against
independently computed values (the `astral` library) for London, New York, Makkah and Sydney,
within 2 minutes. They also cover a zone far from its longitude (Kiritimati, UTC+14) and
polar day. Local mosque timetables often add a few minutes of caution. Use "Fajr adjustment"
to match yours.

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
  - **Photo of your sink**: the alarm only stops once the live camera sees your own sink matching your reference photos. See below.
  - **Solve a sum**.
  - **None**: a normal dismiss button.

  With a mission on, the notification has no dismiss button, so the alarm screen is the only way to turn it off.
- **Auto-stop**: after 10 minutes normally, or 60 minutes with the sink-photo mission, so a phone left at home doesn't ring all day. When it auto-stops it snoozes if snoozes are left, otherwise it stops.
- **"Test the alarm now"** button.

## Sink-photo mission

1. In **To turn the alarm off → Mission**, pick **Photo of your sink**. The setup screen opens.
2. Take 1–3 reference photos standing at your sink with the light on: tap, basin edge, tiles or mirror frame in shot, and no towels or things that move. **Test a wake-up photo** shows the match score you'd get.
3. When the alarm rings, it keeps ringing while you walk to the sink. Point the in-app camera at it, lined up with the faint outline of your reference, and hold still: the alarm stops on its own once about a second of frames in a row match (3 checks a second). **Take photo of sink** still works as a manual fallback.

How it works and its limits:
- **Live camera only**: there's no gallery picker, so an old photo can't be reused. All photos stay on the phone.
- **What's compared**: the edges (tap, basin rim, tile lines) are compared over small shifts, zooms and tilts, which ignores brightness. A colour check comes on top. **Match strictness** (Relaxed / Normal / Strict) sets how close it must be.
- **Tuning**: thresholds were tuned on synthetic bathroom scenes in the unit tests, not real photos. If real shots fail too often, use Relaxed or add another reference from where you usually stand.
- **Fallback**: if the camera permission is off or no reference photo exists, the alarm falls back to the sum, so you're never stuck with an alarm you can't stop.
- **Not tamper-proof**: a determined person can still snooze (set Snooze to Off), photograph a printout of the sink, or force-stop the app from system settings.

## Project layout

```
fajr-alarm/
├── core/   Pure Kotlin/JVM: prayer-time astronomy, alarm planning, photo matching (unit-tested)
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
| `CAMERA` | Sink-photo mission (optional) |
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

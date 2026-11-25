# Prayer Times (Android)

An Android app for the day's prayer times, built around an alarm that wakes you for **Fajr**.
Instead of a fixed clock time, it calculates Fajr (or sunrise) for your location every day and
rings relative to it, so the alarm follows the prayer time as it moves through the year. It
also shows every other prayer time, on the main screen, in widgets and on the lock screen.

The app was called Fajr Alarm until version 1.51. Its folder, package name (`app.fajralarm`)
and update address keep the old name, so installed copies update as before.

## Features

- **Rings relative to Fajr or sunrise**: at the time, or anywhere from 2 h before to 1 h after (5-min steps).
- **Recalculated daily**: after each alarm the next one is computed fresh, and again after a reboot, app update, clock change or timezone change.
- **On by default**: a fresh install has the alarm switched on, so it rings from the first Fajr after you set your location.
- **Pick which days it rings.**
- **Every prayer time of the day**: Fajr, Dhuhr, Asr, Maghrib and Isha, with sunrise and the optional times (Duha, midnight, the last third of the night). The main screen says which prayer it is and how long it has left. See [Prayer times, widgets and the lock screen](#prayer-times-widgets-and-the-lock-screen).
- **A notification for any of those times**, if you want one: a plain notification with the phone's notification sound, never the alarm or a mission. Off until you tap a time's bell.
- **Two home screen widgets**: every time of the day, and the current prayer with a live countdown.
- **The current prayer on the lock screen**: a silent notification that counts down the time it has left.
- **Location** from the device (coarse, no Google Play services needed) or typed in manually.
- **Calculation methods**: Dar al-Fatwa (Lebanon), MWL, ISNA, Egypt, Umm al-Qura, Karachi, Tehran, Dubai, Gulf, Kuwait, Qatar, Singapore, Diyanet, UOIF, or custom Fajr and Isha angles. Asr by the standard rule or the Hanafi one. A ± minute adjustment for each of the five prayers lets you match your mosque's timetable. See [Dar al-Fatwa, Lebanon](#dar-al-fatwa-lebanon).
- **High-latitude rules** (angle-based, one-seventh, middle of the night) for places where twilight never ends in summer.
- **Ringing**:
  - full-screen alarm over the lock screen
  - plays on the alarm stream with any alarm sound or ringtone on the phone (pick your own adhan file if you have one)
  - optional fade-in and vibration
- **Snooze** with a configurable length and a cap on how many times you can snooze. With the sink mission it turns off once sunrise is close.
- **Dismiss missions**: choose what it takes to turn the alarm off.
  - **Find a sink**: the alarm only stops once the live camera recognises a real sink from right in front of it. See below.
  - **Solve a sum**.
  - **None**: a normal dismiss button.

  With a mission on, the notification has no dismiss button, so the alarm screen is the only way to turn it off.
- **Auto-stop**: after 10 minutes with nobody answering, it snoozes if snoozes are left, otherwise it stops. With the sink mission it rings for up to 60 minutes and never past sunrise, so a phone left at home doesn't ring all day.
- **Last alarm**: the main screen shows when the alarm last rang and how it stopped (sink, sum, snooze, ringing out, or Android closing the app). On Android 11 and later it also says whether the app crashed, froze or was force-stopped.
- **"Test the alarm now"** button.
- **English and Arabic**: the app follows the phone's language. See [Languages](#languages).
- **Updates itself** from this repo. See [Updates](#updates).

## Sink mission

1. In **To turn the alarm off → Mission**, pick **Find a sink**. No photos are needed. **Test it** opens a dry run where you can check that your sink is recognised.
2. When the alarm rings, it keeps ringing while you walk to a sink: any bathroom or kitchen sink works. Stand at it and point the in-app camera at the basin, so it sits in the middle of the picture, and keep it there for about 2 seconds. The alarm stops on its own. A sink seen from across the room or through a doorway doesn't count: the screen says to go right up to it.

How it works and its limits:
- **On-phone recognition**: a bundled object-detection model (EfficientDet-Lite0, trained on the 80 everyday COCO objects, "sink" among them) runs on the phone through LiteRT, 3 times a second. It is the TensorFlow Lite build with box decoding built in; if the detector fails to start or errors, the alarm screen switches to a sum instead of closing. Nothing leaves the phone. The one frame that turned the alarm off is kept on the phone and shown under **Last alarm**, so a wrong call can be seen.
- **Why no photos**: there's nothing to set up, so there's no way to register a bedside object as "the sink".
- **Close up and facing it**: the sink's box must cover at least 15% of the picture, which a basin does from about a metre, and the centre of the box must be within a quarter of the picture of its middle. From a doorway two metres off a basin covers about 4%, so it can't be picked off from bed or from outside the bathroom. Both figures are estimates, not measured on real sinks; they are `minArea` and `maxOffCentre` in `SinkVerifier`.
- **Cheating checks**: nothing counts until the phone has been picked up (tilted at least 25° from where it lay, measured with the accelerometer; the alarm's vibration doesn't count). A sink that sits inside a detected phone, TV or laptop screen doesn't count. The sink must stay in view for 2 seconds while the camera moves by at least 5% of the frame, as it does in a hand. Dark frames reset the check.
- **Accuracy**: works best with the light on and the whole basin in view. A close-up of just the tap, or a very dark room, may not be recognised. If your sink isn't recognised from close up, **Test it** shows that before an alarm does. The thresholds haven't been tuned on many real sinks yet.
- **Fallback**: if the camera permission is off or the detector can't start, the alarm falls back to the sum, so you're never stuck with an alarm you can't stop.
- **No way round it from bed**: with a mission set, neither the ringing notification nor the "Snoozed until" notification can turn the alarm off. A snoozed alarm always rings again, and only the mission stops it.
- **Snooze stops near sunrise**: you can snooze as set under **Ringing**, until a snooze would bring the alarm back with less than 15 minutes before sunrise. From then on the snooze button is gone, the alarm screen says sunrise is close, and a snooze tap on the notification does nothing. After sunrise the rule no longer applies.
- **If nobody answers**: it rings for 60 minutes, or up to sunrise if that comes first (at least 10 minutes). If sunrise is still ahead and less than about an hour away, it rests for 5 minutes and rings again until sunrise. Then it gives up: two hours at the most, and one hour for an alarm set after sunrise.
- **Not tamper-proof**: a determined person can still snooze while sunrise is far off (set Snooze to Off), show the camera a printed photo of a sink, turn the camera permission off to get the sum, or force-stop the app from system settings.

## Prayer times, widgets and the lock screen

**The times.** The **Times of the day** card lists the day from Fajr to the last third of the night, with the current prayer marked and the time it has left.

| Time | How it is worked out |
|---|---|
| Fajr, Isha | The sun at the method's angle below the horizon. Umm al-Qura, Gulf and Qatar put Isha 90 minutes after Maghrib instead (Umm al-Qura's 120 minutes in Ramadan is not applied). The high-latitude rule applies to both |
| Sunrise, Maghrib | The sun's upper edge on the horizon. The Tehran method puts Maghrib at 4.5° below it |
| Duha | 15 minutes after sunrise: the start of the Duha prayer's time, which runs until shortly before Dhuhr |
| Dhuhr | Solar noon, with no minutes added |
| Asr | When a shadow is longer than at noon by the object's height (standard), or by twice its height (Hanafi) |
| Midnight | Halfway from sunset to the next Fajr |
| Last third of the night | Two thirds of the way from sunset to the next Fajr: the time for the night prayer (Qiyam, Tahajjud) |

A prayer's time is taken to end when the next prayer starts. Fajr ends at sunrise, and Isha is counted until the next Fajr. Between sunrise and Dhuhr no prayer is current, and the app counts down to Dhuhr instead. Midnight and the last third usually fall after 12 at night, so they are listed with the day whose night they belong to; the list moves on to the next day once the last third has begun.

**Notifications.** Each time except Fajr has a bell. With the bell on, a notification is posted when that time arrives, using the phone's notification sound. It is not the alarm: nothing rings on the alarm stream, there is no mission, and it goes away on its own after an hour. Fajr has no bell because it has the alarm. A notification that would be more than 10 minutes late (the phone was off) is skipped. Sound and vibration are set in the phone's settings for the app, under the **Prayer time notifications** category.

**Widgets.** Add them from **Lock screen & widgets** in the app, or by touching and holding the home screen and choosing Widgets.
- **All times** (4 × 2): the current prayer with a countdown, the five prayers in a row with the current one in gold, and the four optional times under them. Clock times are shown without AM/PM to fit.
- **Current prayer** (2 × 1): the current prayer, the countdown, and the next prayer's time.

The countdown ticks by itself, every second. Tapping a widget opens the app.

**Lock screen.** Most Android phones don't take widgets from apps on the lock screen. Both widgets are marked as allowed there for the devices that do, such as tablets from Android 15; that has not been tried. What works everywhere is a notification, so **Current prayer on the lock screen** keeps a silent one up with the prayer's name and a live countdown. On Samsung phones the lock screen shows notifications as icons until you choose **Settings → Lock screen → Notifications → Cards** (the wording varies by version). From Android 14 the notification can be swiped away; it comes back at the next prayer.

**How it stays current, and what it costs the phone.** Nothing runs between prayer times: no service, no timer, no network. The app queues an alarm for the next time that matters and is asleep until it fires; then it posts that time's notification if its bell is on, redraws the widgets and the lock screen notification, and queues the next one.
- With nothing switched on (no bell, no widget, no lock screen notification) nothing is queued at all.
- A bell wakes the phone once, at that time.
- The lock screen notification wakes it at each of the nine times of the day, so it is right the moment the screen lights up.
- Widgets never wake the phone. Their alarm waits until the phone is awake anyway, and the countdown inside them is drawn by Android, not by the app.
- The widgets don't use Android's periodic widget refresh, which would wake the phone on a timer.

None of this adds a library: the widgets and notifications use what Android has built in. The app's size is still set almost entirely by the sink detector (about 10 MB as published in 1.51).

## Dar al-Fatwa, Lebanon

The **Dar al-Fatwa, Lebanon** method follows Lebanon's official Sunni timetable. It is worked out from the sun for your own coordinates, with nothing downloaded, so it holds for any place in Lebanon and works offline.

| Time | Rule |
|---|---|
| Fajr | Sun 19.5° below the horizon, less 1 minute |
| Dhuhr | Solar noon |
| Asr | Standard (shadow as long as the object, on top of its noon shadow); the Asr setting is yours to change |
| Maghrib | Sunset plus 5 minutes |
| Isha | Sun 17.5° below the horizon, plus 1 minute |

**How far it has been checked: one day.** For Beirut on 25 Nov 2026 the published times were Fajr 4:43, sunrise 6:19, Dhuhr 11:24, Asr 2:11, Maghrib 4:35, Isha 5:56. The app gives the same six, and a test holds it to them. The angles and the 5 minutes come from a comparison of the published times with solar calculations. The 1 minute on Fajr and on Isha was added because the angles alone give 4:44 and 5:55 on that day; quarter-degree larger angles (19.75° and 17.75°) would fit that day equally well and differ by under a minute across the year. Dar al-Fatwa's own site could not be read from here (it turns away automated visitors), so other seasons are not checked, and neither is whether the 5 minutes at Maghrib is the same all year. If a time is off against the printed timetable, the per-prayer adjustments correct it; a few official days from other months would settle the rule.

## Project layout

```
fajr-alarm/
├── core/   Pure Kotlin/JVM: prayer-time astronomy, alarm planning, sink check logic (unit-tested)
└── app/    Android app (Kotlin, Jetpack Compose, Material 3)
    ├── alarm/  AlarmScheduler (AlarmManager.setAlarmClock), receivers, ringing service
    ├── data/   SharedPreferences settings, one-shot location
    ├── prayer/ The wake-up at each time of the day, its notification, the lock screen notification
    ├── widget/ The two home screen widgets
    ├── detect/ On-phone sink detector (LiteRT + bundled model in assets/)
    └── ui/     Settings screen, lock-screen alarm screen, theme
```

## Build & install

Requirements: Android Studio (Ladybug or newer) or the Android SDK with platform 35, and JDK 17+.

```bash
cd apps/fajr-alarm
./gradlew :core:test          # prayer-time unit tests (no Android SDK needed)
./gradlew :app:testDebugUnitTest  # opens the screens in Arabic and English, pictures in app/build/screens; also the sink mission, the prayer notifications and widgets, and the update's background service
./gradlew :app:assembleDebug  # → app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

The APKs are built for 64-bit ARM phones only (`arm64-v8a`), which keeps the detection runtime
to one copy. Remove the `abiFilters` line in `app/build.gradle.kts` to build for every phone.

Or open `apps/fajr-alarm/` in Android Studio and press Run. Min Android version: 8.0 (API 26).

## Languages

The app shows Arabic when the phone's language is Arabic and English otherwise. There is no
language setting in the app: change the phone's language, or on Android 13 and later set
the app's own language under the phone's **Settings → Apps → Prayer Times → Language**.

- In Arabic the screens run right to left, and times, dates and numbers use the digits
  the phone uses for its region (٤:٣٣ in most Arab countries, 4:33 in others).
  Coordinates and the sum stay in 0-9 digits, left to right.
- All text is in `app/src/main/res/values/strings.xml` (English) and
  `values-ar/strings.xml` (Arabic). To add a language, copy the English file into
  `values-<language code>/` and translate it.

## Updates

The app updates itself from this repo. Each time it opens it looks for a newer build, and
a pop-up offers it with an **Update** button when there is one. **Later** closes the pop-up
for now. The version and a **Check for updates** button, which brings the pop-up back, are
at the bottom of the main screen.

- The download is checked against the published SHA-256 before Android installs it.
- The download carries on when the screen turns off or you leave the app, and shows its
  progress in a notification. Android 13 and later ask once to allow that notification;
  the update downloads either way.
- If Android needs your yes to install and the app isn't on screen by then, a notification
  says the update is downloaded. Open the app and tap **Install**; nothing is fetched again.
- A download that stops halfway (no signal, the app closed) carries on from where it
  stopped on the next try. A finished download isn't fetched again if the install is put off.
- The download sits in the app's cache until it is installed and is deleted right after.
  Anything older is deleted each time the app looks for an update.
- The first update asks you to allow the app to install apps, and to confirm. On Android 12
  and later, updates after that install without asking again.
- The app closes when the update is installed; open it again afterwards.
- It won't update while the alarm is ringing, and the next alarm is queued again right
  after an update.
- Publishing a build: see [Updates](../../README.md#updates) in the repo's README.

## Permissions and why

| Permission | Why |
|---|---|
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | Ring at the exact minute, even in Doze. Also moves the widgets and the lock screen countdown on at each prayer time |
| `POST_NOTIFICATIONS` | The ringing notification, a prayer time's notification, the lock screen countdown, and an update's download progress (Android 13+) |
| `USE_FULL_SCREEN_INTENT` | Show the alarm over the lock screen |
| `SYSTEM_ALERT_WINDOW` (Display over other apps) | Open the alarm screen by itself. Android only opens a full-screen alarm when the screen is off or locked, and some phones not even then; without this you'd get just a notification and have to open the app |
| `FOREGROUND_SERVICE(_MEDIA_PLAYBACK)` | Keep playing the alarm sound |
| `FOREGROUND_SERVICE_DATA_SYNC` | Keep an update downloading when the screen turns off |
| `RECEIVE_BOOT_COMPLETED` | Re-queue the alarm after a restart |
| `ACCESS_COARSE_LOCATION` | Optional; you can type coordinates instead |
| `CAMERA` | Sink mission (optional) |
| `WAKE_LOCK`, `VIBRATE` | Ringing. `WAKE_LOCK` also keeps the phone awake while an update downloads |
| `INTERNET` | Only to check for and download the app's own updates |
| `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Installing those updates |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | One-tap fix when battery optimisation could put the alarm to sleep |

The app shows a banner, on the main screen and on the sink test screen, if notifications, exact alarms
or full-screen alarms are blocked, display over other apps is off, or battery optimisation is on, with a button that opens the
right settings page. On Samsung phones it also reminds you once to add the app to
**Never sleeping apps**, since apps can't check that list. Other brands (Xiaomi, Huawei, Oppo)
have similar autostart and background switches in the app's system settings.

## Accuracy

Times come from the standard solar-position formulas. The tests check Fajr and sunrise against
independently computed values (the `astral` library) for London, New York, Makkah and Sydney,
within 2 minutes, and Dhuhr, Asr, Maghrib and Isha against NOAA's solar equations worked
separately for the same places. They also cover a zone far from its longitude (Kiritimati,
UTC+14) and polar day. Local mosque timetables often add a few minutes of caution. Use "Fajr
adjustment" and the ones under it to match yours. Times are shown with the seconds dropped,
not rounded, as the Dar al-Fatwa check day needs.

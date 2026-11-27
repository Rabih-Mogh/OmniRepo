# Safeguard (Android)

A small Android app for taking your time back from your phone. It gives the apps and
websites you choose a daily allowance and hours when they stay closed, keeps named apps
off the phone, closes websites and words you list in any browser, and, once locked,
resists being switched off, loosened or uninstalled.

## Features

- **Time limits**: a limit covers one or more apps and websites (YouTube the app and
  youtube.com in a browser are one limit). Each limit has:
  - a **daily allowance** in minutes. When it is used up, the app or site is closed until
    the next day.
  - **closed hours**: a start, an end and the days they apply. During them it doesn't open at all.

  A new install starts with limits for YouTube, Facebook and Instagram: 30 minutes a day,
  closed Monday to Friday from 9:00 to 17:00. Change them, or add limits for other apps.
- **Blocked apps**: apps that can't be opened or installed. Choose them from the apps on
  the phone, or type the name of one that isn't on it. A blocked app is closed when it is
  opened, and so are its page in the app store and Android's install prompt for it, so an
  app can be blocked before it is ever installed.
- **Blocked websites**: closed in any browser, with everything under them
  (`example.com` also covers `m.example.com`).
- **Blocked words**: a browser page that shows one of them, in its address or its text, is closed.
- **Locking**: unlocked, every change is immediate. Locked:
  - adding a block or making a limit stricter is still immediate;
  - taking anything off a list, loosening a limit, shortening the waiting period or
    unlocking only happens after a **waiting period** you choose (an hour to a week; a day
    to start with). Changes that are waiting are listed with the time left and can be withdrawn;
  - the settings pages that could switch Safeguard off, stop it or uninstall it are closed.
- **Light**: the release APK is about 1.3 MB. It has no account, no ads and no analytics.
- **English and Arabic**: the app follows the phone's language.
- **Updates itself** from this repo. See [Updates](#updates).

## Setting up

Install `apk/safeguard-release.apk` and open the app. Its first page lists the others, one
for each kind of rule: **Time limits**, **Blocked apps**, **Blocked websites**, **Blocked
words**, **Locking** and **Setup**. Open **Setup**, which is red until everything on it is
switched on, and work down its cards. Each one has a button that opens the right settings page.

1. **Accessibility**: switch Safeguard on under the phone's Accessibility settings. This is
   what lets it see which app is in front. On Android 13 and later the phone may say the
   setting is restricted for an app installed from a file: open the app's info page, tap
   the three dots at the top, choose **Allow restricted settings**, then try again.
2. **Device admin app**: allow it. Android won't uninstall a device admin app until that
   is switched off again.
3. **Updates**: allow Safeguard to install its own updates, so an update never needs a
   settings page that a locked Safeguard keeps closed.
4. Set the limits and lists the way you want them on their pages, then open **Locking**,
   choose the waiting period, and tap **Lock**.

## How it works, and its limits

- **Seeing what is in front**: an accessibility service is told by Android when the window
  in front changes. For most apps it only learns which app that is. In browsers, app
  stores and the phone's settings it also reads the text on screen: the address bar and
  page for websites and words, the store page for app names, and the settings page to
  see whether it is about Safeguard. Nothing it reads is kept, and nothing leaves the phone.
- **Closing**: a closed app is covered by a screen that says why, and **OK** goes to the
  home screen. A closed page (a website, a store page, a settings page) is stepped back
  out of first, so the app it was in stays usable.
- **Counting time**: a limit's time runs while one of its apps, or a browser on one of its
  sites, is in front with the screen on. Picture-in-picture and sound playing in the
  background don't count.
- **Any browser**: a browser is any app that offers to open web links, so no list of
  browsers is needed. The address is read from the box that can be typed into, or one
  named like an address bar. A browser that shows its address some other way won't have
  its websites recognised; blocked words on the page still are.
- **Blocked apps go by name**: an app is blocked when its name on the phone contains the
  name on the list, so "TikTok" also covers "TikTok Lite". In the app stores (Google Play,
  Galaxy Store, AppGallery, GetApps, F-Droid, Aurora Store) and Android's install prompt, a
  page is closed whenever a listed name is on it, which includes search results that show it.
- **The waiting period can't be skipped by setting the clock**: it is measured by how long
  the phone has been running, not by the clock. Across a restart it goes by the clock.
- **Closed hours and the daily allowance do go by the phone's clock.** Setting the time or
  the time zone by hand gets round them. This is not closed off yet.
- **Not tamper-proof.** A locked Safeguard closes its own pages in the settings, and that
  works by reading those pages, so a phone whose settings are laid out unusually may leave
  one open. Starting the phone in **safe mode** switches every installed app's
  accessibility service off, Safeguard's included. A computer with developer access to
  the phone can also stop it. Closing those would need the phone set up with Safeguard as
  its device owner, which this version doesn't do.
- **Locked, any settings page that names Safeguard is closed**, including the list of all
  apps while Safeguard's row is on screen. Use the search in the settings to reach other apps.
- **Not tried on a phone yet.** The rules, the waiting, the service's decisions and the
  screens are tested on a computer (see below). How each phone's settings, stores and
  browsers present themselves to the service is only known from running it there.

### If something goes wrong while it is locked

Start the phone in safe mode (hold the power button, then press and hold **Power off**
until it offers safe mode). Safeguard doesn't run there. Under **Settings → Security →
Device admin apps** switch Safeguard off, then uninstall it, and restart the phone.

## Project layout

```
safeguard/
├── core/   Pure Kotlin/JVM: the rules, closed hours, what counts as loosening, waits, reading an address (unit-tested)
└── app/    Android app (Kotlin, Jetpack Compose, Material 3)
    ├── data/    The rules, waiting changes and the day's use, kept in SharedPreferences
    ├── guard/   The accessibility service that watches and closes; the device admin receiver
    ├── ui/      The first page and the page for each kind of rule, the limit editor, the screen shown over what was closed
    └── update/  The self-updater
```

## Build & install

Requirements: Android Studio (Ladybug or newer) or the Android SDK with platform 35, and JDK 17+.

```bash
cd apps/safeguard
./gradlew :core:test              # the rules, hours, waits and address reading (no Android SDK needed)
./gradlew :app:testDebugUnitTest  # the watching service on made-up windows, storage, the updater's download and background service, and the screens in English and Arabic (pictures in app/build/screens)
./gradlew :app:assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open `apps/safeguard/` in Android Studio and press Run. Min Android version: 8.0 (API 26).

## Languages

The app shows Arabic when the phone's language is Arabic and English otherwise. There is no
language setting in the app: change the phone's language, or on Android 13 and later set
the app's own language under the phone's **Settings → Apps → Safeguard → Language** (while
Safeguard is unlocked; locked, that page is closed).

All text is in `app/src/main/res/values/strings.xml` (English) and
`values-ar/strings.xml` (Arabic). To add a language, copy the English file into
`values-<language code>/` and translate it.

## Updates

The app updates itself from this repo. Each time it opens it looks for a newer build, and
a pop-up offers it with an **Update** button when there is one. **Later** closes the pop-up
for now. The version and a **Check for updates** button, which brings the pop-up back, are
at the bottom of the first page.

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
- The first update asks you to confirm. On Android 12 and later, updates after that
  install without asking again.
- The app closes when the update is installed; open it again afterwards. The rules, the
  lock and the changes that are waiting are kept, and Android switches the watching back
  on by itself.
- A locked Safeguard lets Android's install prompt through while its own update is being installed.
- Publishing a build: see [Updates](../../README.md#updates) in the repo's README.

## Permissions and why

| Permission | Why |
|---|---|
| Accessibility service | See which app is in front, read browser, store and settings pages, and step back out of what is closed |
| Device admin app (no policies) | Android won't uninstall the app while this is on |
| `INTERNET` | Only to check for and download the app's own updates |
| `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Installing those updates |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Keep an update downloading when the screen turns off |
| `WAKE_LOCK` | Keep the phone awake while an update downloads |
| `POST_NOTIFICATIONS` | An update's download progress (Android 13+) |

The app also asks Android which apps have an icon on the home screen (to choose the ones
to limit) and which apps open web links (to know the browsers).

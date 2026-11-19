# OmniRepo

A home for several small apps. Each app lives in its own folder under `apps/` and is a
self-contained project: it has its own build, dependencies and README, so apps don't
affect each other and can use different languages or toolchains.

## Apps

| App | What it is | Platform |
|---|---|---|
| [Fajr Alarm](apps/fajr-alarm/) | An alarm that rings relative to Fajr or sunrise, with missions (a sum, or finding a real sink) to turn it off | Android |
| [Accent Coach](apps/accent-coach/) | Read a sentence aloud and see the sounds you made next to a native American speaker's, with non-native sounds highlighted | Android |
| [Recitation](apps/recitation/) | Listen to a recitation of the Quran from a YouTube playlist and follow it word by word, with each word's meaning and the word being recited lit up | Android |

## Layout

```
OmniRepo/
├── README.md          This file: the list of apps
└── apps/
    ├── fajr-alarm/    Android (Kotlin, Gradle); see its README
    │   └── apk/       Ready-to-install builds
    ├── accent-coach/  Android (Kotlin, Gradle); see its README
    │   └── apk/       Ready-to-install builds
    └── recitation/    Android (Kotlin, Gradle); see its README
        └── apk/       Ready-to-install builds
```

## Adding an app

1. Create `apps/<app-name>/` (lowercase, words separated by hyphens).
2. Keep everything the app needs inside that folder: build files, its own `.gitignore`,
   and a `README.md` saying what it does and how to build and run it.
3. If the app ships something ready to install, put it in `apps/<app-name>/apk/`
   (or a similarly named folder for that platform).
4. Add a row to the **Apps** table above.

Build an app from its own folder, for example:

```bash
cd apps/fajr-alarm && ./gradlew :app:assembleRelease
```

## Updates

Every app updates itself from this repo. When one opens, it reads
`apps/<app-name>/apk/version.json` on the `main` branch; if that names a higher version
than the one installed, a pop-up offers to download and install the new APK. The download
runs in the background with its progress in a notification, so it carries on when the
screen turns off. A download that is interrupted carries on where it stopped, and is
deleted once it is installed.

To publish a build, run this before committing, then commit the `apk/` folder (the APKs
need `git add -f`):

```bash
tools/publish-apk.sh fajr-alarm accent-coach recitation
```

It runs the tests, builds the app, copies the APKs into `apk/` and writes `version.json`
(version, file size and SHA-256).

- **Versions:** the version number is the repo's commit count plus a fixed number, so each published
  build is higher than the last without anyone editing a number.
- **Signing key:** Android only installs an update signed with the same key as the
  installed app. The APKs are signed with the debug key of the Mac they're built on
  (`~/.android/debug.keystore`). A build from another machine, or after that file is
  lost, won't install over an existing copy: the app has to be uninstalled first.

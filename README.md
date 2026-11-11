# OmniRepo

A home for several small apps. Each app lives in its own folder under `apps/` and is a
self-contained project: it has its own build, dependencies and README, so apps don't
affect each other and can use different languages or toolchains.

## Apps

| App | What it is | Platform |
|---|---|---|
| [Fajr Alarm](apps/fajr-alarm/) | An alarm that rings relative to Fajr or sunrise, with missions (a sum, or finding a real sink) to turn it off | Android |

## Layout

```
OmniRepo/
├── README.md          This file: the list of apps
└── apps/
    └── fajr-alarm/    Android (Kotlin, Gradle); see its README
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

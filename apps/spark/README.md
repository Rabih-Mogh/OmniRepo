# Spark (Android)

A feed of short reels that teach, in place of the ones that don't. Each reel is one idea
on one screen: a hook, a moving picture, a few lines, and the sentence worth keeping.
The app learns which reels hold your attention and orders the feed by it. Some reels open
into a short lesson you do with your hands. A new batch of reels and lessons, made for
what you asked to learn and what has held you, is prepared every morning.

## Features

- **Reels**: swipe up for the next. A reel's parts come up one after another; a tap brings
  the next part at once. Each has a subject, a picture the app draws and animates itself
  (a number counting up, bars, dots, a curve, steps in a chain), and a takeaway. There are
  three kinds: an **idea**, a piece of **news** with a link to its source, and a **puzzle**
  that keeps its answer until you ask for it.
- **A feed that learns**: how long you stay on a reel, a like, opening its lesson, and
  **Not for me** all move what comes next. See [How the feed is ordered](#how-the-feed-is-ordered).
- **Lessons**: five to eight steps each. Besides reading, a step can ask you to pick an
  answer, put things in order, guess a number on a slider, or drag a curve until it passes
  through a point. Each is marked on the first answer and then explains itself.
- **What I want to learn**: a list you write in the app. Reels that match it come first,
  and sending the list has reels and lessons made for it in a coming batch.
- **A daily goal, then a gentle stop**: 5, 10, 15 or 20 reels and one lesson a day. Once
  you reach it the feed says you're done for today, with your streak. Swiping past it
  carries on.
- **A new batch every morning**: about 15 reels and 2 lessons, a third of them news from
  the last few days. See [Where the reels come from](#where-the-reels-come-from).
- **English and Arabic**: the app follows the phone's language. The reels and lessons
  themselves are in English for now.
- **Light**: no account, no ads, no analytics. What it learns about you stays on the
  phone unless you send it.
- **Updates itself** from this repo. See [Updates](#updates).

## How the feed is ordered

Each time you arrive on a reel, the app chooses the one to come after it from everything
you haven't seen. Every candidate gets a score, and the highest goes next:

- **Your taste.** The app keeps a number from -1 to 1 for every subject and every tag,
  moved a little by each reel you leave: up when you watched it through, liked it or took
  its lesson, down when you flicked past it or said **Not for me**. Recent reels count most.
- **Your wishes.** A reel whose tags or hook share a word with something on your list is
  moved up.
- **Freshness.** News counts for most on the day it arrives and fades within a week.
- **Trying things.** Subjects you've been shown least get a nudge, so the feed keeps
  testing your taste and doesn't narrow to one subject.
- **Variety.** A third reel in a row on one subject is held back while another is waiting.
- **A little chance**, so two days don't run in the same order.

The screen behind the streak pill shows what it has learned, as a bar for each subject.
All of it happens on the phone. Time spent outside the feed, in a lesson or with the app
in the background, isn't counted as attention.

## Where the reels come from

- **The starter pack** ships inside the app: 40 reels and 8 lessons across maths, science,
  computing and ideas, so the feed is full the first time it opens.
- **Daily packs** are published on this repo's `spark-content` branch. The app reads
  `index.json` there each time it comes to the front (at most once an hour), fetches the
  packs it doesn't have, and keeps them. Packs older than 45 days are dropped from the
  index and the app deletes them.
- **Who writes them**: a scheduled run of an AI model on the owner's computer, every
  morning. It reads the newest wishes sent from the app, looks for news in those subjects,
  writes the pack, checks it, and pushes it to the `spark-content` branch.
  [content/GENERATE.md](content/GENERATE.md) is its brief and the pack format.
- **Sending your wishes**: **Send the list** opens a new issue on this repo, titled
  "Spark wishes", with your list and a summary of your taste filled in. Submit it signed
  in as the repo's owner. The run reads the newest such issue from the owner and ignores
  everyone else's. The issue, like the packs, is public.
- **Checking**: the app reads a pack with the same code as the checker
  (`./gradlew -q :core:checkPack -Ppack=…`), and leaves out any reel or lesson that breaks
  a rule, so one bad item can't break the feed.

### Limits

- **Not tried on a phone yet.** The feed's choices, the lessons, the fetching, the storage
  and the screens are tested on a computer (see below). How the swiping and the animations
  feel on a real phone is only known from running it there.
- The reels are text and drawings, not video.
- The feed is only as good as the packs. If the morning run doesn't happen (the computer
  is off), there is no new batch that day, and when every reel has been seen the feed says so.
- A model writes the packs. They are checked for form by a program and for sense by the
  model itself, not by a person, so a mistake in a fact can get through.
- The taste lives in the app's storage: uninstalling the app or clearing its data starts
  it again.

## Project layout

```
spark/
├── core/      Pure Kotlin/JVM: the pack format and its checker, formulas, the feed's ordering, the day's goal and streak (unit-tested)
├── app/       Android app (Kotlin, Jetpack Compose, Material 3)
│   ├── data/    The library of packs (starter and fetched) and what is remembered about the user
│   ├── ui/      The feed and its reels, the lesson screen, the screen about you, the first screen
│   ├── update/  The self-updater
│   └── src/main/assets/starter.json  The starter pack
└── content/   GENERATE.md: how to write, check and publish a pack
```

## Build & install

Requirements: Android Studio (Ladybug or newer) or the Android SDK with platform 35, and JDK 17+.

```bash
cd apps/spark
./gradlew :core:test              # the pack reader, formulas, the feed's ordering, the goal and streak (no Android SDK needed)
./gradlew :app:testDebugUnitTest  # the feed walked through, a lesson taken, fetching packs from a stand-in server, storage, every starter reel on a small phone, the updater, and the screens in English and Arabic (pictures in app/build/screens)
./gradlew :app:assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open `apps/spark/` in Android Studio and press Run. Min Android version: 8.0 (API 26).

## Languages

The app shows Arabic when the phone's language is Arabic and English otherwise. There is no
language setting in the app: change the phone's language, or on Android 13 and later set
the app's own language under the phone's **Settings → Apps → Spark → Language**.

All of the app's own text is in `app/src/main/res/values/strings.xml` (English) and
`values-ar/strings.xml` (Arabic). To add a language, copy the English file into
`values-<language code>/` and translate it. The reels and lessons are in English and read
from the left in either language.

## Updates

The app updates itself from this repo. Each time it opens it looks for a newer build, and
a pop-up offers it with an **Update** button when there is one. **Later** closes the pop-up
for now. The version and a **Check for updates** button, which brings the pop-up back, are
at the bottom of the screen behind the streak pill.

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
- The app closes when the update is installed; open it again afterwards. Your taste, your
  list, the streak and the fetched packs are kept.
- Publishing a build: see [Updates](../../README.md#updates) in the repo's README.

## Permissions and why

| Permission | Why |
|---|---|
| `INTERNET` | Fetching new packs from this repo, and checking for and downloading the app's own updates |
| `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Installing those updates |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Keep an update downloading when the screen turns off |
| `WAKE_LOCK` | Keep the phone awake while an update downloads |
| `POST_NOTIFICATIONS` | An update's download progress (Android 13+) |

A news reel's source link and **Send the list** open in the phone's browser.

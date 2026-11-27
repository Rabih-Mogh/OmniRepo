# Spark (Android)

A feed of short reels that teach, in place of the ones that don't. Each reel is one idea
on one screen: a hook, a moving picture, a few lines, and the sentence worth keeping.
The app learns which reels hold your attention and orders the feed by it. Some reels open
into a short lesson you do with your hands. When the feed runs low, the app writes new
reels and lessons itself, for what you asked to learn and what has held you, with a free
key of your own for Google's Gemini.

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
  - **A guess first**: every lesson opens by asking, before it explains anything.
  - **Your answer against the right one**: a guess on the slider stays where you put it
    while the answer travels out from it to where it belongs; things put in order move to
    their right places, still wearing the numbers you gave them; a wrong pick shakes and
    the right one swells.
  - **What was wrong with your pick**: every wrong option has its own explanation, shown
    when you pick it, before the reason the right one is right.
  - **Pictures that explain**: a step can show a picture with its question and another
    with its explanation, drawn as it appears: the answer as bars, as a share of a ring,
    as points on a line, as squares in a grid, or as a curve with the answer marked on it
    and a second curve to set it against. Each says underneath what it shows. In the
    starter lessons 40 of the 57 steps have one.
- **What I want to learn**: a list you write in the app. Reels that match it come first,
  and the next reels and lessons written are made for it.
- **A daily goal, then a gentle stop**: 5, 10, 15 or 20 reels and one lesson a day. Once
  you reach it the feed says you're done for today, with your streak. Swiping past it
  carries on.
- **A forgiving streak**: a day counts towards the streak once you watch 3 reels or finish
  a lesson, well short of the goal. One missed day in a week is let go without a word; a
  second ends the streak, and nothing announces it.
- **New reels when you need them**: about 15 reels and 2 lessons, a few of them news from
  the last few days, written on the phone when the feed is running out or has gone old.
  See [Where the reels come from](#where-the-reels-come-from).
- **Little sounds**: a chime and a tap of vibration for a right answer, a softer one for a
  wrong one, and others for a pick, a puzzle's answer, a finished lesson and the day's goal.
  Right answers in a row each sound a note higher than the last, and a slider ticks under
  the finger as it moves.
  The app makes the sounds itself, so no sound file ships with it. They play at the media
  volume, keep quiet while the phone is on silent or vibrate, and can be turned off on the
  screen behind the streak pill.
- **English and Arabic**: the app follows the phone's language. The reels and lessons
  themselves are in English for now.
- **Light**: no account in the app, no ads, no analytics. What it learns about you stays
  on the phone, except what goes to Google when the phone writes reels with your key (see
  [Written on the phone](#written-on-the-phone)), and what you choose to send.
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
- **Written on the phone**, with a key of yours: see [below](#written-on-the-phone).
- **Published packs**: the app also reads `index.json` on this repo's `spark-content`
  branch each time it comes to the front (at most once an hour), fetches the packs it
  doesn't have, and keeps them. Packs older than 45 days are dropped from the index and
  the app deletes them. A scheduled run of an AI model on the owner's computer can write
  and publish one each morning; [content/GENERATE.md](content/GENERATE.md) is its brief
  and the pack format. Without a key in the app, **Send the list** opens a new issue on
  this repo, titled "Spark wishes", with your list and a summary of your taste filled in,
  for that run to read. The issue, like the packs, is public.
- **Checking**: every pack, written on the phone or fetched, is read with the same code as
  the checker (`./gradlew -q :core:checkPack -Ppack=…`), and any reel or lesson that
  breaks a rule is left out, so one bad item can't break the feed.

### Written on the phone

Behind the streak pill, **Reels written on this phone** takes a Gemini API key. The app
shows the steps: open [Google AI Studio](https://aistudio.google.com/api-keys), sign in,
accept its terms, copy the key, paste it. The key is free and needs no subscription.

- **When it writes**: only when it has to. Each time the app comes to the front, and when
  the feed turns low while it is open, it writes a pack if fewer reels are left unseen
  than your daily goal, or your newest pack is more than a week old. At most one pack a
  day, and after a try that failed it waits an hour. **Write new reels now** does it by hand.
- **What it asks for**: two answers, the reels and then the lessons. Each request carries
  the rules of a pack ([content/GENERATE.md](content/GENERATE.md), which ships in the
  app), a few reels or a lesson from the starter pack as examples of the standard, your
  list and your taste, and what the feed has already shown, so nothing is repeated.
- **News** comes from three public feeds (ScienceDaily, Phys.org and BBC News
  technology). The model is given each story's title, one-line summary and address, and
  told to say no more than they do. A news reel whose link isn't one of those addresses
  is left out: a link nobody handed the model is one it made up.
- **Checking**: each answer is checked like any pack and sent back once with what was
  wrong. What still breaks a rule is left out.
- **Which model**: the first that answers of the free ones Google lists for the key
  (Gemini 3.8 Flash first).
- **Your key** is kept in a file of the app's that Android leaves out of backups. It goes
  to Google in a header with each request and nowhere else. The app shows only its last
  four characters, and **Remove** deletes it.
- **What Google sees**: your list, the subjects and tags that held you, and the hooks of
  reels you already have. On the free plan, Google's terms let it use what is sent to
  improve its products. Google's terms also treat the free plan differently for users in
  the European Economic Area, Switzerland and the UK: read them before relying on it there.

### Limits

- **Not tried on a phone yet.** The feed's choices, the lessons, the fetching, the storage
  and the screens are tested on a computer (see below). How the swiping and the animations
  feel on a real phone is only known from running it there.
- The reels are text and drawings, not video.
- The sounds are checked for their shape (length, loudness, rising or falling) and for when
  they play. How they sound on a phone's speaker is only known from listening there.
- **Not tried with a real key yet.** The requests to Google are written from its
  documentation and tested against a stand-in server on a computer. Whether the service
  answers them as documented, how good what it writes is, and how far the free allowance
  goes are only known from using a real key.
- The feed is only as good as the packs. Without a key, and with nothing published, there
  is only the starter pack, and when every reel has been seen the feed says so.
- A model writes the packs. They are checked for form by a program, not for sense by a
  person, so a mistake in a fact can get through. News reels are written from a feed's
  headline and one-line summary, not from the story itself.
- One pack a day: if you finish it the same day, more comes tomorrow.
- The taste lives in the app's storage: uninstalling the app or clearing its data starts
  it again.

## Project layout

```
spark/
├── core/      Pure Kotlin/JVM: the pack format and its checker, formulas, the feed's ordering, the day's goal and the streak, the sounds (unit-tested)
├── app/       Android app (Kotlin, Jetpack Compose, Material 3)
│   ├── data/    The library of packs (starter, fetched and written here), the writer that asks Gemini, the news feeds, and what is remembered about the user
│   ├── ui/      The feed and its reels, the lesson screen, the screen about you, the first screen, playing the sounds
│   ├── update/  The self-updater
│   └── src/main/assets/starter.json  The starter pack
└── content/   GENERATE.md: how to write, check and publish a pack
```

## Build & install

Requirements: Android Studio (Ladybug or newer) or the Android SDK with platform 35, and JDK 17+.

```bash
cd apps/spark
./gradlew :core:test              # the pack reader, formulas, the feed's ordering, the goal and streak, the sounds' shape, what a model is asked (no Android SDK needed)
./gradlew :app:testDebugUnitTest  # the feed walked through, a lesson taken, every picture of the starter lessons drawn, fetching packs from a stand-in server, a pack written with a stand-in for Google, the news feeds read, storage, when the sounds play, every starter reel on a small phone, the updater, and the screens in English and Arabic (pictures in app/build/screens)
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
- If Android's install prompt is closed without an answer, the app notices when you come
  back to it and offers the downloaded update again with **Install**.
- It looks for a newer build again each time you come back to it, at most every ten
  minutes. A build newer than the one the pop-up was closed on brings the pop-up back.
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
| `INTERNET` | Asking Gemini for new reels with your key, reading the news feeds, fetching packs from this repo, and checking for and downloading the app's own updates |
| `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Installing those updates |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Keep an update downloading when the screen turns off |
| `WAKE_LOCK` | Keep the phone awake while an update downloads |
| `POST_NOTIFICATIONS` | An update's download progress (Android 13+) |

A news reel's source link and **Send the list** open in the phone's browser.

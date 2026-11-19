# Recitation

An Android app for listening to a recitation of the Quran and following it word by word.
Give it a YouTube playlist of a recitation: it downloads the sound only, works out when
each word is recited, and shows every verse as a row of words with their meanings. The
word being recited is lit and fills in letter by letter, and the page scrolls along.

The app is in English and Arabic, following the phone's language, and is green by day and
gold in dark mode.

## How it works

1. **Add a playlist.** Paste a YouTube or YouTube Music playlist link, or use **Share** in
   YouTube and pick Recitation. One surah per video is expected. The app reads which surah
   each video is from its title ("سورة البقرة", "Surah Al-Baqarah", "002 | …"); tap the
   surah under a recording to correct it.
2. **Get recordings ready.** For each recording the app:
   - downloads the sound (not the video);
   - fetches the surah's words, each word's meaning, and the verse translation;
   - has a speech model listen to the whole recording on the phone;
   - lines the surah's words up with what the model heard.

   This runs in the background with its progress in a notification. Listening is the slow
   step: expect it to take roughly as long as the recording, once. If it is interrupted
   (no signal, the phone restarts), it carries on from where it stopped.
3. **Read along.** Open a recording that is ready. Tap a word to jump to it. The buttons
   beside play go to the previous and next verse. The button at the top right shows or
   hides the meanings. When a recording ends, the playlist's next prepared one follows.
   Sound carries on with the screen off, with controls in the notification.

### Lining words up with the sound

- **The words as sounds:** `core/Phonemizer.kt` turns each word, written in the Uthmani
  script with all its marks, into the sounds a reciter makes. That script spells recitation
  out closely (a letter with no mark is silent or merged into the next, long vowels and
  stops are marked), so following the marks gets close enough.
- **The model:** `wav2vec2-lv-60-espeak-cv-ft` (Facebook AI, Apache-2.0), the same model
  Accent Coach uses. It writes down the sounds it hears, in any language, rather than
  guessing words.
- **Alignment:** `core/Aligner.kt` finds the best way to place the surah's sounds, in
  order, on what the model heard across the whole recording. Sounds a reciter may leave
  out (a vowel dropped at a stop, the opening *a'udhu* and *basmala*) are optional, and
  anything said before or after the surah is skipped.
- **Wrong surah:** the alignment also says how much of the surah the model heard clearly.
  When that is low (the wrong surah was chosen, or the recording stops half way), the app
  says so instead of lighting words up at random.

### The speech model

The model is 318 MB, too big to ship in the APK. If [Accent Coach](../accent-coach/) is
installed and has it, Recitation copies it from there; otherwise it downloads it once,
pinned to one revision. Either way the file is checked against its SHA-256. Everything
after that runs on the phone with ONNX Runtime.

## How accurate it is

Measured on this project's Mac, with the same model file and the same code as the app,
against the word timings Quran.com publishes for the same recordings:

| Recording | Half the words within | Within 0.3 s |
|---|---|---|
| Mishari al-`Afasy, Al-Mulk | 35 ms | 92% |
| As-Sudais, An-Naba | 40 ms | 97% |
| Al-Husary, Al-Mulk | 60 ms | 92% |
| Abdul Basit (mujawwad, slow and melodic), Al-Fatihah | 140 ms | 85% |

Published timings start a word where the one before ended, even across a breath; for this
table such a start was moved to where the sound resumes.

`core`'s tests line up a real recitation of Al-Fatihah (as the model heard it) and check
it against its published timings.

## Limits

- **Not yet run on a phone.** The app was built and its logic tested on a Mac. Decoding
  the sound, running the model, playback, background work and installing updates use
  Android itself and have only been compiled.
- **One surah per video.** A video with several surahs, part of a surah, or verses
  repeated (as in prayer) won't line up; the app will usually say it doesn't match.
- **Old or echoing recordings** are heard less clearly and line up less tightly.
- **Speed on a phone is unknown.** On the Mac the model runs 25 times faster than the
  recording plays. A phone is much slower; "about as long as the recording" is a guess.
- **YouTube changes.** Reading YouTube without its app depends on NewPipe Extractor
  keeping up with YouTube. When downloads stop working, the fix is a newer version of that
  library in `yt/build.gradle.kts` and a new build, which the app's updater delivers.
- **YouTube's terms and the reciter's rights.** Downloading from YouTube is against its
  terms of service, and a recording belongs to its reciter or publisher. Use this for
  recordings you may keep a copy of.
- **Meanings are in English only**, so they start hidden when the phone is in Arabic.

## Sources

| What | From | Licence |
|---|---|---|
| Quran text (Uthmani script), word meanings, verse translation (Saheeh International) | [Quran.com](https://quran.com) API, fetched by the app per surah and kept on the phone; not stored in this repo | Their own terms |
| Speech model | [wav2vec2-lv-60-espeak-cv-ft](https://huggingface.co/facebook/wav2vec2-lv-60-espeak-cv-ft), ONNX export by onnx-community | Apache-2.0 |
| Reading YouTube | [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) | GPL-3.0 |
| Arabic font | [Amiri Quran](https://github.com/aliftype/amiri) | SIL Open Font License ([text](licenses/AmiriQuran-OFL.txt)) |

Because it includes NewPipe Extractor, this app's own code is under the
[GPL-3.0](LICENSE).

## Project layout

```
recitation/
├── core/    Pure Kotlin/JVM, unit-tested: words to sounds, listening in pieces, alignment,
│            word timings, surah names and reading them from titles
├── yt/      Pure Kotlin/JVM: playlists and sound from YouTube (NewPipe Extractor)
├── app/     Android app (Jetpack Compose)
│   ├── data/     The playlists added, and the verses from Quran.com
│   ├── speech/   Model copy or download, sound decoding, ONNX model
│   ├── prepare/  The background work that gets a recording ready
│   ├── player/   Playback service
│   └── ui/       Playlists, recordings and reading screens
├── tools/
│   ├── make_surahs.py            Writes core's list of surah names from Quran.com
│   └── make_test_recitation.py   Writes the real recitation core's tests align
└── apk/     Ready-to-install builds
```

## Build

Needs JDK 17 and the Android SDK (platform 35).

```bash
cd apps/recitation
./gradlew :core:test              # unit tests for the logic
./gradlew :app:testDebugUnitTest  # draws each screen, in both languages and themes, to app/build/screens
./gradlew :app:assembleRelease    # → app/build/outputs/apk/release/app-release.apk
```

To check that YouTube can still be read (this talks to YouTube, so it isn't part of the
normal tests):

```bash
YT_LIVE=1 YT_URL="https://www.youtube.com/playlist?list=..." ./gradlew :yt:test
```

Builds are for 64-bit ARM phones only (`arm64-v8a`). Minimum Android version: 8.0 (API 26).

## Updates

The app updates itself from this repo. Each time it opens it looks for a newer build, and
a pop-up offers it with an **Update** button when there is one. **Later** closes the pop-up
for now. The version and a **Check for updates** button, which brings the pop-up back, are
at the bottom of the first screen.

- The download is checked against the published SHA-256 before Android installs it.
- A download that stops halfway (no signal, the app closed) carries on from where it
  stopped on the next try. A finished download isn't fetched again if the install is put off.
- The download sits in the app's cache until it is installed and is deleted right after.
  Anything older is deleted each time the app looks for an update.
- The first update asks you to allow the app to install apps, and to confirm. On Android 12
  and later, updates after that install without asking again.
- The app closes when the update is installed; open it again afterwards.
- Publishing a build: see [Updates](../../README.md#updates) in the repo's README.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Reading playlists, downloading sound, the verses, the speech model, and the app's own updates. |
| `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Installing those updates. |
| `POST_NOTIFICATIONS` | Showing progress while recordings are prepared. The work runs without it too. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK` | Keeping that work going in the background. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Reciting with the screen off. |

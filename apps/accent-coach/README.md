# Accent Coach

An Android app for practising a native **General American** English accent. Read a
sentence aloud: the app writes down the sounds you actually made (in IPA), lines them
up with how a native speaker says the sentence, and highlights the sounds that give
away a non-native accent, with a tip for each. It also teaches the hardest sounds one at
a time, each with a lesson on how the sound is made and a drill of words, lets you add
the words you find hard and practise those, and keeps a streak of the days you practised.

## How it works

1. **Pick a sentence.** 51 sentences, grouped by the sounds learners find hardest: TH,
   American R, L, V/W, short i vs long ee, the cat, cup and cot vowels, the "ay" and
   "oh" glides, the flap T in *water*, final consonants, H and NG, and everyday phrases.
   **Listen** (or **Slowly**) plays it with the phone's American English voice.
2. **Read it aloud.** While you speak, **What I hear** fills in with the sounds heard
   so far.
3. **See the result.** Each word shows the native sounds and yours, coloured:
   - green: native, or a variant native speakers use too (e.g. the flap T);
   - amber: close but slightly foreign (e.g. a flat "e" for the "ay" glide);
   - red: a different sound, where the accent shows (e.g. "t" for TH, a rolled R,
     or a vowel added after a final consonant);
   - grey and struck through: a sound you left out.

   You also get a score, a tip for every sound to work on, and buttons to play your
   own recording next to the native voice.

### Sound lessons and drills

The **Sounds** tab teaches before it tests. It has 15 lessons, one per sound: the two THs,
the American R, L, V, W, short i, long ee, the cat and cup vowels, the "ay" and "oh"
glides, the American T in *water*, NG and H.

- **The lesson** says how the sound is made (where the tongue, lips and jaw go, and
  whether the voice is on) and the slip most often made with it. Its words are listed
  under it; tap one to hear it.
- **The drill** is nine things to say: six words with the sound, then three pairs of words
  that differ in that sound only (*think, sink*). The two lessons with no such pairs, the
  American T and H, have nine words.
- **Only the lesson's sound is judged.** A word is *native* when that sound was, whatever
  happened to the rest of it; *close* when the sound was a near miss; *not yet* when it was
  another sound or was left out. In a pair, the sound the two words differ in is judged in
  both, so saying the same word twice doesn't pass.
- **A word can be tried again**, and its last try is the one that counts.
- **A lesson is learned** once a drill ends with three in four native (seven of nine).
  The list shows a tick on the learned ones and the best result of each.

### My words

The **My words** tab is for the words you can't get right. Type a word or a short phrase
(up to six words; letters, apostrophes and hyphens) and tap **Add**. It joins your list
with its native sounds, and you practise it like a sentence: **Listen**, say it, and see
each sound coloured, with a score and tips. The list keeps your best score for each word.

Where a word's native sounds come from:

- **The dictionary.** The app carries the sounds of about 234,000 English words, made
  with espeak-ng like the sentences'. A word found there is added at once.
- **The phone's voice.** A word that isn't there (*undisablable*, a name, a plural such
  as *vegetables*) is said by the phone's American English voice into a file, and the
  recogniser writes down what it hears. The word's page says when its sounds were got
  this way. They are only as good as that hearing, so such a word is compared more
  kindly: a sound counts as native if either it or the reference is a native variant of
  the other.

Adding a word that isn't in the dictionary needs an American English text-to-speech
voice on the phone, the same one **Listen** uses. The words are kept in a small file on
the phone (`files/words.json`), like the streak.

### Streak

A day counts once five recordings have been scored on it: sentences, drill words and
your own words alike. A recording in which nothing was heard doesn't count. The card at the top shows:

- how many days in a row have counted, up to today, or up to yesterday while today's five
  aren't done yet;
- today's recordings as a ring that closes at five;
- the last seven days as dots: filled for a day that counted, tinted for one with some
  practice.

The days and the drills' results are kept in a small file on the phone
(`files/progress.json`). They aren't backed up, and uninstalling the app clears them.

### The speech model

- **Model:** the sounds are recognised by `wav2vec2-lv-60-espeak-cv-ft` (Facebook AI,
  Apache-2.0), using an 8-bit ONNX export by onnx-community.
- **Why this one:** it was trained on 60,000 hours of English and then on speech in
  many languages. Because of that it writes down the sound actually spoken, including
  sounds English doesn't use, such as a rolled `r`, a French `ʁ` or a pure `e`. It
  doesn't guess the word you meant, which is why a non-native sound shows up as a
  different symbol.
- **Download:** the model is 318 MB, too big to ship in the APK, so the app downloads
  it once on first launch. The download runs in the background with its progress in a
  notification, so you can leave the app or turn the screen off. If the connection drops
  or Android stops it, it starts again once there's a network and resumes from where it
  stopped. It is pinned to one revision and checked against its SHA-256.
- **Offline:** after that everything runs on the phone with ONNX Runtime. Audio never
  leaves the device.
- **Shared:** [Recitation](../recitation/) uses the same model. If Accent Coach has it,
  Recitation copies it from here instead of downloading it again. Only apps signed with the
  same key as Accent Coach can read it. This needs Accent Coach 1.29 or later.

### Native reference

The native sounds for each sentence come from espeak-ng's `en-us` voice. espeak-ng uses
the same symbols the model was trained on, so the two sides are compared like with like.
The references are generated once, when the sentences are built, and bundled in
`app/src/main/assets/sentences.json`.

### Comparison

Comparison happens in `core/`:
1. A weighted alignment lines up the native sounds with the heard ones; similar sounds
   cost less to swap.
2. Each pair is graded.
3. Native variants count as correct: reduced vowels in unstressed syllables, the
   cot/caught merger, a flapped or glottal T, and a dark L.

## Limits

- **Recogniser mistakes:** the recogniser sometimes mishears, especially with
  background noise or very quiet speech. A single odd sound isn't always your accent.
  Look for sounds that keep coming up red.
- **Only individual sounds are graded:** word stress, rhythm and intonation aren't
  graded yet.
- **Single words are short:** a drill word gives the recogniser less to go on than a
  sentence does, so it may mishear one more often. A word can be tried again.
- **Speed:** the model is large. On a budget phone, expect a few seconds of analysis
  after each sentence, and the live **What I hear** line updates about once every
  couple of seconds.
- **The dictionary is old and plain:** its word list is Webster's Second (1934), so it
  has rare words but few plurals, past tenses or new words; those go to the phone's
  voice. Its pronunciations are espeak-ng's, which are now and then odd.
- **Untested on real voices:** the sentences have only been tried with synthetic voices
  on a Mac, not yet on a phone with real speech. The drills haven't been tried with any
  voice: their judging is tested on written sounds only. Getting a word's sounds from
  the phone's voice hasn't been tried on a phone at all.

## Project layout

```
accent-coach/
├── core/    Pure Kotlin/JVM: IPA tokens, CTC decoding, alignment, grading, tips,
│            drill judging, streak (unit-tested)
├── app/     Android app (Jetpack Compose)
│   ├── speech/  Recording, playback, model download and sharing, ONNX recogniser
│   ├── data/    Sentence, lesson and symbol loading from assets/; the dictionary;
│   │            the progress and words files
│   └── ui/      Setup, sentences, sounds, my words and streak
├── tools/
│   ├── sentences.txt       The practice sentences, by focus sound
│   ├── lessons.txt         The sound lessons and the words of their drills
│   ├── make_sentences.py   Builds assets/sentences.json and lessons.json with espeak-ng
│   └── make_dictionary.py  Builds assets/dictionary.txt from the system's word list
└── apk/     Ready-to-install builds
```

## Build

Needs JDK 17 and the Android SDK (platform 35).

```bash
cd apps/accent-coach
./gradlew :core:test             # unit tests for the comparison logic, the drills and the streak
./gradlew :app:testDebugUnitTest # opens the screens on the JVM; pictures go to app/build/screens
./gradlew :app:assembleRelease   # → app/build/outputs/apk/release/app-release.apk
```

The screens test can't run the speech model, so it records nothing: where a screen shows
a result, the test puts in the one the model would have led to.

Builds are for 64-bit ARM phones only (`arm64-v8a`). Minimum Android version: 8.0 (API 26).

To change the sentences or the lessons, edit `tools/sentences.txt` or `tools/lessons.txt`,
then run `python3 tools/make_sentences.py`. This needs `espeak-ng`
(`brew install espeak-ng`). The script stops if a drill word doesn't have its lesson's
sound, or if the two words of a pair differ in more than that sound.

`python3 tools/make_dictionary.py` rebuilds the dictionary from `/usr/share/dict/words`
(or a word list given as its argument). It takes about a minute.

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
- Publishing a build: see [Updates](../../README.md#updates) in the repo's README.

## Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Hearing you read the sentence. Audio stays on the phone. |
| `INTERNET` | Downloading the speech model once, and the app's own updates. |
| `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Installing those updates. |
| `POST_NOTIFICATIONS` | Showing the progress of the model's download and of an update's. Both run without it too. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK` | Keeping those downloads going in the background and with the screen off. |

# Accent Coach

An Android app for practising a native **General American** English accent. Read a
sentence aloud: the app writes down the sounds you actually made (in IPA), lines them
up with how a native speaker says the sentence, and highlights the sounds that give
away a non-native accent, with a tip for each.

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

### The speech model

- **Model:** the sounds are recognised by `wav2vec2-lv-60-espeak-cv-ft` (Facebook AI,
  Apache-2.0), using an 8-bit ONNX export by onnx-community.
- **Why this one:** it was trained on 60,000 hours of English and then on speech in
  many languages. Because of that it writes down the sound actually spoken, including
  sounds English doesn't use, such as a rolled `r`, a French `ʁ` or a pure `e`. It
  doesn't guess the word you meant, which is why a non-native sound shows up as a
  different symbol.
- **Download:** the model is 318 MB, too big to ship in the APK, so the app downloads
  it once on first launch. The download is pinned to one revision and checked against
  its SHA-256.
- **Offline:** after that everything runs on the phone with ONNX Runtime. Audio never
  leaves the device.

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
- **Speed:** the model is large. On a budget phone, expect a few seconds of analysis
  after each sentence, and the live **What I hear** line updates about once every
  couple of seconds.
- **Untested on real voices:** it has only been tried with synthetic voices on a Mac,
  not yet on a phone with real speech.

## Project layout

```
accent-coach/
├── core/    Pure Kotlin/JVM: IPA tokens, CTC decoding, alignment, grading, tips (unit-tested)
├── app/     Android app (Jetpack Compose)
│   ├── speech/  Recording, playback, model download, ONNX recogniser
│   ├── data/    Sentence and symbol loading from assets/
│   └── ui/      Setup and practice screens
├── tools/
│   ├── sentences.txt       The practice sentences, by focus sound
│   └── make_sentences.py   Builds assets/sentences.json with espeak-ng
└── apk/     Ready-to-install builds
```

## Build

Needs JDK 17 and the Android SDK (platform 35).

```bash
cd apps/accent-coach
./gradlew :core:test           # unit tests for the comparison logic
./gradlew :app:assembleRelease # → app/build/outputs/apk/release/app-release.apk
```

Builds are for 64-bit ARM phones only (`arm64-v8a`). Minimum Android version: 8.0 (API 26).

To change the sentences, edit `tools/sentences.txt`, then run
`python3 tools/make_sentences.py`. This needs `espeak-ng` (`brew install espeak-ng`).

## Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Hearing you read the sentence. Audio stays on the phone. |
| `INTERNET` | Downloading the speech model once. |

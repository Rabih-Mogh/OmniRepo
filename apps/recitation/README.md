# Recitation

An Android app for reading the Quran word by word and following a recitation of it.
Every surah can be opened and read, each word with its meaning underneath. Give the app a
YouTube playlist of a recitation and it downloads the sound only, works out when each word
is recited, and plays along: the block of the word being recited is filled in and kept at
the middle of the page, and the letter being uttered is lit in gold,
the light gliding from letter to letter.

The app is in English and Arabic, following the phone's language. In light mode it is green
on white; in dark mode, gold on black. It follows the phone's dark mode unless light or dark
is chosen in its menu. The bars at the top and bottom of every screen are
quiet: they take the page's own colour, with a thin gold rule between them and the page,
and the player sits on the bottom one. Surah names are written in
calligraphy. In the list of surahs each number is plain gold with a small gold diamond
under it, and a fine gold line parts the rows; verse numbers sit in an eight-point
star. A verse's words are laid out as blocks that share the verse's whole width between
them, touching one another and stopping a small margin short of the screen's edges; the block of the word being recited, its meaning included,
is filled with a tint and has no frame.

## How it works

The band at the top of the first screen has the menu button, the app's name beside it, and
the search button at the far end:

- **Menu** (the three lines) slides in a panel with a switch between light and dark (or
  **Auto**, which follows the phone, as the app does until one is chosen), whether each
  word's meaning shows, the verses' size, the script they are written in, and the app's
  version with **Check for updates**.
- **Search** turns the band into a search box. It finds a surah by its number or part of
  its name (Arabic or Latin letters, or what the name means), a verse by its numbers
  ("2:255", "kahf 10", "البقرة ٢٥٥"), and verses by their words: Arabic letters are
  looked for in the verses however they are vowelled or spelt, anything else in the
  translation. Words are searched in the surahs already on the phone. Tapping a result
  opens the surah, at the verse found.

Below it are four tabs, at the bottom.

**Quran** lists the 114 surahs, under a card that offers to **move to last read**: the
surah and verse where the last recitation was left. Tap one to read it, whether or not a recitation of it has
been downloaded; a play mark shows the ones that have. The bar at the bottom of a surah
says how its recitation stands (none in the playlists, not downloaded, downloading,
ready) and has the button for the next step. The words are fetched from Quran.com the
first time a surah is opened, and the app fetches the rest quietly while it is open, so
the whole Quran ends up readable without a connection.

**Saved** lists the pins and the collections (see [Pins and collections](#pins-and-collections)).

**Stats** shows what has been listened to: the time today, over the last seven days (as
bars, a day each) and in all; how many days in a row; how many of the Quran's 6,236
verses, and how many whole surahs, have been heard at least once; how many recordings are
ready and how long they run; what is saved; and the room the app's files take on the
phone. Listening is counted while a recitation plays with its surah on show, and a verse
counts as heard once the recitation has passed through it. It is kept in a file on the
phone and nowhere else.

**Playlists** is where recitations come from:

1. **Add a playlist.** Paste a YouTube or YouTube Music playlist link, or use **Share** in
   YouTube and pick Recitation. One surah per video is expected. The app reads which surah
   each video is from its title ("سورة البقرة", "Surah Al-Baqarah", "002 | …"); tap the
   surah under a recording to correct it.
2. **Get recordings ready.** For each recording the app:
   - downloads the sound (not the video);
   - fetches the surah's words, each word's meaning, and the verse translation;
   - has a speech model listen to the whole recording on the phone;
   - lines the surah's words up with what the model heard.

   This runs in the background with its progress in a notification, and the rest of the
   app stays usable meanwhile. Listening is the slow step: expect it to take roughly as
   long as the recording, once.
   - **A recording can be played before it is ready.** As soon as its sound is
     downloaded it has a play button. The listening carries on meanwhile, and words light
     up as far as it has got; the player says how far. Past that point the recitation
     plays and the words are there to read, but none is lit until the listening catches
     up. Tapping a word, or **Next verse**, jumps only to words that have been placed.
   - The recording that is open goes to the front of the queue, ahead of the one being
     worked on.
   - **Pause** stops the work where it is and keeps what was fetched; **Resume** carries
     on from there, also after the app was closed.
   - **Cancel** empties the queue. The cross on a recording takes just that one out.
   - A card at the top always says what is happening: the step and how far it is,
     paused, waiting for a connection, or what went wrong. After a connection problem the
     app tries again a few times, then shows the error, in the system's own words, on the
     recording.
3. **Read along.** Open a recording that is ready, or its surah from the Quran tab.
   - A surah opens on its title page. Two gold rules, each with a small diamond at its
     middle, run nearly from edge to edge, and between them stand its name in
     calligraphy, what the name means, how many verses it has, where it was revealed and
     its place in the order of revelation. Under them comes the basmala drawn as one
     calligraphic piece. (Al-Fatihah has
     the piece too, though the basmala is also its first verse; At-Tawbah has none.) The
     title page fills the top half, so the first verse starts at the middle of the page.
   - The app remembers how far each recitation was listened to. Opened again from the
     start, a bar offers **Move to last read** with the verse it had reached.
   - The page follows the recitation and keeps the word being recited at the middle of
     the page, so it is always in the same place for a glance. Where the page is short (a
     phone held on its side) and that would push the line just read off the top, the word
     sits lower instead, so that line stays in view above it.
   - Nothing is drawn under the camera's hole or notch: when the phone is on its side the
     page stops short of it, in the page's own colour. The page glides after
     the word rather than jumping, the block's fill and the light fade from one word to the next, and
     a jump to a far part of the surah is covered by a short dissolve. Scrolling by hand
     stops the following until the follow button is tapped: a gold star, like the verse
     numbers', with an arrow pointing up or down the page to the word being recited.
   - The recitation doesn't start by itself: it is loaded and waits for **play**.
   - While a recitation plays with its surah on show, the screen stays on: it doesn't dim
     or turn off, so the word can be glanced at without touching the phone. Paused, or
     with the surah left, the phone sleeps as usual.
   - **Verses 1 – 7** above the time line chooses which verses to recite: a first and a
     last verse. Play starts at the first and stops after the last. Left at the whole
     surah, the playlist's next recording follows as before.
   - Tap a word to jump to it. The previous-verse and next-verse buttons are either side
     of the play button. The player is a slim band: a thin time line, then the verse range,
     the three buttons and nothing else. Its buttons, and the round one that takes up
     following the recitation again, carry icons only.
   - A surah's bar has the way back at one end, the surah's name in the middle, and a
     **menu** button at the other. The menu is a panel that slides in from the side its
     button is on (the right in English, the left in Arabic), like the first screen's. It
     switches between light and dark, shows or hides the meaning under each word,
     makes the verses, meanings and translation smaller or larger (70% to 200%), and
     chooses the script the verses are written in (see [Scripts](#scripts)). All of
     these are remembered, and are in the first screen's menu too.
   - Tap a verse to see its translation. It closes by itself after long enough to read
     it (6 to 25 seconds, by its length).
   - When a recording ends, the playlist's next prepared one follows.
   - The recitation carries on when the surah, or the app, is left: with the screen off,
     in another app, or after the app is swiped away. Play and pause are in the
     notification and on the lock screen. Android colours that player from the picture
     it is given, so each surah comes with one in the theme's colours: its name under the
     arch, gold on near-black in dark mode and cream on green by day. Inside the app, a bar at the bottom of the
     lists shows what is reciting, with play or pause, a cross to stop it, and a tap to
     go back to its surah.

### Tajweed

The letters are coloured by the rule of recitation they fall under, and the light that
follows the recitation takes the rule's colour and moves as the rule sounds. **Tajweed
colours** in either menu turns the colours on or off and names each colour's family in
Arabic; **All the rules** under it opens a page that sets out every rule: its Arabic
name (with the same in Latin letters where the app is in English), what it is, and words
from the Quran that show it.

Eight colours, as in the mushafs printed with the rules in colour:

| Colour | Family | Rules | The light, while it is recited |
|---|---|---|---|
| Green | الغُنَّة, 2 counts | النون والميم المشدَّدتان, الإخفاء الحقيقي, الإخفاء الشفوي, الإقلاب, الإدغام بغُنَّة, الإدغام الشفوي | Hums: it swells and fades for as long as the sound is held |
| Deep red | المدّ اللازم, 6 counts | A long vowel before a doubled or stopped letter; the letters some surahs open with | Spreads sideways for as long as the vowel is held |
| Orange-red | المدّ الواجب, 4 or 5 counts | المدّ الواجب المتّصل | The same |
| Purple | المدّ الجائز, 2, 4 or 6 counts | المدّ الجائز المنفصل, مدّ الصِّلة الكبرى, المدّ العارض للسكون, مدّ اللِّين | The same |
| Brown | المدّ الطبيعي, 2 counts | المدّ الطبيعي where it is written with a small letter, مدّ الصِّلة الصغرى, مدّ العِوَض | The same |
| Light blue | القلقلة | The echo on a stopped ق ط ب ج د | Lets a ring go as the letter ends |
| Dark blue | التفخيم | تفخيم الراء, تفخيم لام لفظ الجلالة | A fuller, rounder light |
| Grey | الإدغام وما لا يُلفَظ | الإدغام بغير غُنَّة, إدغام المتماثلَين, المتجانسَين and المتقاربَين, اللام الشمسية, همزة الوصل, ما لا يُلفَظ | A grey touch and no pool of light |

A letter under no rule is lit in gold, as before. Letters read plainly (الإظهار), a light
r, and ordinary long vowels written as full letters keep the text's colour. Not marked:
the heavy letters that are always heavy (خ ص ض غ ط ق ظ), and the few one-off readings
(saktah, imalah, ishmam, tashil).

- **Where the rules come from.** There is no list of every place a rule applies behind
  the colours: `core/Tajweed.kt` works them out from the Uthmani script, which writes the
  rules into its signs: an n with no sukun is merged or hidden, a wavy line over a long
  vowel means it is drawn out, and which letter follows decides the rest. It follows the
  reading of Hafs from 'Asim as the Madinah mushaf writes it.
- **Checked against Quran.com.** Quran.com publishes the same text with the rules tagged
  (`text_uthmani_tajweed`). On 337 verses (surahs 1, 2:1-30, 3:1-50, 4:1-50, 18:1-50,
  19:1-30, 36:1-30, 67, 78, 97, 112 to 114; 3,382 tagged places) every tagged place of
  every kind is found here too. The differences are of naming and extent: Quran.com
  calls both the muttasil and the munfasil "obligatory"; it greys the silent و of
  "الصلوٰة" where the long a is coloured here; it tags the l of the name of Allah as a sun
  letter. Coloured here and not tagged there: the heavy r and l, مدّ العِوَض, the letter
  an n is merged into, a doubled m or n that opens a word, and rules that depend on a
  stop inside a verse.
- **Stopping and running on.** Several rules depend on whether the reciter stops after a
  word: an n at a word's end merges into the next word only if he runs on; the long vowel
  before a word's last letter is drawn out, and a final q, t, b, j or d echoes, only if he
  stops there; a joining alif is silent only when run into. With a recording, the colours
  and the light follow what its reciter did: he is taken to have stopped where he waits
  long after a word (0.7 s after a short sound, 1.8 s after a long vowel, a quarter less
  at a verse's end). Without a recording, a stop is assumed at the end of every verse.
  This is an estimate from the word timings, not from the sound itself, so a short breath
  can be missed and a very long vowel taken for a stop.
- **No letter is skipped.** A letter merged into the next one has no sound of its own, so
  the light used to jump over it. It now gets the opening part of the sound it is merged
  into: the n of "min rabbihim" is lit as the doubled r begins. Silent letters before a
  sounded one are passed over quickly rather than left out.
- **Vowel signs.** The light falls on a letter and then on its vowel sign: the sign is
  lit by itself, with a small light of its own, while the letter stays half lit under
  it. Recordings prepared from this version on time the letter and its vowel apart; for
  ones prepared earlier the time they shared is split between them.
- **The heavy r.** An r is heavy with a fatha or damma and light with a kasra. Without a
  vowel, or stopped on, it follows what comes before it: heavy after a, u, a long a or a
  long u, and after a joining alif ("irji'u"); light after an i or a y, unless a raised
  letter follows it in the word ("qirtas"). After a vowelless letter it follows the vowel
  before that one. The l of the name of Allah is heavy after a or u and light after i,
  looking back into the word before when the name opens a word.
- **How it is painted.** Letters and signs are painted over the word in colour without
  the word being laid out again (`ui/WordPaint.kt`), so nothing shifts. A letter's shape
  depends on the whole word (a kaf before a lam, a ya after a fa, and about half of all
  words have some such letter), so the word is shaped once, whole, and the very glyphs
  that came out are painted again one by one. Asking Android to draw a few characters of
  a word gets them in their plain joined form instead, which showed as a second letter on
  top of the first; that is how it was done before version 1.45. Android gives a word's
  glyphs out from version 12. On Android 8 to 11 the whole word is painted across the
  room the wanted letters take in it, which is exact for letters but can't tell a sign
  from its letter: there a sign has no colour or light of its own.
  Painting needs the shown spelling to have the Uthmani spelling's letters, which the two
  Uthmani scripts do. In the IndoPak and everyday spellings, words spelt with other
  letters show no colours, and the light falls on the letter's width as it did before.

### Pins and collections

Hold a verse, or tap its number, to open its pins and collections.

- **A pin** marks a place reached. It has a name ("Reading", "Memorising"; left empty it
  is called "Pin 1", "Pin 2", …) and sits on exactly one verse: putting it on another
  verse moves it there. Make as many pins as there are places to keep. A pinned verse
  shows the pin's name on the rule above it.
- **A collection** is a named set of verses to come back to; a verse can be in several,
  and shows a bookmark on its rule. One collection is there from the start and can't be
  deleted: **To look into**, for a verse one has a question about, to study later.
- The **Saved** tab lists every pin with the verse it is on, and every collection. Tapping
  a pin, or a verse inside a collection, opens its surah at that verse; if a recitation
  of the surah is loaded, it is moved to that verse and waits for play.
- They are kept in a file on the phone and nowhere else.

### Scripts

The words are fetched once in four spellings, and the menu chooses which is shown. The recitation is always lined up with the Uthmani spelling, whichever is shown.

| Script | What it is | Font |
|---|---|---|
| **Uthmani · Madinah mushaf** (the default) | The Uthmani spelling as the King Fahd Complex typesets it for the Madinah mushaf | KFGQPC Uthmanic Script HAFS, shipped in the app |
| **Uthmani · Amiri hand** | The same spelling in a rounder hand | Amiri Quran, shipped in the app |
| **IndoPak** | The spelling and pause marks of the mushafs of the Indian subcontinent | Fetched from Quran.com on first run; the script is offered once the font is on the phone |
| **Everyday spelling** | Modern Arabic spelling (*imla'i*), fully vowelled | Amiri Quran |

Surahs kept by an earlier version of the app have only the Uthmani spelling; they are
fetched again, quietly, the next time the app is open with a connection.

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
- **While listening:** `core/LiveAlignment.kt` places the words as the listening goes,
  a stretch at a time, each stretch starting where the one before ended. It stays 13 to 22
  seconds of recitation behind the listening, so that a word's place is settled
  before it is shown. On a clear recording this gives the same places as the whole
  recording does. On a muffled one it can drift, so it checks how clearly the sounds it
  placed were heard and, when that is low, lights nothing until the listening is done.
  Either way, once the whole recording has been heard every word is placed again against
  all of it, and that is what is kept.
- **Wrong surah:** the alignment also says how much of the surah the model heard clearly.
  When that is low (the wrong surah was chosen, or the recording stops half way), the app
  says so instead of lighting words up at random.

### A passage the reciter says again

A reciter who runs out of breath, or wants a passage heard again, goes back some words and
carries on from there. The placing above puts every sound of the surah once, in order, so
it had nowhere to put the second time through: the words stood still, or ran ahead, for as
long as the passage lasted.

Once the whole recording has been placed, `core/Repeats.kt` goes through it again a stretch
at a time, between places where a long run of sounds was each heard clearly where it was
put. Each stretch is placed afresh, this time letting the reciter go back from the end of
any word to the start of that word or an earlier one (up to 60 words). Going back costs
enough that it is only chosen where it explains a good deal, and a passage said again is
kept only if it has at least six sounds and three in five of them were heard clearly. The
timings file then holds every word once, plus each passage said again with its own
timings, and the light passes over those words a second time. Tapping a word, or a verse
button, still goes to where it was first said.

Tried on what the model heard of four real recordings: it found the passages their
reciters really did say again (78:38 and 78:40 in one, 67:28 in another), which are the
same ones the published word-by-word timings of those recordings show, and none in the
recording that has none. With a passage spliced in a second time at five places in each,
all five were found in two clear recordings and three exactly in the third (the other two
began a word late). On a muffled recording (clarity 0.74) the spliced ones were mostly
missed: there the words stand still as before.

It runs when a recording has been heard to the end, not while it is still being listened
to. Recordings prepared before version 1.45 don't have it: **Listen again**, in a
recording's menu, has the phone hear it once more (the sound is kept, so nothing is
downloaded) and places its words afresh.

### The speech model

The model is 318 MB, too big to ship in the APK.

- **Accent Coach installed:** Recitation reads [Accent Coach](../accent-coach/)'s copy of
  the model where it lies, and keeps none of its own, so the phone holds the file once.
  Accent Coach hands the open file only to apps signed with the same key. If Accent Coach
  can't lend it (it is a version from before it shared the model, it hasn't finished
  downloading the model itself, or its file doesn't match the checksum), Recitation stops
  and says which, with **Try again** and **Download it instead**. It never downloads
  318 MB on its own while Accent Coach is there.
- **A copy from before:** versions up to 1.42 copied the model out of Accent Coach, so the
  phone held it twice. The next time the app is opened it checks Accent Coach's copy
  against the checksum and, if it matches, deletes its own, freeing 318 MB. Preparing a
  recording does the same. A copy Recitation downloaded itself is kept for as long as
  Accent Coach can't lend one.
- **Accent Coach not installed:** the model is downloaded once, pinned to one revision, into
  the app's own storage.
- **Accent Coach removed later:** Recitation then has no model. The next recording to be
  prepared downloads it, as on a phone that never had Accent Coach. Recordings already
  prepared don't need the model and keep working.

Either way the file is checked against its SHA-256 before it is used. Everything after
that runs on the phone with ONNX Runtime.

### The speech engine

ONNX Runtime's native library, which runs the model, is 34 MB: nine tenths of what the
app's package used to weigh, and the same in every build. It is no longer inside the
package, which is about 4 MB instead of 38 MB, so every update is that much smaller. The
engine is put on the phone once, the first time a recording is prepared:

- **Accent Coach installed:** the library is copied out of Accent Coach's installed
  package, which carries the very same file. Nothing is downloaded.
- **Otherwise:** it is downloaded from this repo ([engine/](engine/)), compressed (13 MB).

The file must match its fixed SHA-256 before it is kept, and is kept read-only in the
app's private storage. The small library that joins the engine to the app's Java side
(120 KB) stays inside the package: on Android, ONNX Runtime will load that one only from
the package. The app loads the engine first, by its full path, and the small library then
finds it already loaded. Versions 1.43 to 1.46 kept both files outside the package and
could not start the engine on a phone ("The speech engine couldn't be started"), so no
recording could be prepared with them. Reading and searching the Quran, and playing recordings that are
already prepared, don't need the engine.

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
  Android itself and have only been compiled. That includes starting the engine from the
  app's storage rather than from inside its package, and reading the model straight out of
  Accent Coach's file. If the engine won't start on a phone, the recording being prepared
  says so, with Android's own wording.
- **One surah per video.** A video with several surahs, part of a surah, or verses
  repeated (as in prayer) won't line up; the app will usually say it doesn't match.
- **Old or echoing recordings** are heard less clearly and line up less tightly.
- **Speed on a phone is unknown.** On the Mac the model runs 25 times faster than the
  recording plays. A phone is much slower; "about as long as the recording" is a guess.
  If a phone listens slower than the recitation plays, playing from the start will
  overtake the listening, and the words stop lighting up until it catches up.
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
| Quran text (Uthmani, IndoPak and everyday spellings), word meanings, verse translation (Saheeh International) | [Quran.com](https://quran.com) API, fetched by the app and kept on the phone; not stored in this repo | Their own terms |
| Speech model | [wav2vec2-lv-60-espeak-cv-ft](https://huggingface.co/facebook/wav2vec2-lv-60-espeak-cv-ft), ONNX export by onnx-community | Apache-2.0 |
| Speech engine | [ONNX Runtime](https://github.com/microsoft/onnxruntime) 1.31.1: its Java classes in the app, its native library in [engine/](engine/) and fetched by the app | MIT ([text](licenses/ONNXRuntime-MIT.txt)) |
| Reading YouTube | [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) | GPL-3.0 |
| Font for the Madinah mushaf script | KFGQPC Uthmanic Script HAFS, King Fahd Glorious Quran Printing Complex | Free to use, copy and distribute; may not be sold or modified ([text](licenses/UthmanicHafs-KFGQPC.txt)) |
| Font for the Amiri and everyday scripts, and the basmala | [Amiri Quran](https://github.com/aliftype/amiri) | SIL Open Font License ([text](licenses/AmiriQuran-OFL.txt)) |
| Font for the IndoPak script | "AlQuran IndoPak by QuranWBW", fetched by the app from Quran.com on first run and kept on the phone; not stored in this repo. Used only if it is byte for byte the file the app was written against | None stated: their own terms |
| Calligraphy of the surahs' names | Quran.com's surah-name font, fetched by the app on first run and kept on the phone; not stored in this repo. It is used only if it is byte for byte the file the app was written against | None stated: their own terms |
| Font for the surahs' names until that is fetched | [Aref Ruqaa](https://github.com/alif-type/aref-ruqaa) | SIL Open Font License ([text](licenses/ArefRuqaa-OFL.txt)) |

Because it includes NewPipe Extractor, this app's own code is under the
[GPL-3.0](LICENSE).

## Project layout

```
recitation/
├── core/    Pure Kotlin/JVM, unit-tested: words to sounds, listening in pieces, alignment,
│            word timings, surah names and reading them from titles
├── yt/      Pure Kotlin/JVM: playlists and sound from YouTube (NewPipe Extractor)
├── app/     Android app (Jetpack Compose)
│   ├── data/     The playlists added, the verses from Quran.com, pins and collections, fetched fonts
│   ├── speech/   Where the model and the engine come from, sound decoding, ONNX model
│   ├── prepare/  The background work that gets a recording ready
│   ├── player/   Playback service
│   └── ui/       The surah list, playlists, recordings and reading screens
├── engine/  ONNX Runtime's native library, compressed, for the app to fetch once
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
./gradlew :app:testDebugUnitTest  # draws each screen, in both languages and themes, to app/build/screens; also the update's background service
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
at the bottom of the menu on the first screen.

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
| `INTERNET` | Reading playlists, downloading sound, the verses, the speech model, and the app's own updates. |
| `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Installing those updates. |
| `POST_NOTIFICATIONS` | Showing progress while recordings are prepared and while an update downloads. The work runs without it too. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK` | Keeping that work going in the background. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Reciting with the screen off. |

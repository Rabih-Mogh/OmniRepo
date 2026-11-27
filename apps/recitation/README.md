# Recitation

An Android app for reading the Quran word by word and following a recitation of it.
Every surah can be opened and read, each word with its meaning underneath. Give the app a
YouTube playlist of a recitation and it downloads the sound only, works out when each word
is recited, and plays along: the block of the word being recited is filled and
is kept at the middle of the page, and the letter being uttered is lit in amber,
the light gliding from letter to letter.

The app is in English and Arabic, following the phone's language. It is made for readers
whose first language is Arabic and whose second is English, so wherever the two stand
together the Arabic comes first, each the way its page runs: in English a surah's row
reads number, Arabic name, then its Latin spelling and meaning at the far side, and a
surah named in a line of text is "الفاتحة · Al-Fatihah"; in Arabic the Arabic name stands
alone or first. In the reader, in English, the Latin spelling under the Arabic name has
the surah's number before it ("1 · Al-Fatihah").

The look is plain, made to be read at a glance. In light mode it is black on white with
one colour, a deep teal; in dark mode, white on black with a pale mint. It follows the
phone's dark mode unless light or dark is chosen in its menu. Headings are bold, corners
are round and buttons have round ends. The bars at the top and bottom of every screen
are a colour of their own, teal by day and a dark teal by night, so the page stands apart
between them; the player sits on the bottom one. The phone's own status icons are drawn
light on the top bar. Surah names are written in calligraphy. In the list of surahs each
number is large and plain, the rows are tall, and a faint line with the same margin at
either end parts them. A surah that has a recitation downloaded (at least one recording
whose sound is on the phone) is told apart by its number: in teal, with small bars of
sound under it, where the others are grey. Until one of its recordings has been listened
to the end, another mark stands in place of the bars: the picture of the work in its ring
while the surah is being worked on, an hourglass while it waits its turn, and the first
bars followed by dots when the listening was left off part-way. The tab showing, at the
bottom of the first screen, has a short line at the top edge of the bar, and is bright where the others are faint.
Verse numbers sit in an eight-point
star. A verse's words are laid out as blocks that share the verse's whole width between
them, touching one another and stopping a small margin short of the screen's edges; the
block of the word being recited, its meaning included, is filled in the app's colour,
with no line round it; on a verse's first and last rows the fill reaches the rules above
and below the verse.

## How it works

The band at the top of the first screen has the menu button, the app's name beside it
("The Recitation" in English, as the Arabic name has its article), and the search button
at the far end:

- **Menu** (the three lines) opens a panel beside the page: the whole page slides aside
  by the panel's width and the panel comes in behind it, so nothing lies over anything.
  A cross at its head closes it, as do a tap on what is left of the page and Back. It
  holds what belongs to the whole app: a switch between light and dark (or **Auto**,
  which follows the phone, as the app does until one is chosen), what every surah's
  **page** is made of (its script, the language of the words' meanings, the translation
  and the exegesis: see [What the page is made of](#what-the-page-is-made-of-script-word-meanings-translation-exegesis)),
  and the app's version with **Check for updates**. What is turned to while reading
  (meanings, text size, the tajweed colours) is in a surah's own menu, which has the
  page's four choices too. Under the version, **Copy a report** copies what the app
  knows about how it is running on this phone: the phone's model and Android version,
  the room left on it, the speech model and engine, how many recordings are ready, queued
  or failed and why, the state of the background work, and the last lines of a small
  journal the background work keeps (when it started and stopped, what went wrong, how
  fast each ten minutes of recitation was listened to). It is for pasting to whoever is
  looking into a problem, and goes nowhere unless it is pasted somewhere.
- **Search** turns the band into a search box. It finds a surah by its number or part of
  its name (Arabic or Latin letters, or what the name means), a verse by its numbers
  ("2:255", "kahf 10", "البقرة ٢٥٥"), and verses by their words: Arabic letters are
  looked for in the verses however they are vowelled or spelt, anything else in the
  translation. Words are searched in the surahs already on the phone. Tapping a result
  opens the surah, at the verse found.

Below it are five tabs, at the bottom.

**Quran** lists the 114 surahs, under a card that offers to **move to last read**: the
surah and verse where the last recitation was left. Tap one to read it, whether or not a recitation of it has
been downloaded; a play mark shows the ones that have. The bar at the bottom of a surah
says how its recitation stands (none in the playlists, not downloaded, downloading,
ready) and has the button for the next step. The words are fetched from Quran.com the
first time a surah is opened, and the app fetches the rest quietly while it is open, so
the whole Quran ends up readable without a connection.

**Saved** lists the pins and the collections (see [Pins and collections](#pins-and-collections)).

**Practise** is where the reader's own voice comes in: **Recite and be corrected** (see
[Reciting to the phone](#reciting-to-the-phone)) and **Train your voice** (see
[Training the voice](#training-the-voice)).

**The days listened on in a row are a plant.** A day counts once a minute has been
listened to on it. The plant is a seed in the soil before the first day and a sprout on
it; it grows a pair of leaves at three days and another at a week, flowers at a
fortnight and bears fruit at a month. On a day not yet listened on it is grey and its
leaves hang until that day's minute is in; a day missed, and it is a seed again. It
stands small in the first screen's top bar with the number of days, and a tap opens the
whole of it: what today still asks for, the longest run so far, and the last seven
days, each a dot that is filled if it was listened on. The same card heads the Stats tab.

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
   each video is from its title ("سورة البقرة", "Surah Al-Baqarah", "002 | …"); **Choose
   surah** in a recording's menu corrects it.
   - A playlist shows as a card: a ring that fills with the share of its recordings that
     are ready (or holds the picture of the work while one of them is being worked on),
     its title and channel, and in the app's own marks how many are ready, how many
     wait their turn and how many couldn't be got ready.
   - Its recordings are laid out like the surahs of the Quran tab: the surah's number
     with the mark under it for where its recitation stands (the bars of sound, the
     work in its ring, an hourglass, or the picture of what it waits for), the surah's
     name in calligraphy, and under it the recording's own title and how long it is.
     Only what went wrong is put in words.
2. **Get recordings ready.** For each recording the app:
   - downloads the sound (not the video);
   - fetches the surah's words, each word's meaning, and the verse translation;
   - has a speech model listen to the whole recording on the phone;
   - lines the surah's words up with what the model heard.

   This runs in the background with its progress in a notification, and the rest of the
   app stays usable meanwhile. Listening is the heavy step: while it goes on it keeps the
   phone's processor busy.
   - **The listening goes on only while the app is on the screen.** Closed, or left for
     more than ten seconds, it stops at the end of the ten seconds of sound it is at and
     keeps what it has heard; the sounds still to come go on downloading, which costs the
     phone next to nothing, and then the work ends. What is left is listened to when the
     app is opened again, from where it stopped. Left unopened, it is also listened to,
     unasked, **while the phone is charging and nobody is using it** (its screen off for a
     while): Android starts that run by itself and stops it the moment the phone is
     unplugged or picked up. So a long playlist is ready by morning without the phone
     ever being slowed while it is in the hand.
   - **It gives way to everything else.** The engine runs at a low priority, one step
     above Android's own for background work, so the screen, the playing and any other
     app get the processor first; it uses at most half the phone's cores, and fewer
     unless more are clearly faster; and it holds still while the reader recites to the
     phone, so that the checking of their recitation has the engine to itself.
   - **A recording can be played before it is ready.** As soon as its sound is
     downloaded it has a play button. The listening carries on meanwhile, and words light
     up as far as it has got; the player says how far. Past that point the recitation
     plays and the words are there to read, but none is lit until the listening catches
     up. Tapping a word, or **Next verse**, jumps only to words that have been placed.
   - **The sounds are downloaded beside the listening, not in its way.** Downloading
     takes the connection and hardly any of the phone's strength; listening takes all of
     that strength and no connection. So the sounds are fetched one after another, with
     a short while between them so YouTube isn't asked for too much at once, and
     meanwhile the recordings already on the phone are listened to, one at a time. Every
     recording of a playlist can so be played long before the last has been listened to.
     The status card shows both: the listening, and under it the surah whose sound is
     being downloaded; each has its own ring on its row in the lists.
   - The recording that is open goes first in both: its sound is fetched ahead of the
     one being downloaded, and it is listened to ahead of the one being listened to.
   - **Pause** stops the work where it is and keeps what was fetched; **Resume** carries
     on from there, also after the app was closed.
   - **Cancel** empties the queue. The cross on a recording takes just that one out.
   - **The speech model is never left idle while the app is open.** Every recording whose
     sound is on the phone and that hasn't been listened to the end goes into the queue
     by itself, when the app opens and whenever the work runs out. That needs no
     connection. It doesn't happen while the work is paused, nor for a recording taken
     out of the queue with its cross or by **Cancel**, nor for one that failed: those
     wait to be asked for.
   - What the app is at is shown in pictures, not told in words. Each kind of work has
     its own icon (an arrow for the sound being downloaded, a book for the verses, an ear
     for the listening, bars over words for the matching; and, only while the app is
     first being set up, a chip for the speech model, a cog for the engine and sliders
     for the trial of the fastest way to run it), and so has
     each way of waiting (an hourglass for a turn, a pause mark, a struck-through cloud
     for no connection, a struck-through circle for YouTube refusing). A ring round the
     icon fills as the work goes; work that can't say how far it is has a ring that turns.
     Only what went wrong is put in words.
   - It is in sight on every screen for as long as anything is being done or waited for:
     the icon in its ring, with the number of the surah it is for, stands at the end of
     the top bar, in the lists and in a surah. In a surah it shows only what is being
     done to other surahs: the work on the surah's own recording shows in its player,
     and nothing about other surahs does. Tapping it opens the whole of it, with the
     words for the picture and the buttons to pause or call it off.
   - A card at the top of the playlists, and of a playlist's recordings, shows the same larger: the icon, the surah's number
     and name, the row of a recording's four steps with the one being done marked (the three steps of setting the app up, only while that is being done), the listening's
     speed as a figure, and the pause and cancel buttons. In the list of surahs the surah
     being worked on has the icon under its number, and in a surah the player's line has
     a small mark standing on it at the place being listened to. After a connection problem, or
     a server that answers with an error or with half an answer, the app tries again a
     few times, then shows the error, in the system's own words, on the recording.
   - When YouTube refuses the phone for a while (it was asked too much, or wants proof
     that a person is asking), no recording is marked as failed. The card says so, the
     recordings whose sound is already on the phone are still listened to, and the
     downloads are tried again a quarter of an hour later.
   - A phone whose storage is full is told apart from a bad connection: the recording
     says the storage is full, at once, and before a download that wouldn't fit is begun.
   - What is kept on the phone (the playlists, the pins and collections, the listening
     figures, each recording's word timings, each surah's words) is written whole or not
     at all, so closing the app or losing power in the middle of a save leaves the last
     good copy. A half-finished download is carried on only with more of the same sound:
     if YouTube offers another for the video, it starts over.
   - Changing a recording's surah, forgetting it or removing its playlist while it is
     being worked on stops that work; a new surah's words are then placed from what was
     already heard.
   - Only fetching needs a connection. The listening runs on the phone, so with no
     connection the recordings that have their sound, their surah's words, the speech
     model and its engine on the phone are still listened to; the others wait for the
     connection to come back and are taken up by themselves when it does.
3. **Read along.** Open a recording that is ready, or its surah from the Quran tab.
   - A surah opens on its title page. Two hairlines run nearly from edge to edge, and
     between them stand its name in
     calligraphy, what the name means, how many verses it has, where it was revealed and
     its place in the order of revelation. Under them comes the basmala drawn as one
     calligraphic piece. (Not for Al-Fatihah, whose first verse is the basmala, nor for
     At-Tawbah, which has none.) The
     title page fills the top half, so the first verse starts at the middle of the page.
   - The surah's name in the top bar is also the way to another surah: a tap opens the
     list of all of them at the one that is open. Opening another surah doesn't stop a
     recitation that is playing: it carries on, and is given up for the new surah's own
     recording only when play is pressed there.
   - Where a verse's words run out part-way (the listening hasn't got further yet), the
     page never goes back up to the verse's start: the verse is lit whole and the page
     stays, or goes on down to show the rest of it.
   - The top bar gets out of the way. Once the page goes on down the surah, whether a
     finger scrolls it or the recitation carries it, the bar slides up out of sight and
     the verses have its room; the strip behind the phone's status bar stays. It slides
     back when the page is pulled back a little, when the recitation is paused, and at
     the surah's start. While the page follows the recitation, a passage said again
     (which takes the page back a line or two) doesn't bring it back.
   - The app remembers how far each recitation was listened to. Opened again from the
     start, a bar offers **Move to last read** with the verse it had reached.
   - The page follows the recitation and keeps the word being recited at the middle of
     the page, so it is always in the same place for a glance. Where the page is short (a
     phone held on its side) and that would push the line just read off the top, the word
     sits lower instead, so that line stays in view above it.
   - Nothing is drawn under the camera's hole or notch: when the phone is on its side the
     page stops short of it, and the strip beside it takes the bars' colour. The page glides after
     the word rather than jumping, the block's fill and the light fade from one word to the next, and
     a jump to a far part of the surah is made out of sight: the page fades, is moved, and
     comes back drifting the last of the way from the side it travelled. Scrolling by hand
     stops the following until the follow button is tapped: a disc in the app's colour
     with lines of text on it and, beside them, an arrow up or down for the way along the
     page to the word being recited.
   - The recitation doesn't start by itself: it is loaded and waits for **play**.
   - While a recitation plays with its surah on show, the screen stays on: it doesn't dim
     or turn off, so the word can be glanced at without touching the phone. Paused, or
     with the surah left, the phone sleeps as usual.
   - **Which verses to recite**, in the surah's menu, chooses a first and a
     last verse. When that isn't the whole surah, the verses chosen are also written
     small under the time line, where a tap opens the chooser again. Play starts at the first and stops after the last. The player itself is
     told where to stop, so it does with the screen off or the app left, too. Left at the
     whole surah, the playlist's next recording follows as before.
   - Turning the phone on its side, or setting it in a car's cradle, keeps everything as
     it was: the place in the page, the verses chosen, a recitation waiting for a verse
     to be heard.
   - Tap a word to jump to it. The previous-verse and next-verse buttons are either side
     of the play button. The player is one slim row: the three buttons, then a thin time
     line with the time gone and the whole time under it, and between those two, in
     pictures, what is being done to the recording. Its buttons, and the round one that
     takes up following the recitation again, carry icons only.
   - A surah's bar has the way back at one end, the surah's name in the middle, and a
     **menu** button at the other. The menu is a panel that comes in from the side its
     button is on (the right in English, the left in Arabic), pushing the page aside as
     the first screen's does, with a cross to close it. It holds what belongs to reading
     a surah: which verses to recite, whether the meaning shows under each word, the
     size of the verses, meanings and translation (70% to 200%), light or dark right
     under that (Auto, Light or Dark; it is the whole app's, and in the first screen's
     menu too), and the tajweed colours with their legend. All of these are remembered.
     The script the verses are written in is chosen once for every surah, in the first
     screen's menu (see [Scripts](#scripts)).
   - Tap a verse to see its translation. It closes by itself after long enough to read
     it (6 to 25 seconds, by its length).
   - When a recording ends, the playlist's next prepared one follows.
   - The recitation carries on when the surah, or the app, is left: with the screen off,
     in another app, or after the app is swiped away. Play and pause are in the
     notification and on the lock screen. Next and previous there, on a headset and on a
     car's wheel go by verse, not by recording: next to the following verse (past the
     last, to the next recording), previous to the start of the verse being recited, or
     from there to the verse before. Tapping the notification opens the surah being
     recited. Play pressed on a headset or in a car while the app is closed takes up the
     recitation listened to last, where it was left. Android colours that player from the picture
     it is given, so each surah comes with one in the theme's colours: its name under the
     arch, on the bars' teal by day and their dark teal in dark mode. Inside the app, a bar at the bottom of the
     lists does what the player in the surah does: a thin line along its top for how
     far the recitation has got, previous verse, play or pause, next verse, the surah's
     number and name, the verse being recited and the time gone of the whole, a cross
     to stop it, and a tap to go back to its surah.

### Tajweed

The letters are coloured by the rule of recitation they fall under, and the light that
follows the recitation takes the rule's colour and moves as the rule sounds. **Tajweed
colours** in a surah's menu turns the colours on or off and names each colour's family in
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

A letter under no rule is lit in amber. Letters read plainly (الإظهار), a light
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

### Reciting to the phone

**On the page of a surah**, **Recite to the phone** in the surah's menu opens the
microphone. Nothing more is asked: the phone finds by itself which verse is being recited,
wherever in the surah the reader begins, and follows them from verse to verse to the
surah's end or until they stop. A recording that is playing is paused first. In the
player's place there is a bar for it: the microphone in its turning ring, the number of
the verse the reader is heard to be at, how many of the words gone over so far were
sound, and the button that ends it.

- **The page follows the reader**: the verse they are at is brought into view and the
  word they have reached is lit on its block, as when a recording plays.
- **Each verse is gone over as the reader goes on to the next**, and what there is to go
  over is marked on it right away; the last one when they stop.
- **The reader may go back or skip ahead**: they are found again, and a verse recited a
  second time is gone over afresh. A verse taken up in its middle is followed but not
  gone over, as its beginning wasn't heard. The basmala said before a surah's first
  verse is followed and not taken for that verse's words.

How it is done is in `core/Follower.kt`. The sound is heard a stretch at a time, each
ending where the reader falls silent, so no word is cut in two and a verse of any length
can be recited: the model takes half a minute at once, a stretch is never that long.
What was heard is kept in one run. Its last forty sounds (four or five words) say where
the reader is: they are looked for near where the reader was first, where the words of a
refrain are the ones meant, and then anywhere in the surah. When the reader is heard to
be five sounds into the next verse, the one before is cut out of the run where its
sounds end, at the silence if there is one within a few sounds, and gone over the way a
verse recited by itself is.

**A word that wasn't said is not judged.** It is told as not heard and nothing else:
no madd, ghunnah or qalqalah in it is called short or missing, since those are ways of
saying a word wrongly and it wasn't said. Which words weren't said is settled before
anything is judged (`Corrections.unsaid`): a word nearly all of whose sounds are missing,
or one that what was heard fits better without, by two thirds of the word's sounds or
more. The second is there because, lined up sound by sound, a word passed over borrows a
vowel or a ل from its neighbours and then looks said, and said badly. The words that
were said are then lined up again without the others, so none of them is corrected for
sounds that were the unsaid word's, and nothing that rests on an unsaid word (a nun
merged into it) is judged either. A reader who recites half a verse and stops is held to
how the word they stopped on sounds at a stop, if that is nearer to what was heard. This
departs from the rule engine the corrections follow, which judges such a word's rules
before it finds the word missing. Checked on the 35 expert verses of the tests, each
with every word dropped in turn and cut short after every word (383 cases: before, 17
had a rule judged in a word not said; now none), and on the 159 learner clips, where one
word in 1,272 changes, from "said badly" to "not heard".

What there is to go over is marked right there on the page:

- **A word with something to go over has its block filled**: red for a mistake in the
  wording itself (لحن جلي: another letter, another vowel, a sound left out or added, a
  word passed over), and for a point of tajweed **the colour its rule has on the page and
  in the legend of the colours**: green for a ghunnah cut short, a hidden nun said clearly
  or an iqlab or idgham missed, the madd's own colour for a madd held too short, too long
  or unevenly, light blue for a qalqalah with no echo. The letters the rule is on are
  underlined in that colour, so the mark, the coloured letters and the legend read as one.
  This holds with the colours of the page switched off, too.
- **Under the verse, what to go over in the word picked** (the first marked one, until
  another is tapped): what was heard and how to put it right; for a letter, where in the
  mouth it is made, in the words of the classical books of tajweed. A point of tajweed is
  headed by its family of rules with the dot of its colour; a tap on that opens the page
  that sets the rules out.
- **Beside the verse's number, how it went**: how many of its words were sound. A tap on
  that takes the corrections off the page. They stay otherwise until the verse is recited
  again or the surah is left.

The same can be done a verse at a time from the **Practise** tab, on a page of its own
with the verse in large letters: there the words still to come are faint, and at a
surah's first verse the button for the verse before is plainly faint, and at its last
the button for the verse after.

**A word marked there can be played back as the reader said it.** Under what there is to
go over in a word stands **Play back how you said it**: the word as the microphone took
it, with a moment either side, so the reader hears what the phone heard. What was
recited is kept in memory only, until the next verse is recited or the page is left, and
each word is found in it by when its first sound and the next word's began
(`WordRecited.heardFrom`). A word that wasn't said has nothing to play. On the page of a
surah, where the reader is followed through many verses, there is no such button yet.

**A verse recited there, or in an exercise, is gone over without a second press**
(`core/Ending.kt`). The microphone closes by itself once the reader falls silent: after
one second of quiet if the verse's last word has been reached, and after two and a half
otherwise, which leaves room for a breath and for a stop made on purpose in the middle of
a verse. And it closes by itself after eight seconds in which nothing that could be a
voice has come (a microphone that gives nothing, a room with no one reciting): what was
taken is gone over, and the page says nothing was heard. Quiet is judged by loudness
against the take itself: a moment is quiet when it is under three tenths of the take's
loud level, and a voice has been heard once the loud level stands about ten decibels
clear of the quiet one. In a room so noisy that the voice doesn't stand clear, the
microphone stays open, as long as the model makes out the verse's words, and the reader
ends it with the button as before; the button ends it sooner at any time. Reciting a
whole surah on its page is not ended this way: there a silence is a breath between
verses. Checked on sounds made up for the tests, not on a voice in a room.

How it listens:

- **The model.** The model that follows a reciter's recording was trained on ordinary
  speech in many languages, and cannot judge the letters learners get wrong: measured on
  expert reciters, it writes something else for ث ذ ظ ض ص ط ع غ even when they are said
  perfectly. So checking uses another, made for this: **HisnLabs Tajweed v2**, a small
  model (23 MB) that writes a recitation in the Quran phonetic script, a long vowel once
  for every count it is held, with signs of their own for a hidden nun, a nun turned to a
  mim and the echo of a qalqalah. It is fetched once from [models/tajweed-v2](models/tajweed-v2)
  by the same background work that fetches recordings, as soon as a surah or the Practise
  tab is opened (a microphone tapped before it is here shows it on its way, in a filling ring): it carries on with the app closed, waits for a connection,
  takes a download up where it was cut off, and shows in the top bar as a microphone in
  a filling ring. It runs on the phone, on the engine the app already has. Nothing
  recited leaves the phone.
- **What the verse should sound like** is worked out beforehand for every word of the
  Quran with the phonetizer the model was trained with (quran-transcript), in the four
  forms a word can take: inside a breath, first in one, last in one (as at a stop), or a
  breath of its own. That is `app/src/main/assets/tajweed_reference.bin`, written by
  `tools/make_tajweed_reference.py`. Any stretch of a verse stitched from these forms is
  exactly what the phonetizer gives for it (4,432 random stretches checked).
- **Comparing.** Where the reciter stopped and began again is found first, so a word
  stopped on is judged in the form it takes at a stop, and words gone back over are not
  taken for mistakes. A stop that changes three sounds or more shows in what was heard;
  many change fewer (a tanween read as an alif, the echo of a qalqalah, a last vowel left
  off), and those are taken for stops only where the reciter fell silent
  (`core/…/Silences.kt`: the sound stays under three tenths of its loud level for 0.3 s,
  which holds in a room that echoes too). Without a silence they are not: a nun left
  unmerged before the next word is a mistake. Then the two scripts are lined up sound by sound and each difference
  is named. Lengths are allowed one count either way; a long madd is also measured by the
  clock against the reciter's own natural madd; and a remark about a sound the model was
  not sure of is left out. This is the method of the rule engine published with the model
  (`imam.py`), written again in Kotlin (`core/…/Recited.kt`, `Corrections.kt`). On 291
  cases (54 real verses by four expert reciters, and the same with mistakes written into
  what was heard) it gives the rule engine's own verdict on every one.

What to expect of it, and what not:

- It can be wrong, and says so on its screen. Its makers measured a remark on 4 to 9 in
  every 100 words that were recited correctly. Measured here on 322 verses by five expert
  reciters (2,175 words, all correct): 41 words remarked on before stops were told by
  the silences, 29 with them (1.3%); most of the rest are the recording cut off at a
  verse's end, a few the model mishearing. On 159 clips of learners marked by human
  experts (obadx/qdat_bench, the end of 5:109: 1,272 words, 203 of them wrong) it marks
  65 in 100 of the wrong words, and 10 in 100 of the words the experts marked right. It
  does not hear a nun held but not hidden. It is an aid for going over a recitation, not
  a teacher.
- A model forty times the size (Quran Muaalem, 876 MB) was measured beside it: it finds
  no more of the mistakes (63 in 100), and marks fewer right words wrong on the learners
  (4 in 100) but as many on the experts. It was left out.
- It hears a verse at a time, up to half a minute. A verse longer than that can't be
  recited whole yet.
- It is for the reading of Hafs from ʿAsim. It does not judge the heavy and light of ر
  and of the ل of the name of Allah, nor stopping and starting in the wrong places.
- No tool does catch every mistake: the best published systems still pass about three in
  ten mispronunciations, and the best-known recitation app checks words and vowels but
  not tajweed.

### Tajweed lessons and exercises

**The app teaches before it tests.** The Practise tab opens on the lessons: the ten rules
in the order a learner meets them, each by its own name with the dot of its colour, and
a tick on the ones learned. A lesson (`ui/Lessons.kt`) shows the rule in pictures; plays
words from the reader's own recordings that have it, at a tap, so it is heard as a
reciter does it; says when the rule comes (the letters that bring it about), how it is
said (where the tongue and lips are, what goes through the nose, how many counts), and
the slip most often made with it; and shows words of the Quran that have it, the letters
it falls on in its colour. Only then comes **Practise it**, which opens the rule's
exercise. A pair of letters has a lesson too (the two mouths side by side, where each
letter is made, and how they differ), and so has stopping on a word.

**What the count of rules learned counts.** "2 of 10 learned" counts exercises that went
well, not lessons that were opened. A rule's exercise is eight verses that have the rule
(fewer where the short surahs have fewer), recited one after another and judged for that
rule alone. The rule is ticked once three goes in four are sound in one sitting: six of
eight (`Drills.needed`). A go whose rule wasn't heard, and a verse passed over, are not
sound. A verse may be recited again and counts as it last went. The tick comes the moment
the sixth is sound and is kept from then on; until then the list shows the best the
exercise has gone ("5/8"), and the exercise shows its goes as a row of dots with how many
are sound of the number needed. The rule is said in a line under the count.

**The pictures** (`ui/RulePictures.kt`) are drawn by the app, not stored:

- **The mouth, from the side, for every rule**, and beside each mouth what every part of
  it does while the rule is said, a line to a part: the tip of the tongue, its back, the
  lips, the jaw, the nose, and for how long. A doubled nun and a doubled mim (the tip on
  the gum, or the lips shut, the soft palate down, the way the sound takes out of the
  nose); the hidden nun beside a clear one to compare (the tip just off the gum); the
  nun turned to a mim; the nun merged, once into a ي with the sound in the nose and once
  into a ل with all of it out of the mouth; the five places the letters of qalqalah shut
  the sound off; and for every madd the three mouths it is held in, an alif (the jaw
  dropped), a و (the lips rounded and forward) and a ي (the middle of the tongue raised).
  Two letters to tell apart have their two mouths side by side, with the place each is
  made and the back of the tongue raised for the heavy one.
- **An example laid out in time**, for every rule: what is said, each sound as wide as it
  is long, read from the right; under it what of that goes through the nose; under the
  sound the rule is about, its counts; and over it, where the mushaf writes something
  else than is said (a nun turned or merged), what it writes. A madd of four or five, or
  of two, four or six, has the counts it may also be held for left open. Its button
  plays the time through, not a recitation: a line crosses at a measured pace (0.45 s a
  count) with a soft tick as each count of the rule's sound begins, so the counts are
  seen and heard for how long they are and can be recited along with. The echo of a
  qalqalah, which has no counts, ticks once where the letter springs open. The tick is
  made by the app (a 45 ms note), at the loudness of the recitations.
- A lesson on a madd has **Count it with your voice**, which opens the counting of a madd
  set to that madd's counts; Back returns to the lesson.

These are schematic: they show where and how long, drawn from what the lessons say, and
have not been looked over by a teacher of tajweed any more than the lessons' words have.

**In Arabic or in English, whichever the phone is in** (`ui/Teaching.kt`). The lessons,
the exercises and every correction can be turned to Arabic on a phone set to English,
and to English on one set to Arabic: a switch in the top bar of a lesson, of an exercise
and of reciting a verse, on the lessons' card on the Practise tab (which turns with it,
as does the card of weak spots next to it: their words, and the rules by their Arabic
names), and on the card of corrections on the page of a surah. It is one choice for all of them, kept from then on;
the rest of the app stays in the phone's language. Inside what is taught the app's own
strings of that language are used and the page is laid out from the side that language
reads from.

**It is taught in the voice of the reciter on the phone.** Wherever a recording that has
been listened to has the words, they are played from it, a stretch at a time, apart from
the player the recitations go through (which is paused meanwhile):

- A lesson has up to five stretches of the reader's recordings that show the rule (the
  word it is in, and the one after where the rule lies between the two), from more than
  one surah, the short surahs first. Each is played at a tap, and nothing is played until
  one is tapped.
- **They are cut by the app from whatever recordings are on the phone, so they are taken
  only where that reciter really does the thing** (`core/Cases.kt`). A nun at the end of
  a word is hidden, turned or merged only if the reciter runs on into the next word; a
  long vowel before a word's last letter is drawn out, and a ق ط ب ج د at a word's end
  bounces, only if the reciter stops there. So the examples go by where this reciter
  stopped, as the page of a surah does for its colours (`Pointer.refine`: the wait after
  a word's last sound), not by where a verse ends on paper: a reciter who takes a breath
  between two words gives no example of the rule that would have joined them, and one
  who runs two verses together gives no madd of a stop at the first one's end. Where the
  reciter stopped is an estimate from the timing, so now and then an example can still be
  taken where the rule wasn't done; it was right on the made-up cases of the tests and
  has not been checked against recordings by ear.
- **A tap on one also says, under it, what makes those very words an example**: which
  letter the rule falls on, the letter after it that brings the rule about, and what
  that asks ("«مِن»: its nun comes before ق, one of the fifteen letters of ikhfa … ق is
  a heavy letter, so the nasal sound before it is heavy too"), or that the reciter stops
  on the word and what that does to it.
- In an exercise, **Hear the reciter** plays the verse before it is recited, and after a
  verse that wasn't sound for its rule, **Hear how the reciter says it** plays the words
  the rule is in. The verses of an exercise are chosen from surahs that have a recording
  first, so there is a voice for as many of them as can be.
- Over the counting of a madd, each kind of madd in the table can be heard held by the reciter.

Where no recording has the words (none has been listened to yet, or not that surah), the
lesson and the exercise are as they are without the voice.

The teaching goes on inside the exercises. Under the verse stands what to do, in a line,
and the rule's example in time. A verse that wasn't sound for its rule has how the rule
is said under its verdict, there and then. Spotting the rule names the rule at
work in the word once it is answered and says what that rule is. Counting a madd has the
length of every madd set out above the counting.

What is taught is the ordinary teaching for the reading of Hafs from Asim, as the primers
set it out (Tuhfat al-Atfal for the nun, the mim and the madds; al-Muqaddimah
al-Jazariyyah for where the letters are made). The lessons' wording is the app's own, in
English and in Arabic, and has not been read over by a teacher of tajweed: it should be
before it is relied on.

The Practise tab has seven exercises on top of reciting a verse. Four of them are verses
to recite one after another, each judged for one thing alone, and the exercise ends with
how many of its verses were sound for it. The verses come from Al-Fatihah and the last
part of the Quran (surahs 78 to 114), three to ten words long, chosen afresh each time
(`core/Drills.kt`): they are the ones most widely known by heart, and a breath or two each.

**Only the exercise's own thing is judged, shown and kept** (`Drills.only`). The whole
verse is heard, since the rule can't be found in it otherwise, but of what was heard only
what the exercise is about is left:

- Before the verse is recited, the letters the rule falls on are in the rule's colour
  (`Drills.letters`: the rules as the page marks them, in the words where the checker
  listens for the rule; for the natural madd, the ordinary long vowels too, which the
  page gives no colour).
- Afterwards, no other word is marked, no count of sound words is given, and nothing is
  said of a madd in an exercise on the ghunnah. In place of that, each place of the rule
  is told as it was heard (`Measure`, `ui/RuleResults`): a madd as beads, one a count,
  filled as far as it was held ("1 of 4 counts"); a nasal sound as a bar, filled as far
  as it was kept; an echo, a nun turned or merged, as heard or not. A place the model
  wasn't sure of is shown as sound, as it is judged.
- What is kept for **Your weak spots** is the exercise's thing alone: another mistake in
  the verse is not counted against the reader, and one noted there earlier is not taken
  as put right (`Mistakes.note(only)`).
- A pair of letters and a stop are told word by word, as for any verse, but only for the
  pair's letters, or for the word to stop on.

A rule is judged only where its words were said. If none of the words that carry the
exercise's rule were said (the verse was cut short before them, say), the verdict is
**Not heard** instead of sound or to go over, the go doesn't count as sound, and a
mistake noted in that verse earlier stays on the list of verses to go over.

- **A rule's exercise**, reached from its lesson. One rule: the ghunnah of a doubled nun or mim, ikhfa, iqlab,
  idgham, qalqalah, or a madd (natural, munfasil, muttasil, lazim, or the one that comes
  of stopping). A verse is offered for a rule only if the rule is listened for in it.
- **Your weak spots.** Whatever is corrected when the reader recites, anywhere in the
  app, is kept by topic (`data/Mistakes.kt`): how often each came up, and the verses it
  came up in. This exercise is those verses again, the latest first, each judged for
  what was corrected in it. A verse recited again with that sound is taken off the list.
- **Letter pairs.** Verses with one of two letters made close to each other (ص س, ض د,
  ط ت, ظ ذ, ق ك, ح ه, ع ء), judged for one of the pair said for the other.
- **Stopping on a word.** A longer verse with a word marked to stop on, one that sounds
  different at a stop than recited on. It is sound if a stop is heard there and the word
  is said as it sounds at a stop.

The other three:

- **Madd counting.** The reader holds a long vowel and its counts fill, bead by bead,
  as they hold it; when they let go it says how many counts that was and whether it was
  on the mark, short or past the counts aimed for (2, 4, 5 or 6). A count is as long as
  the reader sets it, as their own pace has it. The holds so far stand as bars against
  the line aimed for, so their evenness shows. Voice is told from silence by loudness
  (`core/Holding.kt`); the speech model isn't used.
- **Spot the rule.** A word is shown without its colours, and played where a recording of
  its surah has been listened to; the reader says which family of rules is at work in
  it, out of four, and is shown the right one and what it is. No microphone.
- **Echo the reciter.** A short verse from one of the reader's recordings is played, the
  reader recites it and is corrected as ever; and for every madd longer than the natural
  one, the counts the reciter held it for stand beside the reader's own. The reciter's
  counts are had by having the same model listen to that stretch of the recording.

What these can't judge is what the checker can't: whether ر and ل are heavy or light,
and a nun held but not hidden.

### Training the voice

"Adorn the Quran with your voices" (Abu Dawud, al-Nasaʾi). The scholars' condition is
that a melody never changes a letter, a vowel or the length of a madd, so tajweed comes
first; and since scholars differ on deliberately learning the musical modes (maqamat),
the app teaches none. What it trains is what every voice needs:

- **Breath**, an exercise recitation teachers give: breathe in to a slow count of four,
  then let the air out on one steady sound. The app times the longest unbroken sound and
  says whether its loudness stayed even.
- **A steady note**: hold one easy note. The app shows how far it strayed, in cents
  (hundredths of a semitone).
- **Range**: slide from the lowest easy note to the highest. The app shows the reach in
  semitones, the low end named qarar and the high end jawab, as reciters name them.

While the microphone listens, the voice draws its own line on the screen, high notes
high. The pitch is found by the YIN method (de Cheveigné and Kawahara, 2002), on the
phone. The best result of each exercise is kept. The last two are general voice
exercises, and are said to be.

### Pins and collections

Hold a verse, or tap its number, to open its pins and collections.

Beside each verse's number, on its rule, are three small buttons. The triangle recites
from that verse: the recitation goes to its start and plays (it is there only when the
surah has a recording and the verse's start is known, exactly or by the quick look; if
the verse lies outside the verses chosen to be recited, the whole surah is chosen again).
The bookmark opens the verse's pins and collections, and is filled once the verse is kept
in a collection. The three dots open the rest: recite this verse only, show or hide its
translation, copy the verse, and share it. Copied or shared, a verse is its Arabic text,
its translation under it, and where it is from.

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

### What the page is made of: script, word meanings, translation, exegesis

Four things about a surah's page are the reader's to choose, each from its own list: in
the first screen's menu under **The page**, and in a surah's own menu without leaving it.
A row for each says what is chosen now; a tap opens the list.

| | What there is to choose | What comes without a download |
|---|---|---|
| **Script** | The four below | All but IndoPak |
| **Word meanings in** | English, Urdu, Persian, Turkish, Indonesian, Bengali, Hindi, Tamil: the languages Quran.com has every word's meaning in. And Arabic, from QuranEnc.com (below) | English |
| **Translation** | Every translation Quran.com lists (126 in 69 languages when this was written), under their languages, the phone's own first; the list can be searched | Saheeh International |
| **Exegesis (tafsir)** | Every exegesis Quran.com lists (20: Arabic, English, Urdu, Bengali, Russian, Kurdish) | None: none is chosen until the reader chooses one |

- **Word meanings in Arabic are of another kind, and from another place.** Quran.com has
  no word meanings in Arabic, so these are QuranEnc.com's «معاني الكلمات» (its
  `arabic_seraj`). They are what an Arabic mushaf prints in its margin: a meaning for
  the words and phrases a reader of Arabic may not know, and nothing for the rest. In the
  42 surahs looked at (1, 2, 18, 36, 67 and 78 to 114), 62 verses in 100 have at least
  one, and about one word in nine stands under one. They come a verse at a time, as lines
  of "the words: what they mean", and the app finds each line its word
  (`core/Glosses.kt`): by the word's letters alone, in everyday spelling, allowing one
  letter in four to differ, and looking in the verse before and after for a line the
  source has a verse off. Of 973 lines in those surahs, 968 found their word in their own
  verse and the other five in the verse beside it. A meaning of several words stands
  under the first of them and the rest have "…"; a line found nowhere would be shown
  whole under its verse's last word, so nothing of the source is left out. The text is
  shown as it comes, vowels and all; a word with no meaning has none, not the English
  one. QuranEnc.com gives no version number for it through its API.
- **Each one in a list says where it stands:** a tick on the one chosen; "With the app"
  on what needs no download; its size and a bin on one that is on the phone (the bin
  takes it off again; the one in use has none); a ring filling on the one being
  downloaded; an hourglass on one waiting its turn; an arrow on one a tap will download.
- **Choosing one that isn't on the phone downloads it, in the background, like every
  other download** (`data/Extras.kt`, and a lane of its own in the background work beside
  the sounds and the listening): a surah at a time from Quran.com, the surah being read
  first, with its own line in the notification. It goes on with the app closed, carries
  on where it stopped, waits for a connection when there is none, and is tried again
  later if Quran.com won't answer. It goes on while the recordings' work is paused, too.
- **The choice holds at once.** Until the surah being read has arrived (a moment: it is
  first in line) the page shows what it has, and changes by itself when it comes: the
  meanings and the translation that come with the surah's words, and for a script the
  Madinah mushaf's, so no word is ever shown in a font that isn't for its spelling.
- **A verse's exegesis** opens from the page-of-writing button beside the verse's number:
  the chosen exegesis of that verse, in headings and paragraphs, read from the side its
  language is written from. A verse explained together with the ones before it shows
  theirs, and says so. Its name stands over it, and a tap there chooses another. If the
  surah isn't on the phone yet it is fetched there and then. With none chosen, the
  button offers the list.
- Choosing a language for the words' meanings also turns the meanings on.
- Searching the Quran still goes by the everyday spelling and the Saheeh International
  translation, whatever is chosen for the page.

The lists of translations and exegeses are asked of Quran.com the first time they are
opened and kept, so they are there without a connection afterwards. Translations are
kept without their footnote marks; an exegesis is kept as Quran.com gives it.

#### Scripts

The recitation is always lined up with the Uthmani spelling, whichever script is shown.

| Script | What it is | Font | On the phone |
|---|---|---|---|
| **Uthmani · Madinah mushaf** (the default) | The Uthmani spelling as the King Fahd Complex typesets it for the Madinah mushaf | KFGQPC Uthmanic Script HAFS, shipped in the app | With the app |
| **Uthmani · Amiri hand** | The same spelling in a rounder hand | Amiri Quran, which the app's own screens are set in | With the app |
| **Everyday spelling** | Modern Arabic spelling (*imla'i*), fully vowelled | Amiri Quran | With the app: it is the spelling a search goes by, so every surah has it |
| **IndoPak** | The spelling and pause marks of the mushafs of the Indian subcontinent | "AlQuran IndoPak by QuranWBW", from Quran.com | Downloaded when chosen: its spelling of every word, and its font |

Up to version 1.68 every surah was fetched in all four spellings and the IndoPak font was
fetched when the app first ran, whether or not that script was ever used. Now only what
the default needs comes with a surah. Surahs fetched by those versions keep the IndoPak
spelling they came with; the rest is downloaded if the script is chosen.

**Not offered:** the Madinah mushaf exactly as printed, page by page (Quran.com's "V1",
"V2" and tajweed "V4" fonts). Each is a font for every one of the 604 pages, 95 to 208 MB
in the form a phone can read, and a word in them is one drawn shape, so the letter-by-
letter light and the app's own tajweed colours could not be shown on it. Nor other
readings than Hafs: Quran.com has no text of them, and the recordings are lined up with
Hafs.

### Lining words up with the sound

- **The words as sounds:** `core/Phonemizer.kt` turns each word, written in the Uthmani
  script with all its marks, into the sounds a reciter makes. That script spells recitation
  out closely (a letter with no mark is silent or merged into the next, long vowels and
  stops are marked), so following the marks gets close enough.
- **The model:** HisnLabs Tajweed v2 (Apache-2.0), the small model that also checks the
  reader's own recitation (see below). It writes down the sounds it hears in the Quran
  phonetic script, every 20 ms, rather than guessing words. `core/Sounds.kt` boils each
  20 ms down to the chance of a pause and of each sound a word can be made of: a long
  vowel takes its short vowel's sign too (the model writes قَالَ as قَ ا ا لَ, a long
  vowel once per count), a hidden nun or mim counts as the nun or mim, and the echo of a
  qalqalah counts with the pause.
- **A held sound at a word's start:** the model writes a held sound once for every count.
  A nun or tanween merged into the next word («لَهُم مَّغْفِرَةٌ», «مَن يَقُولُ») is that
  word's first letter held through the nose, and a doubled ل or sun letter is held too.
  The word is taken to begin where the hold is let go, not where it starts
  (`Recital.released`): that is where the published timings put it.
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

The stretches overlap by a third. They used to follow one another end to end, and an end
could fall in the middle of a passage said again, since the places trusted as ends come
from the first placing, which has no idea a passage was said twice. Cut in two like that,
the passage was seen in neither stretch. That is how a real one was missed: in a
recording of Ya-Sin its reciter says three words of 36:47 again, the published timings
show it, and it was found only when a passage spliced in earlier happened to move the
stretch's end. With the overlap it is found in the recording as it is.

Passages said again are also looked for while a recording is still being listened to, in
the part heard in order: every half minute of recitation, what has been placed since the
last look is gone through the same way. A stretch some twenty seconds behind the listening
is gone through once and what was found in it stands; nearer the end the reciter may be
in the middle of going back, so those stretches are gone through again next time. It is
only done while the placing is holding well (clarity of 0.8 or more): a placing that is
drifting sees him go back where he didn't. On six real recordings fed in ten-second
pieces, every passage the whole recording gives was found on the way, the same words at
the same times, 16 to 25 seconds after it was said, with none shown and then taken back;
one that falls in a recording's last half minute was left to the final placing, and the
muffled recording, whose placing drifts, was shown none. Where the recording is heard
out of turn, because it is being played there, the words are still placed once through,
so over a passage said again they stand still until it has passed.

While the reciter is saying a passage again, all the words he went back over are marked,
in a way of their own: their blocks are faintly tinted and a stream flows back along
their foot, the way he went, in two swells that run on unbroken from block to block. It
is there from the moment he goes back until he has said the passage again and goes on
past it, and the word being uttered is lit on top of it as ever. It goes by where the
recitation is, not by the phone's clock, so it shows whatever the phone does with
animations, stands still when the recitation is paused, and is there again when that
place is gone back to.

A short word at the start of the passage gone back to could be missed: said faintly
straight after a breath (the «إِذْ» of «إِذْ بَعَثَ» in 3:164), it can sound no more like itself
than like more of the sound the reciter stopped on, and he was then taken to have gone
back to the word after it. The model's rows are all speech (pauses aren't among them),
so rows right before the going back that were clear neither as the sound stopped on nor
as a pause are him saying something, and they are now given to the words before the one
gone back to, as many as they have room for (`Repeats.saidBefore`).

None of this has to be asked for, recording by recording. A recording's timings say
which way of placing the words they were made by (`Timings.method`). One that is ready
but was placed an earlier way is listened to once more by itself when nothing else is
waiting, one at a time, and keeps the timings it has, whole, until the new ones are
whole too. That is a recording's worth of listening each, so going through a whole
library takes many hours of the app being open; **Pause** holds it, and taking one out
of the queue leaves that one as it is. **Listen again**, in a recording's menu, still
does it for one right away.

**Listen again to some verses…**, in the same menu, does that for a part of the recording
only: choose the first and last verse, and just that stretch of the sound is heard again
(from three seconds before the first verse's first word to three seconds after the last
verse ends, by the timings the recording has), its words are placed afresh, passages said
again in it are looked for, and the result is put in place of those verses' timings. It
takes about as long as the stretch itself, not the whole recording. The stretch is found
from the timings there are, so if those were off right at a verse's start or end, take in
the verse next to it too.

### A quick look over the recording, for where the verses begin

Placing the words needs the recording to be listened to, which takes as long as it
takes. Going to a verse shouldn't have to wait for that. So `core/Sketch.kt` takes a
quick look over the recording that needs no model: how loud the recording is every 20 ms
shows where the reciter pauses, and each verse is taken to end on the pause that leaves
every verse closest to its share of the surah's sounds, longer pauses being the likelier
ends. That gives a rough start for every verse (the "sketch"), kept with the timings
until the real ones replace it.

The look begins with the download, reading the sound as it arrives
(`speech/Arriving.kt`), so it is done about when the download is, and the recording has
its rough verse starts from the moment it can be played. What was read as it arrived is
only used if it comes to the whole recording's length; otherwise the finished file is
looked over again. The pauses found are kept, so the listening, when the recording's
turn comes, doesn't look for them a second time.

The sketch is a guess and is treated as one. It is gone by only where no word has been
placed yet: for where "go to this verse" lands, and for which verse shows as playing. No
word is lit from it. Where it is wrong the reader lands off the mark; the listening is
sent to where they are, and what the model hears there takes the guess's place.

With it, before a verse has been heard: the next and previous verse buttons and the
"which verses to recite" chooser go to it; the page follows the verse being played,
lights all of it (softly: every block of the verse, not its number alone) and brings
all of it into view, in the middle of the page if it fits there; and the listening, which goes to where the recording is being played,
gets there straight away. Going to such a verse (by those buttons, the chooser, or the
play bar) puts everything else aside: the listening is sent there at once and stays with
the reader, up to five minutes ahead, before it goes back to the pieces in order; and the
recitation waits ("Listening to this verse first…") until the listening has been there,
so the words light up from the verse's first. Pressing play doesn't wait, and after a
minute and a half the recitation plays regardless. A recording whose listening had been
left off is taken up again, unless everything was paused.

The play bar's line is cut where each verse begins, so it reads verse by verse; verses
too short to tell apart on the line share a piece of it. It is grey where the recording
hasn't been heard yet and takes the bar's colour where it has: as far as it has been
heard in order, and every stretch heard out of turn, wherever the reader has been. Those
stretches are kept with the timings, so they show again when the app is reopened.

Against the published verse timings of four real recordings: within 2 seconds for all 30
verses of one, 29 of 30 of another, and 71 of 83 of a muffled one (worst 13 s). In a
recording made in a mosque, with the hall never silent and verses run together, there
are few pauses to find and the starts were about 10 seconds off: there the sketch only
gets one near the verse.

### Listening where the recording is being played

A recording is heard in ten-second pieces, in order from its start, and the words are
placed as far as that has got. If the reader plays the recording further on than that,
the listening used to carry on from where it was, and nothing lit up until it arrived.

Now it goes to where the recording is being played: the piece the reader is in, then on
from there, keeping up to five minutes ahead of them; with that in hand it goes back to the
pieces in order. What was heard out of turn has nothing placed before it to say where in
the surah it is, so `core/Locator.kt` finds it by its sounds: how far through the
recording it begins gives the neighbourhood (a fifth of the surah either way), and the
run of words its sounds fit best there, beginning at the start of a word, is taken. If
that fits poorly (under three sounds in five heard clearly) nothing is shown. Its words
light up where they are played until the pieces in order reach them; every piece is
heard once only, whatever the order, and the final placing of the whole recording is as
before.

Tried on stretches of 10, 20 and 60 seconds cut at nine places from each of four real
recordings: in the three clear ones every stretch but three was found, in the right
place each time; in the muffled one (clarity 0.74) 19 of 27 were left unshown and none
was put in the wrong place.

A stretch can grow to many minutes, when the recitation is paused and the listening
carries on ahead of it. So it is found in chunks of eighty seconds (`core/Stretch.kt`): a
chunk heard whole is found once and kept, and only the unfinished one is gone over again
as each piece arrives. Before, the whole stretch was found afresh on every piece, which
took longer the longer it grew, and past five or six minutes only its first half minute
was found. In chunks, on the three clear recordings, a stretch begun at any of four
places was found to the recording's end, with at most 17 ms of work a piece on a laptop.
About one word in fourteen of a stretch found this way lights up half a second to a
second off, mostly early, until the pieces in order reach it and place it properly.

### The speech model

One model does all the listening: **HisnLabs Tajweed v2**, 23 MB. It listens to the
recordings, to say when each word is recited, and to the reader reciting, to go over how
they said it. It is fetched once from this repo ([models/tajweed-v2](models/tajweed-v2)),
checked against its SHA-256, and kept on the phone. Everything after that runs on the
phone with ONNX Runtime.

Up to version 1.63 the recordings were listened to with another model
(`wav2vec2-lv-60-espeak-cv-ft`, 318 MB, written in IPA and trained on ordinary speech in
many languages), read out of Accent Coach's storage where that app was installed. It is
gone:

- **Measured before the change**, on recordings whose word times Quran.com publishes
  (`recitation-scratch/make_lp.py` in the notes, and the app's own placing run over the
  model's output). On the four recordings both models could be scored on (938 words):

  | recording | 23 MB model: typical error, within 0.1 s, within 0.3 s | 318 MB model |
  |---|---|---|
  | An-Naba, as-Sudais | 40 ms, 74%, 98% | 40 ms, 76%, 98% |
  | Al-Mulk, al-Husary | 41 ms, 72%, 85% | 60 ms, 67%, 85% |
  | Ya-Sin, al-`Afasy | 55 ms, 73%, 90% | 45 ms, 76%, 90% |
  | Al-Mulk, al-`Afasy | 55 ms, 74%, 92% | 45 ms, 73%, 91% |

  The same passages said again were found in each (among them the two spliced into Ya-Sin
  for the tests), and a recording set against another surah's words scored 0.38 to 0.57
  for clarity against 0.98 to 1.0 for its own, so the wrong surah is still told.
- **Speed:** on the Mac the model alone gets through a recording about 450 times as fast
  as it plays, against 25 times for the old one. On a phone it has not been measured.
- **Download:** 23 MB instead of 318 MB.
- **On a phone that had the old model:** the app's own copy of it, if it had one, is
  deleted the next time the app opens, with what that model had heard of recordings it
  hadn't finished (those are listened to again from their start). Recordings already
  listened to keep their timings. The fastest way to run the engine is tried afresh, since
  it was found for the other model.
- **Not known:** how the small model does on poor recordings (old, echoing, a phone held
  up in a mosque). The old model's clarity on a muffled recording was measured; this
  one's was not, as that recording is no longer on the Mac.

ONNX Runtime's Android library comes with a part that reports on its use. The app leaves
that part out (`tools:node="remove"` in the manifest); it could not start in this app in
any case, since it looks for the engine inside the package, where it no longer is.

### The speech engine

ONNX Runtime's native library, which runs the model, is 34 MB: nine tenths of what the
app's package used to weigh, and the same in every build. It is no longer inside the
package, which is about 4 MB instead of 38 MB, so every update is that much smaller. The
engine is put on the phone once, the first time a recording is prepared:

- **Accent Coach installed:** the library is copied out of [Accent Coach](../accent-coach/)'s
  installed package, which carries the very same file. Nothing is downloaded.
- **Otherwise:** it is downloaded from this repo ([engine/](engine/)), compressed (13 MB).

The file must match its fixed SHA-256 before it is kept, and is kept read-only in the
app's private storage. The small library that joins the engine to the app's Java side
(120 KB) stays inside the package: on Android, ONNX Runtime will load that one only from
the package. The app loads the engine first, by its full path, and the small library then
finds it already loaded. Versions 1.43 to 1.46 kept both files outside the package and
could not start the engine on a phone ("The speech engine couldn't be started"), so no
recording could be prepared with them. Reading and searching the Quran, and playing recordings that are
already prepared, don't need the engine.

How fast the engine listens depends on how many threads it is run on and on whether
those spin while they wait for work, and which is fastest differs from phone to phone.
So before the first recording is listened to, the app tries: eleven seconds of sound on 2
threads spinning and not, then on 4 threads if the phone has eight cores or more, and on
6 if it has twelve ("Finding the fastest way to listen on this phone…", once). Not
spinning is taken unless it is more than a tenth slower, since it keeps the phone cooler
and a hot phone slows itself down. Then the fewest threads that are within a quarter of
the fastest: the listening shares the phone with everything else, and is never run on
more than half its cores. What was found is kept for that version of the engine and of
the model and written in the journal, so the report shows it. Where the engine can't be
tried, it is run on 2 threads.

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

The model is given each ten-second piece with half a second of the sound either side of
it, for context. It used to be a second. On five real recordings heard both ways, the
words came out as near the published timings with half a second: the same median in
each, and 517 of 1,943 words more than 0.3 s off against 516. That is 11 seconds for the
model to get through for every ten, instead of 12.

Once all of a recording has been heard, every word is placed again against the whole of
it. For a long surah that is a large search; its two halves, and the two passes that find
where to halve it, don't depend on each other, so they are worked on side by side, on up
to four threads, with the same result.

## Limits

- **Not yet run on a phone.** The app was built and its logic tested on a Mac. Decoding
  the sound, running the model, playback, background work and installing updates use
  Android itself and have only been compiled. That includes starting the engine from the
  app's storage rather than from inside its package. If the engine won't start on a phone,
  the recording being prepared says so, with Android's own wording.
  Two things about the listening rest on how Android behaves and could only be checked
  on a phone: that the engine's own threads take the low priority of the thread that
  makes them, and when exactly Android counts the phone as charging and unused and
  starts the run for that.
- **One surah per video.** A video with several surahs, part of a surah, or verses
  repeated (as in prayer) won't line up; the app will usually say it doesn't match.
- **Old or echoing recordings** are heard less clearly and line up less tightly.
- **Speed on a phone is unknown.** On the Mac the model runs about 450 times faster than
  the recording plays (the model it replaced ran 25 times faster). A phone is much slower;
  how much is not known.
  While a recording is being listened to, the card on the first screen shows how the
  listening compares with playing it as a figure ("1.3×"), so it can be
  read off a phone. Under 1×, the listening can't keep up with someone playing the
  recording, wherever it starts.
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
| Quran text (Uthmani, IndoPak and everyday spellings), word meanings, and whichever translations and exegeses the reader chooses | [Quran.com](https://quran.com) API, fetched by the app and kept on the phone; not stored in this repo | Their own terms, and each translation's and exegesis's own |
| Word meanings in Arabic, if the reader chooses them | [QuranEnc.com](https://quranenc.com)'s «اللغة العربية - معاني الكلمات» (`arabic_seraj`), fetched by the app from its API and kept on the phone; not stored in this repo. Shown unaltered, with its source named where it is chosen | QuranEnc.com's terms: may be republished unmodified, with the source named |
| Speech model, for the recordings and for the reader reciting | [HisnLabs Tajweed v2](https://huggingface.co/Hisnlabs/tajweed-v2), int8 ONNX, in [models/tajweed-v2](models/tajweed-v2) and fetched by the app. Its base is the encoder of tarteel-ai/whisper-base-ar-quran; its teacher was Quran Muaalem | Apache-2.0 ([text](licenses/Apache-2.0.txt)) |
| How a recitation is compared with the verse | The rule engine published with that model (`imam.py`), written again in Kotlin | Apache-2.0 ([text](licenses/Apache-2.0.txt)) |
| What each word should sound like (`tajweed_reference.bin`) | Written with [quran-transcript](https://github.com/obadx/quran-transcript), from the Tanzil Uthmani text it carries (Tanzil Project, tanzil.net) | MIT for quran-transcript; Creative Commons Attribution 3.0 for the Tanzil text ([both](licenses/QuranTranscript-MIT-Tanzil.txt)) |
| Speech engine | [ONNX Runtime](https://github.com/microsoft/onnxruntime) 1.31.1: its Java classes in the app, its native library in [engine/](engine/) and fetched by the app | MIT ([text](licenses/ONNXRuntime-MIT.txt)) |
| Reading YouTube | [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) | GPL-3.0 |
| Font for the Madinah mushaf script | KFGQPC Uthmanic Script HAFS, King Fahd Glorious Quran Printing Complex | Free to use, copy and distribute; may not be sold or modified ([text](licenses/UthmanicHafs-KFGQPC.txt)) |
| Font for the Amiri and everyday scripts, and the basmala | [Amiri Quran](https://github.com/aliftype/amiri) | SIL Open Font License ([text](licenses/AmiriQuran-OFL.txt)) |
| Font for the IndoPak script | "AlQuran IndoPak by QuranWBW", fetched by the app from Quran.com when that script is chosen and kept on the phone; not stored in this repo. Used only if it is byte for byte the file the app was written against | None stated: their own terms |
| Calligraphy of the surahs' names | Quran.com's surah-name font, fetched by the app on first run and kept on the phone; not stored in this repo. It is used only if it is byte for byte the file the app was written against | None stated: their own terms |
| Font for the surahs' names until that is fetched | [Aref Ruqaa](https://github.com/alif-type/aref-ruqaa) | SIL Open Font License ([text](licenses/ArefRuqaa-OFL.txt)) |

Because it includes NewPipe Extractor, this app's own code is under the
[GPL-3.0](LICENSE).

## Project layout

```
recitation/
├── core/    Pure Kotlin/JVM, unit-tested: words to sounds, listening in pieces, alignment,
│            word timings, surah names and reading them from titles; and for the reader's
│            own voice: the spectrogram, the heard script, breaths and corrections, pitch
├── yt/      Pure Kotlin/JVM: playlists and sound from YouTube (NewPipe Extractor)
├── app/     Android app (Jetpack Compose)
│   ├── data/     The playlists added, the verses from Quran.com, pins and collections, fetched fonts
│   ├── speech/   Where the models and the engine come from, sound decoding, the microphone, ONNX models
│   ├── prepare/  The background work that gets a recording ready
│   ├── player/   Playback service
│   └── ui/       The surah list, playlists, recordings, reading, reciting and voice screens
├── engine/  ONNX Runtime's native library, compressed, for the app to fetch once
├── models/  The tajweed model, for the app to fetch once
├── tools/
│   ├── make_surahs.py            Writes core's list of surah names from Quran.com
│   ├── make_test_recitation.py   Writes the real recitation core's tests align
│   └── make_tajweed_reference.py Writes what every word of the Quran should sound like
└── apk/     Ready-to-install builds
```

## Build

Needs JDK 17 and the Android SDK (platform 35).

```bash
cd apps/recitation
./gradlew :core:test              # unit tests for the logic
./gradlew :yt:test                # downloading, from a stand-in server on this machine
./gradlew :app:testDebugUnitTest  # draws each screen, in both languages and themes, to app/build/screens; also the files kept on the phone, fetching a surah, the player's stop at a verse, and the update's background service
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
- If Android's install prompt is closed without an answer, the app notices when you come
  back to it and offers the downloaded update again with **Install**.
- It looks for a newer build again each time you come back to it, at most every ten
  minutes. A build newer than the one the pop-up was closed on brings the pop-up back.
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
| `ACCESS_NETWORK_STATE` | Knowing whether there is a connection: without one, only recordings with everything on the phone are worked on. |
| `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Installing those updates. |
| `POST_NOTIFICATIONS` | Showing progress while recordings are prepared and while an update downloads. The work runs without it too. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK` | Keeping that work going in the background. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Reciting with the screen off. |
| `RECORD_AUDIO` | Hearing the reader recite, to check the recitation and for the voice exercises. Asked for the first time the microphone button is pressed. What is heard is worked on in the phone's memory and is neither kept nor sent anywhere. |

package app.recitation.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.scale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import app.recitation.data.VerseRef
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.StrokeCap
import app.recitation.core.WordRecited
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import app.recitation.R
import app.recitation.data.Appearance
import app.recitation.data.Script
import app.recitation.Reading
import app.recitation.core.Family
import app.recitation.core.Mark
import app.recitation.core.Recital
import app.recitation.core.Uttered
import app.recitation.core.SURAHS
import app.recitation.core.Surah
import app.recitation.core.Timings
import app.recitation.core.WordTiming
import app.recitation.data.Verse
import app.recitation.data.Word

/**
 * Finds the word being recited at a moment of the recording. Words are counted through the
 * whole surah, [sizes] being how many each verse has; while the recording is still being
 * listened to, only the first of them have a time yet.
 */
internal class WordIndex(timings: Timings, sizes: List<Int>) {
    val starts: IntArray = timings.verses.flatMap { verse -> verse.map { it.startMs } }.toIntArray()
    val firstOfVerse: IntArray
    private val verseOf: IntArray

    init {
        val first = ArrayList<Int>()
        val of = ArrayList<Int>()
        for ((v, size) in sizes.withIndex()) {
            first += of.size
            repeat(size) { of += v }
        }
        firstOfVerse = first.toIntArray()
        verseOf = of.toIntArray()
    }

    // Every time a word is said, in the order they are said: each word once, and once more
    // for every passage the reciter went back over.
    private val saidAt: IntArray
    private val saidWord: IntArray
    private val saidTiming: List<WordTiming>

    init {
        val said = ArrayList<Triple<Int, Int, WordTiming>>()
        timings.verses.flatten().forEachIndexed { word, timing -> said += Triple(timing.startMs, word, timing) }
        for (repeat in timings.repeats) repeat.words.forEachIndexed { i, timing -> said += Triple(timing.startMs, repeat.first + i, timing) }
        said.sortBy { it.first }
        saidAt = IntArray(said.size) { said[it].first }
        saidWord = IntArray(said.size) { said[it].second }
        saidTiming = said.map { it.third }
    }

    // While the recording is still being listened to, words are placed only this far, and
    // in the stretches that were heard out of turn because they were being played.
    private val placedUntil = if (timings.complete) Int.MAX_VALUE else timings.heardMs
    private val ahead = timings.ahead

    /** Which saying of a word is under way at [ms]: the last to have started; -1 before the first, or past where words have been placed. */
    fun said(ms: Int): Int {
        val stretch = if (ms > placedUntil) ahead.firstOrNull { ms in it } ?: return -1 else null
        var lo = 0
        var hi = saidAt.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (saidAt[mid] <= ms) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        // In a stretch heard out of turn, only its own words count: not the last word placed before it.
        if (stretch != null && found >= 0 && saidAt[found] < stretch.first) return -1
        return found
    }

    /**
     * The words the reciter goes back over when saying [said] begins, if it begins a passage
     * said again: from the word he goes back to, to the word he had got to.
     */
    fun goesBack(said: Int): IntRange? {
        val to = saidWord.getOrNull(said) ?: return null
        val from = saidWord.getOrNull(said - 1) ?: return null
        return if (to < from) to..from else null
    }

    /**
     * A passage being said again: the words gone back over, when the reciter went back to
     * the first of them, and when he has said them all again and goes on past them, in ms.
     */
    data class GoneBack(val block: IntRange, val atMs: Int, val untilMs: Int)

    // The sayings that begin a passage said again, in order.
    private val backs = (1 until saidWord.size).filter { saidWord[it] < saidWord[it - 1] }.toIntArray()

    /** The passage said again that the reciter most lately went back over, as of [ms]; null before the first. */
    fun goneBack(ms: Int): GoneBack? {
        val now = said(ms)
        val back = backs.lastOrNull { it <= now } ?: return null
        val block = saidWord[back]..saidWord[back - 1]
        val past = (back + 1 until saidWord.size).firstOrNull { saidWord[it] > block.last }
        return GoneBack(block, saidAt[back], past?.let { saidAt[it] } ?: Int.MAX_VALUE)
    }

    /** The word being said at [ms]; -1 as for [said]. */
    fun at(ms: Int): Int = saidWord.getOrElse(said(ms)) { -1 }

    /** The timing of that saying of the word, and when the next thing is said. */
    fun timing(said: Int): WordTiming? = saidTiming.getOrNull(said)
    fun ends(said: Int): Int = saidAt.getOrElse(said + 1) { Int.MAX_VALUE }

    fun verse(word: Int): Int = verseOf.getOrElse(word) { -1 }

    // When each verse starts: by its first word where that is placed, in order or out of
    // turn; otherwise roughly, by the quick look taken over the recording; -1 if not at all.
    private val verseStarts = IntArray(firstOfVerse.size) { v ->
        val word = firstOfVerse[v]
        starts.getOrNull(word)
            ?: timings.repeats.firstNotNullOfOrNull { again -> again.words.getOrNull(word - again.first)?.startMs?.takeIf { word >= starts.size } }
            ?: timings.sketch?.getOrNull(v) ?: -1
    }

    /** When [verse] starts, exactly or roughly; null if nothing says. */
    fun startOfVerse(verse: Int): Int? = verseStarts.getOrNull(verse)?.takeIf { it >= 0 }

    /** The verse being recited at [ms], going by when the verses start: for where no word is placed. -1 if nothing says. */
    fun verseAt(ms: Int): Int {
        var found = -1
        for (v in verseStarts.indices) if (verseStarts[v] in 0..ms) found = v
        return found
    }
}

/**
 * The reading screen: every verse as a row of word tiles, each with its meaning underneath.
 * The word being recited is framed, a light glides under its letters, and the page scrolls to keep
 * its verse in view. [position] is where the recording is, in ms; it is read as often as
 * the screen redraws, so it is a function rather than a value.
 */
@Composable
fun ReaderContent(
    reading: Reading,
    showMeanings: Boolean,
    playing: Boolean,
    position: () -> Int,
    duration: Int,
    onBack: () -> Unit,
    onMeanings: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    withoutRecitation: @Composable () -> Unit = {},
    suggestion: @Composable () -> Unit = {},
    textScale: Float = 1f,
    onTextScale: (Float) -> Unit = {},
    range: IntRange? = null,
    listening: Boolean = true,
    onRange: (IntRange) -> Unit = {},
    script: Script = Script.MADINAH,
    indopak: FontFamily? = null,
    startVerse: Int? = null,
    marks: VerseMarks = VerseMarks(),
    onMark: (Int) -> Unit = {},
    appearance: Appearance = Appearance.AUTO,
    onAppearance: (Appearance) -> Unit = {},
    tajweed: Boolean = true,
    onTajweed: (Boolean) -> Unit = {},
    speed: Float = 0f,
    waiting: Boolean = false,
    listeningAt: Int = -1,
    recital: PageRecital? = LocalPageRecital.current,
    onSurah: ((Int) -> Unit)? = null,
) {
    val surah = SURAHS[reading.surah - 1]
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val verses = pluralStringResource(R.plurals.verses, surah.verses, surah.verses)
    // Also while the reader recites to the phone: their eyes are on the page, not their hands.
    KeepScreenOn(playing || recital?.state == Reciting.Listening)
    // With no recording there are no timings: the words are simply shown, and nothing is lit.
    val index = remember(reading) { reading.timings?.let { WordIndex(it, reading.verses.map { verse -> verse.words.size }) } }
    val current by remember(index) { derivedStateOf { index?.at(position()) ?: -1 } }
    // A passage said again has timings of its own: which saying of the word this is.
    val said by remember(index) { derivedStateOf { index?.said(position()) ?: -1 } }
    // Where no word is placed yet, the verse being played is still known, roughly.
    val unheardVerse by remember(index) { derivedStateOf { if (index == null || index.at(position()) >= 0) -1 else index.verseAt(position()) } }
    val currentVerse = if (current >= 0) index?.verse(current) ?: -1 else unheardVerse
    // When the reciter goes back to say a passage again, all the words he goes back over are
    // marked for as long as he is saying them again: faintly tinted, with a stream flowing
    // back under them, the way he went. The word being uttered is lit on top of that as ever.
    // The wave goes by the recording's own clock, not the phone's: it is where the
    // recitation is that says how far it has got, so it runs whatever the phone does with
    // animations, however the page is drawn again meanwhile, and again if that place is gone back to.
    val back by remember(index) { derivedStateOf { index?.goneBack(position())?.takeIf { position() < it.untilMs } } }
    // How strongly a word is marked as being said again, 0..1: it comes up when he goes back and fades as he goes on past the passage.
    val wash = remember(index) {
        { word: Int ->
            val gone = back
            if (gone == null || word !in gone.block) 0f else {
                val ms = position()
                minOf((ms - gone.atMs) / STREAM_IN_MS, (gone.untilMs - ms) / STREAM_OUT_MS, 1f).coerceAtLeast(0f)
            }
        }
    }
    // Where each verse's first word is among all the surah's words, whether or not there is a recording.
    val firsts = remember(reading) { IntArray(reading.verses.size).also { var n = 0; for (v in it.indices) { it[v] = n; n += reading.verses[v].words.size } } }
    // Reciting to the phone: the word the reader is heard to be at is the one the page follows.
    val reciting = recital?.takeIf { it.state == Reciting.Listening && it.verse in 1..reading.verses.size && it.reached >= 0 }
    val lead by rememberUpdatedState(if (reciting != null) firsts[reciting.verse - 1] + reciting.reached else current)
    val above = 1 // the surah's title page comes before the first verse in the list
    // Opened at a verse (from a pin or a collection): that verse is at the top of the page.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = startVerse?.let { it + above } ?: 0)
    // The IndoPak script needs its own font, which is fetched; until it is here, the Madinah mushaf's.
    // Kept from one drawing to the next: every word is handed it, and a new one would have them all drawn again.
    val hand = remember(script, indopak) { hand(if (script == Script.INDOPAK && indopak == null) Script.MADINAH else script, indopak) }

    // The page follows the recitation until the reader scrolls it themselves. Opened at a
    // verse the recitation can't be moved to, it stays on that verse instead.
    var follow by remember { mutableStateOf(startVerse == null || index?.startOfVerse(startVerse) != null) }
    LaunchedEffect(list) {
        list.interactionSource.interactions.collect { if (it is DragInteraction.Start) follow = false }
    }
    // The word being recited is kept at the middle of the page, always in the same place
    // for a quick glance. The page glides after it like a camera: it speeds up and slows
    // down gently and never restarts when the next word comes. A jump too far to glide
    // (a seek to another part of the surah) is made out of sight instead: the page fades,
    // is moved, and comes back drifting the last of the way from the side it travelled.
    val place = remember { WordPlace() }
    // The top bar slides away once the page goes on down the surah, whether the reader's
    // finger moves it or the recitation carries it, so the verses have the room. It is
    // back when the reader pulls the page back, when the recitation is paused, and at the
    // surah's start. While the page follows the recitation only going on counts: a passage
    // said again takes the page back a line or two, and the bar isn't to come and go with
    // that. The page lies under the bar, so nothing is laid out again as it slides. With
    // the note that the recording isn't this surah, the bar stays.
    val bar = remember { SlidingBar() }
    val barStays = reading.timings?.matches == false
    place.top = if (barStays) 0f else bar.strip.toFloat()
    val enoughToTell = with(LocalDensity.current) { 24.dp.toPx() }
    val going by rememberUpdatedState(playing || reciting != null)
    LaunchedEffect(list, barStays) {
        if (barStays) {
            bar.away = false
            return@LaunchedEffect
        }
        var before = emptyMap<Int, Int>() // where each item on the page was, by its number
        var firstBefore = 0
        var gone = 0f // how far the page has gone the one way since it last turned
        snapshotFlow { list.layoutInfo.visibleItemsInfo }.collect { showing ->
            val first = showing.firstOrNull() ?: return@collect
            // Measured on an item that was on the page before and still is; with none, the page jumped.
            val same = showing.firstOrNull { it.index in before }
            val moved = when {
                same != null -> (before.getValue(same.index) - same.offset).toFloat()
                before.isEmpty() -> 0f
                first.index > firstBefore -> enoughToTell
                else -> -enoughToTell
            }
            before = showing.associate { it.index to it.offset }
            firstBefore = first.index
            gone = if (moved * gone < 0) moved else gone + moved
            when {
                // At the surah's start, where the bar would only uncover the empty room it stood on.
                first.index == 0 && -first.offset < bar.height - bar.strip -> bar.away = false
                // Not for the page settling on its word while nothing is being recited.
                gone >= enoughToTell -> if (going || list.isScrollInProgress) bar.away = true
                gone <= -enoughToTell && !follow -> bar.away = false
            }
        }
    }
    // Paused, the bar is back: that is when the way out and the menu are wanted.
    LaunchedEffect(playing) { if (!playing) bar.away = false }
    with(LocalDensity.current) {
        place.margin = 4.dp.toPx()
        // Between a verse's last line and the next verse's first there is the rule with its star, and perhaps a translation.
        place.gap = 120.dp.toPx()
    }
    val fade = remember { Animatable(1f) }
    val drift = remember { Animatable(0f) }
    val travel = with(LocalDensity.current) { 28.dp.toPx() }
    val verseHead = with(LocalDensity.current) { 56.dp.toPx() }
    suspend fun travelTo(onward: Boolean, move: suspend () -> Unit) {
        try {
            fade.animateTo(0f, tween(160))
            move()
            drift.snapTo(if (onward) travel else -travel)
            coroutineScope {
                launch { fade.animateTo(1f, tween(340)) }
                launch { drift.animateTo(0f, tween(340, easing = LinearOutSlowInEasing)) }
            }
        } finally {
            // Cut short by another jump: never left faded out.
            withContext(NonCancellable) {
                fade.snapTo(1f)
                drift.snapTo(0f)
            }
        }
    }
    fun onward(item: Int) = item > (list.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0)
    // How fast the page is gliding, in px per second: kept from one word to the next, and
    // through the timings changing under it while the recording is still being listened to.
    val gliding = remember { floatArrayOf(0f) }
    // The verse of the word the page last followed: where its words run out, the page stays with it.
    val followed = remember(reading.surah) { intArrayOf(-1) }
    LaunchedEffect(follow, index, firsts) {
        if (!follow) {
            fade.snapTo(1f)
            gliding[0] = 0f
            return@LaunchedEffect
        }
        fun verseOf(word: Int) = firsts.indexOfLast { it <= word }
        snapshotFlow { lead }.collectLatest { word ->
            if (word < 0) return@collectLatest
            followed[0] = verseOf(word)
            var last = 0L
            var waited = 0
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.05f)
                last = now
                val off = place.offCentre(word)
                if (off == null) {
                    // Its verse isn't on the page yet (it is far away): go there out of sight.
                    if (++waited < 3) continue
                    travelTo(onward(verseOf(word) + above)) {
                        list.scrollToItem(verseOf(word) + above)
                        repeat(4) { if (place.offCentre(word) == null) withFrameNanos { } }
                        place.offCentre(word)?.let { list.scrollBy(it) }
                    }
                    gliding[0] = 0f
                    break
                }
                waited = 0
                if (abs(off) > place.pageHeight() * 1.5f) {
                    travelTo(off > 0) { list.scrollBy(off) }
                    gliding[0] = 0f
                    break
                }
                if (fade.value < 1f) fade.snapTo(1f)
                if (abs(off) < 1f && abs(gliding[0]) < 8f) break // there
                // A critically damped spring: pulled toward the word, never overshooting.
                gliding[0] += (STIFFNESS * STIFFNESS * off - 2 * STIFFNESS * gliding[0]) * dt
                val step = gliding[0] * dt
                if (abs(list.dispatchRawDelta(step)) < abs(step) * 0.5f) {
                    gliding[0] = 0f // the page can't scroll any further that way
                    break
                }
            }
        }
    }

    // With no word to follow, the page goes to the verse being played and shows all of it:
    // in the middle of the page if it fits there, from the top bar's foot down if it is
    // taller. On to the next verse it glides; anywhere further it travels out of sight.
    // The verse whose words the page was following is another matter: where its words run
    // out the page never goes back up to its start. It only goes on down, as far as shows
    // the rest of the verse without losing the line that was being recited.
    LaunchedEffect(follow, unheardVerse) {
        if (!follow || unheardVerse < 0) return@LaunchedEffect
        val item = unheardVerse + above
        // How far the page has to move for the verse to be where it should; null if it isn't on the page.
        fun off(onwardOnly: Boolean): Float? {
            val info = list.layoutInfo
            val shown = info.visibleItemsInfo.firstOrNull { it.index == item } ?: return null
            val top = (shown.offset - info.viewportStartOffset).toFloat()
            val head = (if (barStays) 0 else if (bar.away) bar.strip else bar.height) + verseHead / 4
            val foot = info.viewportEndOffset - info.viewportStartOffset - verseHead / 4
            val wanted = if (shown.size <= foot - head) head + (foot - head - shown.size) / 2 else head
            val by = top - wanted
            return if (onwardOnly) by.coerceIn(0f, ((place.lineTop() ?: head) - head).coerceAtLeast(0f)) else by
        }
        if (unheardVerse == followed[0]) {
            off(onwardOnly = true)?.takeIf { it >= 1f }?.let { list.animateScrollBy(it) }
            return@LaunchedEffect
        }
        followed[0] = -1
        val showing = list.layoutInfo.visibleItemsInfo
        if (showing.any { it.index == item || it.index == item - 1 }) {
            if (showing.none { it.index == item }) list.animateScrollToItem(item)
            off(onwardOnly = false)?.let { list.animateScrollBy(it) }
        } else {
            travelTo(onward(item)) {
                list.scrollToItem(item)
                withFrameNanos { }
                off(onwardOnly = false)?.let { list.scrollBy(it) }
            }
        }
    }

    // Whether the word being recited is above where the page now is: the way the follow button points.
    val recitedAbove by remember(index, firsts) {
        derivedStateOf {
            val item = (if (lead >= 0) firsts.indexOfLast { it <= lead } else -1) + above
            val showing = list.layoutInfo.visibleItemsInfo
            when {
                item < above || showing.isEmpty() -> false
                item < showing.first().index -> true
                item > showing.last().index -> false
                else -> (place.offCentre(lead) ?: 0f) < 0f
            }
        }
    }

    // A verse's translation shows when the verse is tapped, and goes away by itself after
    // long enough to read it. Tapping again while it shows starts the wait afresh.
    var translated by remember(reading.surah) { mutableStateOf<Int?>(null) }
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(translated, taps) {
        val verse = translated ?: return@LaunchedEffect
        delay(readingTime(reading.verses.getOrNull(verse)?.translation.orEmpty()))
        translated = null
    }

    fun seekToVerse(verse: Int) {
        val words = index?.takeIf { it.firstOfVerse.isNotEmpty() } ?: return
        // A verse the listening hasn't reached yet has no place to jump to.
        val start = words.startOfVerse(verse.coerceIn(0, words.firstOfVerse.size - 1)) ?: return
        onSeek(start)
        follow = true
    }

    // Which verses to recite is chosen in a dialog, opened from the menu or from the player's bar.
    var choosing by remember { mutableStateOf(false) }
    if (choosing && range != null) {
        RangeDialog(reading.verses.size, range, onDone = { choosing = false; onRange(it) }, onDismiss = { choosing = false })
    }
    // The menu's panel comes in beside the page from the side its button is on, the side
    // reading ends at, pushing the page aside. In it is what belongs to reading a surah:
    // which verses to recite, and how the verses are shown, by day or by night.
    var menu by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    if (picking && onSurah != null) {
        SurahPicker(onPick = { picking = false; if (it != reading.surah) onSurah(it) }, onDismiss = { picking = false }, current = reading.surah)
    }
    SidePanel(
        open = menu, onClose = { menu = false }, title = stringResource(R.string.reading), fromEnd = true, tag = "reader-menu",
        panel = {
            // Reciting the surah to the phone: it finds the verse by itself, follows it down the page, and goes over each verse as it ends.
            if (recital != null) {
                Row(
                    Modifier.fillMaxWidth().testTag("recite-to-phone").clickable(role = Role.Button) {
                        menu = false
                        // The reader's own voice is listened to, not the recording's: that is paused first.
                        if (!recital.active) {
                            if (playing) onPlayPause()
                            follow = true
                        }
                        recital.onToggle()
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (recital.active) AppIcons.Stop else AppIcons.Mic, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(if (recital.active) R.string.recite_stop else R.string.recite_to_phone), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (range != null) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { menu = false; choosing = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Range, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.range_title), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (range == 0..reading.verses.lastIndex) stringResource(R.string.range_whole) else stringResource(R.string.range_short, range.first + 1, range.last + 1),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            ReadingChoices(showMeanings, onMeanings, textScale, onTextScale, appearance, onAppearance, tajweed, onTajweed)
        },
    ) {
            Scaffold(
                topBar = {
                    Banner(bar) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                            val onBanner = LocalBanner.current.content
                            // The surah's name is also the way to another surah: a tap opens the list of them.
                            Column(
                                Modifier.weight(1f).then(
                                    if (onSurah == null) Modifier else Modifier.clip(RoundedCornerShape(12.dp))
                                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.choose_surah)) { picking = true }.testTag("surah-switch"),
                                ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SurahTitle(surah, 24.sp, onBanner, Modifier.testTag("surah-title"), withWord = true)
                                    if (onSurah != null) Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(20.dp), tint = LocalBanner.current.quiet)
                                }
                                Text(
                                    // Under the Arabic name: the surah's number, its Latin spelling, how long it is.
                                    if (arabic) verses else stringResource(R.string.name_and_verses, stringResource(R.string.surah_chip, surah.number, surah.latin), verses),
                                    style = MaterialTheme.typography.bodySmall, color = LocalBanner.current.quiet,
                                )
                            }
                            // What is being done to other surahs meanwhile: out of the player's way, up here.
                            DoingChip(size = 26.dp, apartFrom = reading.surah)
                            IconButton(onClick = { menu = true }) { Icon(AppIcons.Menu, stringResource(R.string.menu)) }
                        }
                    }
                },
                bottomBar = {
                    Column {
                        suggestion()
                        if (recital?.active == true) {
                            ReciteBar(recital)
                        } else if (index == null) {
                            withoutRecitation()
                        } else PlayerBar(
                            // Still being listened to: say how far the words can light up.
                            placedUntil = reading.timings?.takeIf { !it.complete }?.heardMs, listening = listening,
                            heard = reading.timings?.takeIf { !it.complete }?.let { listOf(0..it.heardMs) + it.ahead }, speed = speed, waiting = waiting,
                            surah = reading.surah, listeningAt = listeningAt,
                            verseStarts = remember(index) { (1 until reading.verses.size).mapNotNull(index::startOfVerse) },
                            range = range?.takeIf { it != 0..reading.verses.lastIndex }, onChooseRange = { choosing = true },
                            playing = playing, position = position, duration = duration, onPlayPause = onPlayPause,
                            onSeek = { onSeek(it); follow = true },
                            onPrevious = {
                                // Back to the start of this verse, or to the one before if it has only just begun.
                                val start = index.startOfVerse(currentVerse) ?: 0
                                seekToVerse(if (position() - start > 2_000) currentVerse else currentVerse - 1)
                            },
                            onNext = { seekToVerse(currentVerse + 1) },
                        )
                    }
                },
                floatingActionButton = {
                    if (!follow && (index != null || reciting != null)) FollowButton(up = recitedAbove, onClick = { follow = true })
                },
            ) { padding ->
                // The verses run up under the top bar, which slides off them; only the note stands below it.
                val top = padding.calculateTopPadding()
                Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                    if (barStays) {
                        Text(
                            stringResource(R.string.mismatch, surahName(surah)),
                            Modifier.padding(top = top).padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Verses(if (barStays) 0.dp else top, with(LocalDensity.current) { place.top.toDp() }, reading, index, list, current, currentVerse, index?.timing(said), index?.ends(said) ?: Int.MAX_VALUE, position, showMeanings, textScale, hand, tajweed, marks, onMark, place, { fade.value }, { drift.value }, wash, translated, { translated = it; taps++ },
                        recital = recital, firsts = firsts,
                        // Reciting from a verse outside the verses chosen to be recited takes the whole surah again.
                        onPlayFrom = { verse ->
                            if (range != null && verse !in range) onRange(0..reading.verses.lastIndex)
                            seekToVerse(verse)
                            if (!playing) onPlayPause()
                        },
                        onOnly = if (range == null) null else { verse: Int ->
                            onRange(verse..verse)
                            seekToVerse(verse)
                            if (!playing) onPlayPause()
                        },
                    ) { ms ->
                        onSeek(ms)
                        follow = true
                    }
                }
            }
    }
}

/**
 * Keeps the phone's screen from dimming and turning off while [on]: while a recitation is
 * playing with its words on show. Someone glancing at the words as they drive can't keep
 * tapping the screen awake. Paused, or with the surah left, the phone sleeps as usual.
 */
@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Takes up following the recitation again, after the page was scrolled away from it: a
 * disc in the app's colour with lines of text on it and, beside them, an arrow for the
 * way to the word being recited ([up] the page, or down it).
 */
@Composable
fun FollowButton(up: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.follow)
    Box(
        Modifier.size(60.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(54.dp)) { drawCircle(colors.primary) }
        Icon(if (up) AppIcons.FollowUp else AppIcons.FollowDown, null, Modifier.size(30.dp), tint = colors.onPrimary)
    }
}

/** Where the word being recited sits on the page, so the page can be moved to centre it. */
private class WordPlace {
    var list: LayoutCoordinates? = null
    private var word = -1
    private var tile: LayoutCoordinates? = null

    /** A word on the line that was being recited just before the line the word is on now. */
    private var lineBefore: LayoutCoordinates? = null

    /** The room kept between a line and the edge of the page, and the most that may lie between two lines for one to count as the line before the other, in px. */
    var margin = 0f
    var gap = 0f

    /** How much of the page's top is always covered: the strip behind the phone's status bar, in px. */
    var top = 0f

    fun placed(word: Int, tile: LayoutCoordinates) {
        val page = list
        val old = this.tile
        if (page != null && old != null && old !== tile && page.isAttached && old.isAttached && tile.isAttached) {
            val was = page.localBoundingBoxOf(old, clipBounds = false)
            val now = page.localBoundingBoxOf(tile, clipBounds = false)
            when {
                now.top - was.top > was.height / 2 -> lineBefore = old // on to the next line
                was.top - now.top > was.height / 2 -> lineBefore = null // gone back: the line above wasn't just read
            }
        }
        this.word = word
        this.tile = tile
    }

    fun pageHeight(): Float = list?.size?.height?.toFloat() ?: 0f

    /** How far down the page the top of the line last recited is, in px; null if it isn't laid out. */
    fun lineTop(): Float? {
        val page = list?.takeIf { it.isAttached } ?: return null
        val tile = tile?.takeIf { it.isAttached } ?: return null
        return page.localBoundingBoxOf(tile, clipBounds = false).top
    }

    /** How far down the page its middle is: the middle of what is never covered. */
    fun middle(): Float = (pageHeight() + top) / 2

    /**
     * How far the page has to move for [word] to be where it should: at the middle of
     * the page, or lower than that when a short page (a phone on its side) would
     * otherwise push the line just read off the top. That line stays on the page too, as
     * long as the line being recited still fits under it. In px; null if the word isn't
     * laid out.
     */
    fun offCentre(word: Int): Float? {
        val page = list?.takeIf { it.isAttached } ?: return null
        val tile = tile?.takeIf { this.word == word && it.isAttached } ?: return null
        val now = page.localBoundingBoxOf(tile, clipBounds = false)
        val centred = now.center.y - middle()
        val before = lineBefore?.takeIf { it.isAttached }?.let { page.localBoundingBoxOf(it, clipBounds = false) } ?: return centred
        // Only the line right above counts: after a jump, what was recited before is somewhere else.
        if (before.top >= now.top || now.top - before.bottom > gap) return centred
        if (before.top - centred >= top + margin) return centred
        return maxOf(before.top - top - margin, now.bottom - (page.size.height - margin))
    }
}

@Composable
private fun Verses(
    top: Dp,
    covered: Dp,
    reading: Reading,
    index: WordIndex?,
    list: androidx.compose.foundation.lazy.LazyListState,
    current: Int,
    currentVerse: Int,
    now: WordTiming?,
    nowEnds: Int,
    position: () -> Int,
    showMeanings: Boolean,
    textScale: Float,
    hand: Hand,
    tajweed: Boolean,
    marks: VerseMarks,
    onMark: (Int) -> Unit,
    place: WordPlace,
    fade: () -> Float,
    drift: () -> Float,
    wash: (Int) -> Float,
    translated: Int?,
    onTranslate: (Int) -> Unit,
    recital: PageRecital?,
    firsts: IntArray,
    onPlayFrom: (Int) -> Unit,
    onOnly: ((Int) -> Unit)?,
    onSeek: (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The page lies under the top bar, [top] tall, of which [covered] never slides away.
        // With a recitation, the title page fills the page down to its middle, so the first
        // verse starts there, where the word being recited is kept; and half a page of room
        // below the last verse lets its words reach the middle too.
        val middle = (maxHeight + covered) / 2
        val room = if (index != null) (middle - top - 40.dp).coerceAtLeast(0.dp) else 0.dp
        val below = if (index != null) (maxHeight - middle - 40.dp).coerceAtLeast(0.dp) else 0.dp
        // The basmala is one very wide glyph, 11.5 times as wide as its font size: sized to span the page.
        val basmalaSize = with(LocalDensity.current) { ((maxWidth - 32.dp) / 11.5f).toSp() }
        LazyColumn(
            Modifier.fillMaxSize().testTag("verses").onGloballyPositioned { place.list = it }.graphicsLayer { alpha = fade(); translationY = drift() },
            state = list,
            contentPadding = PaddingValues(top = top, bottom = below + 24.dp),
        ) {
            item { TitlePage(SURAHS[reading.surah - 1], minHeight = room, basmalaSize = basmalaSize) }
            itemsIndexed(reading.verses, contentType = { _, _ -> "verse" }) { v, verse ->
                val first = firsts.getOrElse(v) { 0 }
                VerseBlock(
                    number = v + 1,
                    verse = verse,
                    timings = reading.timings?.verses?.getOrElse(v) { emptyList() }.orEmpty(),
                    // The word of this verse being recited; -1 when the recitation is elsewhere.
                    current = if (currentVerse == v) current - first else -1,
                    now = if (currentVerse == v) now else null,
                    // Only the verse being recited is told when its word ends, so only it is drawn again as the words go by.
                    nowEnds = if (currentVerse == v) nowEnds else Int.MAX_VALUE,
                    playingUnheard = current < 0 && currentVerse == v,
                    position = position,
                    showMeanings = showMeanings,
                    textScale = textScale,
                    hand = hand,
                    // The rules of recitation in each of the verse's words, when their colours are wanted.
                    rules = if (tajweed) reading.rules.getOrNull(v).orEmpty() else emptyList(),
                    // Reciting it to the phone: where that stands if this is the verse, and what was heard of it.
                    listening = recital?.state == Reciting.Listening && recital.verse == v + 1,
                    reached = if (recital?.verse == v + 1) recital.reached else -1,
                    heard = recital?.heard?.get(v + 1),
                    // The letters a correction points at are found by their rules, whether the colours show or not.
                    allRules = if (recital?.heard?.containsKey(v + 1) == true) reading.rules.getOrNull(v).orEmpty() else emptyList(),
                    onForget = { recital?.onForget(v + 1) },
                    pins = marks.pins[v].orEmpty(),
                    saved = v in marks.saved,
                    onMark = { onMark(v) },
                    showTranslation = translated == v,
                    onPlaced = { w, tile -> place.placed(first + w, tile) },
                    wash = { w -> wash(first + w) },
                    reference = verseName(VerseRef(reading.surah, v + 1)),
                    // Only where the recording can be moved to: a verse whose start is known, exactly or roughly.
                    onPlayFrom = if (index?.startOfVerse(v) != null) ({ onPlayFrom(v) }) else null,
                    onOnly = if (onOnly != null && index?.startOfVerse(v) != null) ({ onOnly(v) }) else null,
                    onTap = { onTranslate(v) },
                    // Tapping a word also jumps the recitation to it.
                    onWord = { w ->
                        onTranslate(v)
                        index?.starts?.getOrNull(first + w)?.let(onSeek)
                    },
                )
            }
        }
    }
}

/** A small round button on a verse's rule, no taller than the verse's number. */
@Composable
private fun VerseButton(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Box(
        Modifier.size(VERSE_BUTTON).clip(CircleShape).background(MaterialTheme.colorScheme.background).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(18.dp), tint = tint)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VerseBlock(
    number: Int,
    verse: Verse,
    timings: List<WordTiming>,
    current: Int,
    now: WordTiming?,
    nowEnds: Int,
    playingUnheard: Boolean,
    position: () -> Int,
    showMeanings: Boolean,
    textScale: Float,
    hand: Hand,
    rules: List<List<Mark>>,
    listening: Boolean,
    reached: Int,
    heard: List<WordRecited>?,
    allRules: List<List<Mark>>,
    onForget: () -> Unit,
    pins: List<String>,
    saved: Boolean,
    onMark: () -> Unit,
    showTranslation: Boolean,
    onPlaced: (Int, LayoutCoordinates) -> Unit,
    wash: (Int) -> Float,
    reference: String,
    onPlayFrom: (() -> Unit)?,
    onOnly: (() -> Unit)?,
    onTap: () -> Unit,
    onWord: (Int) -> Unit,
) {
    // What was heard when the reader recited this verse to the phone, by its words; and the
    // word whose corrections show under the verse: the first with any, until another is tapped.
    val byWord = remember(heard, verse.words.size) { byWord(heard, verse.words.size) }
    var picked by remember(heard) { mutableIntStateOf(byWord.entries.filter { !it.value.sound }.minOfOrNull { it.key } ?: -1) }
    val chosen = byWord[picked]?.takeIf { !it.sound && !listening }
    // What stands right under the verse's words, which their blocks then stop short of.
    val under = showTranslation || chosen != null
    // The whole verse answers a tap, quietly: no ripple across a block of scripture. A long
    // press, like a tap on its number, opens its pins and collections.
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                // Said by a screen reader, which otherwise only says that the verse can be tapped.
                onClickLabel = stringResource(if (showTranslation) R.string.verse_translation_hide else R.string.verse_translation_show),
                onLongClickLabel = stringResource(R.string.marks_open),
                onClick = onTap, onLongClick = onMark,
            ),
    ) {
        // The verse's number sits on the rule above it, at the left in every language.
        val label = stringResource(R.string.verse, number)
        val direction = LocalLayoutDirection.current
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            // Drawn over the words' blocks, not under them: a filled block reaches up to the rule,
            // and the star, which is solid, covers the corner of it.
            Row(Modifier.fillMaxWidth().zIndex(1f).padding(start = 12.dp, end = 12.dp, top = ABOVE_RULE), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.width(8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                StarNumber(
                    number,
                    size = STAR,
                    modifier = Modifier.padding(horizontal = 4.dp).clip(CircleShape).clickable(onClickLabel = stringResource(R.string.marks_open), onClick = onMark)
                        .semantics { contentDescription = label },
                )
                // What can be done with the verse, right beside its number: recite from it, keep it
                // (pins and collections; the bookmark is filled once it is in a collection), and more.
                if (onPlayFrom != null) VerseButton(AppIcons.Play, stringResource(R.string.verse_play_from), onClick = onPlayFrom)
                // The verse's exegesis, in the one the reader has chosen (or the choosing of one).
                LocalExplain.current?.let { explain -> VerseButton(AppIcons.Exegesis, stringResource(R.string.verse_exegesis, number)) { explain(number) } }
                VerseButton(
                    if (saved) AppIcons.Bookmark else AppIcons.BookmarkOutline,
                    stringResource(if (saved) R.string.saved_mark else R.string.marks_open),
                    tint = if (saved || pins.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onMark,
                )
                Box {
                    var more by remember { mutableStateOf(false) }
                    VerseButton(Icons.Filled.MoreVert, stringResource(R.string.verse_more, number)) { more = true }
                    // The menu reads the way the page does, not left to right like the rule it hangs from.
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        val context = LocalContext.current
                        val clipboard = LocalClipboardManager.current
                        // The verse as it is written, its translation, and where it is from.
                        val text = listOf(verse.words.joinToString(" ") { it.arabic }, verse.translation, reference).filter { it.isNotEmpty() }.joinToString("\n")
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            if (onOnly != null) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.verse_only)) }, onClick = { more = false; onOnly() })
                            }
                            if (verse.translation.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (showTranslation) R.string.verse_translation_hide else R.string.verse_translation_show)) },
                                    onClick = { more = false; onTap() },
                                )
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.verse_copy)) }, onClick = { more = false; clipboard.setText(AnnotatedString(text)) })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.verse_share)) },
                                onClick = {
                                    more = false
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                    runCatching { context.startActivity(Intent.createChooser(send, null)) }
                                },
                            )
                        }
                    }
                }
                // How the verse went when it was recited to the phone: how many of its words were sound. A tap takes the corrections off the page.
                if (heard != null && !listening) {
                    val sound = byWord.values.count { it.sound }
                    val says = stringResource(R.string.recite_sound_words, sound, byWord.size)
                    Row(
                        Modifier.padding(start = 2.dp).testTag("recited").clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClickLabel = stringResource(R.string.recite_forget), onClick = onForget)
                            // Solid, like the buttons beside it: a marked word's block may lie under it.
                            .background(MaterialTheme.colorScheme.primary.faded(0.12f, MaterialTheme.colorScheme.background)).padding(horizontal = 8.dp, vertical = 3.dp)
                            .semantics(mergeDescendants = true) { contentDescription = says },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(if (sound == byWord.size) AppIcons.Check else AppIcons.Ear, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("$sound/${byWord.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(4.dp))
                // What marks the verse: each pin left on it, by name.
                for (pin in pins) {
                    Row(
                        Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Pin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text(pin, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                    }
                }
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        // Arabic runs from the right, whatever the phone's language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            // Which words are on the verse's first row and which on its last, as the blocks were
            // last laid out: those blocks' fill reaches on to the rule above and the rule below.
            var firstRowEnds by remember { mutableIntStateOf(Int.MAX_VALUE) }
            var lastRowStarts by remember { mutableIntStateOf(0) }
            // The verse being played where none of its words is placed yet: all of it is lit, softly.
            WordBlocks(Modifier.fillMaxWidth().padding(horizontal = EDGE_MARGIN).then(if (playingUnheard) Modifier.testTag("verse-lit") else Modifier), onRows = { firstEnds, lastStarts -> firstRowEnds = firstEnds; lastRowStarts = lastStarts }) {
                verse.words.forEachIndexed { w, word ->
                    // While the reader recites this verse to the phone, the word lit is the one they have reached.
                    val at = if (listening) reached else current
                    val result = byWord[w]?.takeIf { !listening }
                    WordTile(
                        word = word,
                        // The word being recited goes by this saying of it, which may be a second one.
                        // The reader's own word has no timing: its block is lit, not its letters.
                        timing = if (listening) null else if (w == current && now != null) now else timings.getOrNull(w),
                        state = when {
                            at < 0 -> WordState.RESTING
                            w < at -> WordState.RECITED
                            w == at -> WordState.CURRENT
                            else -> WordState.RESTING
                        },
                        // Something to go over in it: its block takes the colour of the rule, and the rule's letters are pointed at.
                        mark = result?.colour(),
                        whole = playingUnheard,
                        picked = w == picked,
                        pointed = result?.corrections?.flatMap { it.rules }?.toSet()?.let { broken -> allRules.getOrNull(w).orEmpty().filter { it.rule in broken } }.orEmpty(),
                        position = position,
                        // The word's last sound runs until the next word begins.
                        endMs = if (w == current && now != null) nowEnds else timings.getOrNull(w + 1)?.startMs ?: Int.MAX_VALUE,
                        showMeaning = showMeanings,
                        textScale = textScale,
                        hand = hand,
                        rules = rules.getOrNull(w).orEmpty(),
                        // Up to this verse's rule, and down to the next verse's, each short of it by a
                        // hair. Not down over what stands under the verse: its translation, or what to go over in it.
                        reachUp = { if (w <= firstRowEnds) RULE_TO_WORDS - RULE_MARGIN else 0.dp },
                        reachDown = { if (w >= lastRowStarts && !under) WORDS_TO_NEXT_RULE - RULE_MARGIN else 0.dp },
                        onPlaced = { onPlaced(w, it) },
                        wash = { wash(w) },
                        // A marked word shows what to go over in it; any other, the verse's translation.
                        onClick = { if (result != null && !result.sound) picked = w else onWord(w) },
                        onLongClick = onMark,
                    )
                }
            }
        }
        // What reciting it to the phone came to: what to go over in the word picked, or why it couldn't be done.
        AnimatedVisibility(visible = chosen != null, enter = fadeIn(tween(260)) + expandVertically(tween(260)), exit = fadeOut(tween(260)) + shrinkVertically(tween(260))) {
            // Kept while it closes, so it doesn't empty first.
            var last by remember { mutableStateOf(chosen) }
            if (chosen != null) last = chosen
            last?.let { CorrectionsCard(it, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag("corrections")) }
        }
        // The verse's translation opens and closes softly rather than popping in.
        AnimatedVisibility(
            visible = showTranslation && verse.translation.isNotEmpty(),
            enter = fadeIn(tween(260)) + expandVertically(tween(260)),
            exit = fadeOut(tween(420)) + shrinkVertically(tween(420)),
        ) {
            Text(
                verse.translation, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                // From the side its own language is written from: a translation may be in any.
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                fontSize = 14.sp * textScale, lineHeight = 20.sp * textScale,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Lays a verse's words out as blocks that take up all of the verse's room between them:
 * rows are filled from the side reading starts from, each row's whole width is shared out
 * among its words (every word gets its own width plus an equal share of what is left
 * over), and every block in a row is as tall as the tallest. The blocks touch: there is no
 * gap between one block's edge and the next, nor between rows. [onRows] is told the last
 * word of the first row and the first word of the last row.
 */
@Composable
private fun WordBlocks(modifier: Modifier = Modifier, onRows: (firstRowEnds: Int, lastRowStarts: Int) -> Unit = { _, _ -> }, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val natural = measurables.map { minOf(it.maxIntrinsicWidth(Constraints.Infinity), width) }
        // As many words to a row as fit at their own widths.
        val rows = ArrayList<IntRange>()
        var first = 0
        var used = 0
        for (i in measurables.indices) {
            if (i > first && used + natural[i] > width) {
                rows += first until i
                first = i
                used = 0
            }
            used += natural[i]
        }
        if (measurables.isNotEmpty()) rows += first until measurables.size
        if (rows.isNotEmpty()) onRows(rows.first().last, rows.last().first)

        val widths = IntArray(measurables.size)
        val heights = IntArray(rows.size)
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        for ((r, row) in rows.withIndex()) {
            val spare = width - row.sumOf { natural[it] }
            val share = spare / row.count()
            var odd = spare - share * row.count()
            for (i in row) widths[i] = natural[i] + share + if (odd-- > 0) 1 else 0
            heights[r] = row.maxOf { measurables[it].maxIntrinsicHeight(widths[it]) }
            for (i in row) placeables[i] = measurables[i].measure(Constraints.fixed(widths[i], heights[r]))
        }
        layout(width, heights.sum()) {
            var y = 0
            for ((r, row) in rows.withIndex()) {
                var x = 0
                for (i in row) {
                    placeables[i]?.placeRelative(x, y)
                    x += widths[i]
                }
                y += heights[r]
            }
        }
    }
}

/** The star a verse's number sits in, on the rule above the verse; the rule runs through its middle. */
private val STAR = 38.dp

/** The buttons beside a verse's number: as big as fits on the rule without moving the verses apart. */
private val VERSE_BUTTON = 36.dp

/** The room above a verse's rule. */
private val ABOVE_RULE = 6.dp

/** From a verse's rule down to the top of its first row of words: the lower half of the star. */
private val RULE_TO_WORDS = STAR / 2

/** From the bottom of a verse's last row of words down to the next verse's rule. */
private val WORDS_TO_NEXT_RULE = ABOVE_RULE + STAR / 2

/** How far short of the screen's edges the word blocks stop. */
private val EDGE_MARGIN = 6.dp

/** How far short of a rule the filled block stops. */
private val RULE_MARGIN = 3.dp

private enum class WordState { RESTING, RECITED, CURRENT }

/** How long the light takes to pass from one letter to the next, in ms. */
private const val GLIDE_MS = 130f

/** How eagerly the page follows the word: higher is quicker. At 8 it settles in about two thirds of a second. */
private const val STIFFNESS = 8f

/** How long a translation stays up, in ms: time to read it, between 6 and 25 seconds. */
internal fun readingTime(translation: String): Long = (3_000L + 70L * translation.length).coerceIn(6_000L, 25_000L)

/**
 * A stream along the foot of a word's block, flowing back the way the words are read
 * from: for a passage the reciter is saying again. Two swells, one over the other, the
 * nearer the slower. [left] is where the block stands in its row, so the swells run on
 * from one block to the next; [ms] is where the recitation is, which is what moves them;
 * [bottom] is the block's foot. [path] is the tile's own, used afresh each time.
 */
private fun DrawScope.stream(path: Path, color: Color, strength: Float, left: Float, ms: Int, bottom: Float) {
    val back = if (layoutDirection == LayoutDirection.Rtl) 1f else -1f
    val turn = ms / STREAM_MS * 2 * PI.toFloat()
    val step = 2.dp.toPx()
    for (layer in 0..1) {
        val long = (if (layer == 0) 64.dp else 44.dp).toPx()
        val deep = (if (layer == 0) 12.dp else 7.dp).toPx()
        val swell = (if (layer == 0) 3.2.dp else 2.4.dp).toPx()
        path.rewind()
        path.moveTo(0f, bottom)
        var x = 0f
        while (true) {
            path.lineTo(x, bottom - deep + swell * sin((left + x) / long * 2 * PI.toFloat() - back * turn * (1f + 0.5f * layer) + 2.1f * layer))
            if (x >= size.width) break
            x = minOf(x + step, size.width)
        }
        path.lineTo(size.width, bottom)
        path.close()
        drawPath(path, color.copy(alpha = (if (layer == 0) 0.15f else 0.24f) * strength))
    }
}

/**
 * The pool of light behind the letter being uttered. A letter under a rule of recitation
 * gets the rule's motion: sound held through the nose hums, a long vowel spreads for as
 * long as it is held, an echoing letter lets a ring go as it ends, and a letter that isn't
 * sounded gets no pool at all. Only the pool moves: the letter itself never changes size.
 */
private fun DrawScope.pool(family: Family?, color: Color, center: Offset, uttered: Uttered, lit: Float) {
    val radius = 24.dp.toPx()
    when (family) {
        Family.QUIET -> {}
        Family.NASAL -> {
            val hum = sin(uttered.sinceMs / 1000f * 2f * PI.toFloat() * 1.7f) * 0.5f + 0.5f
            spot(color.copy(alpha = (0.2f + 0.22f * hum) * lit), center, radius * (0.9f + 0.2f * hum), flatten = 0.95f)
        }
        Family.MADD_LAZIM, Family.MADD_WAJIB, Family.MADD_JAIZ, Family.MADD_TABII ->
            spot(color.copy(alpha = 0.3f * lit), center, radius, flatten = 0.95f, widen = 1f + 1.6f * uttered.fraction)
        // A full-mouthed letter: a fuller, rounder light.
        Family.HEAVY -> spot(color.copy(alpha = 0.34f * lit), center, radius * 1.25f, flatten = 1.1f)
        Family.ECHO -> {
            spot(color.copy(alpha = 0.26f * lit), center, radius, flatten = 0.95f)
            val out = (uttered.fraction - 0.5f) / 0.5f
            if (out > 0f && out < 1f) {
                drawCircle(color.copy(alpha = (1f - out) * 0.8f * lit), radius = 8.dp.toPx() + 22.dp.toPx() * out, center = center, style = Stroke(2.dp.toPx()))
            }
        }
        null -> spot(color.copy(alpha = 0.26f * lit), center, radius, flatten = 0.95f)
    }
}

/** A soft round light that fades to nothing at its edge, squashed to [flatten] of its height and stretched to [widen] times its width. */
private fun DrawScope.spot(color: Color, center: Offset, radius: Float, flatten: Float, widen: Float = 1f) {
    scale(scaleX = widen, scaleY = flatten, pivot = center) {
        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius), radius = radius, center = center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordTile(
    word: Word,
    timing: WordTiming?,
    state: WordState,
    position: () -> Int,
    endMs: Int,
    showMeaning: Boolean,
    textScale: Float,
    hand: Hand,
    rules: List<Mark>,
    mark: Color?,
    whole: Boolean,
    picked: Boolean,
    pointed: List<Mark>,
    reachUp: () -> Dp,
    reachDown: () -> Dp,
    onPlaced: (LayoutCoordinates) -> Unit,
    wash: () -> Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val water = remember { Path() }
    var left by remember { mutableFloatStateOf(0f) }
    val written = word.shownIn(hand.script)
    val isCurrent = state == WordState.CURRENT
    // Nothing switches on or off: the block's fill and the light fade from one word to the
    // next, and a word dims gently once it has been recited.
    val lit by animateFloatAsState(if (isCurrent) 1f else 0f, tween(if (isCurrent) 220 else 420), label = "lit")
    // Its verse is being recited but its words aren't placed yet: every block of the verse is lit, more softly than a word is.
    val soft by animateFloatAsState(if (whole) 1f else 0f, tween(420), label = "soft")
    val ink by animateColorAsState(
        // Solid, not see-through: see-through ink shows dark patches where the letters join.
        if (state == WordState.RECITED) colors.onSurface.faded(0.45f, colors.background) else colors.onSurface, tween(420), label = "ink",
    )
    Column(
        // The word's block is as large as the verse gives it (see WordBlocks). The block of the
        // word being recited is filled, its meaning included, with no frame round it, and
        // tells the page where it is so the page can keep it in the middle. The space inside
        // the block is the word's own: it is what keeps neighbouring words apart.
        (if (isCurrent) Modifier.onGloballyPositioned(onPlaced) else Modifier)
            // On the verse's first and last rows the fill carries on past the block, to the
            // rules above and below the verse, so the block stands the full height between them.
            .then(
                if (lit > 0f) {
                    Modifier.drawBehind {
                        val up = reachUp().toPx()
                        drawRect(colors.primary.copy(alpha = 0.2f * lit), Offset(0f, -up), Size(size.width, size.height + up + reachDown().toPx()))
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (soft > 0f) {
                    Modifier.drawBehind {
                        val up = reachUp().toPx()
                        drawRect(colors.primary.copy(alpha = 0.11f * soft), Offset(0f, -up), Size(size.width, size.height + up + reachDown().toPx()))
                    }
                } else {
                    Modifier
                },
            )
            // Something to go over in it, from reciting the verse to the phone: the block in the
            // colour of what is to go over, stronger on the word whose corrections are showing.
            .then(
                if (mark != null) {
                    Modifier.drawBehind {
                        val up = reachUp().toPx()
                        drawRect(mark.copy(alpha = if (picked) 0.26f else 0.13f), Offset(0f, -up), Size(size.width, size.height + up + reachDown().toPx()))
                    }.testTag(if (picked) "marked-word-picked" else "marked-word")
                } else {
                    Modifier
                },
            )
            // A passage being said again: its blocks tinted, more faintly than the word being
            // uttered, and a stream flowing back along their foot. Read as it is drawn, so
            // nothing is laid out again while it flows. Where the block stands in its row is
            // kept, so the stream runs on unbroken from one block to the next.
            .onPlaced { left = it.positionInParent().x }
            .drawBehind {
                val strength = wash()
                if (strength > 0f) {
                    val up = reachUp().toPx()
                    val tall = size.height + up + reachDown().toPx()
                    drawRect(colors.primary.copy(alpha = 0.07f * strength), Offset(0f, -up), Size(size.width, tall))
                    stream(water, colors.primary, strength, left, position(), tall - up)
                }
            }
            .combinedClickable(
                onClickLabel = stringResource(R.string.word_tap), onLongClickLabel = stringResource(R.string.marks_open),
                onClick = onClick, onLongClick = onLongClick,
            )
            .padding(horizontal = (if (showMeaning) 8.dp else 14.dp) * textScale, vertical = 3.dp * textScale),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The letters themselves are never recoloured: recolouring part of a joined-up word
        // makes Android shape it again, and the letters visibly shift. Colour is painted over
        // them instead: the rules of recitation once, and the letter being uttered afresh every
        // frame, with nothing laid out again.
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        val amber = LocalHighlight.current
        val tajweed = LocalTajweed.current
        val context = LocalContext.current
        val letterSize = with(LocalDensity.current) { (hand.size * textScale).toPx() }
        val spelling = remember(word, hand.script) { Spelling(word.arabic, written) }
        // Single letters and signs can be painted only where the shown spelling has the Uthmani
        // one's letters and its font is at hand; otherwise the light falls on the letter's width.
        val repaint = remember(written, hand.script, letterSize, spelling) {
            if (spelling.exact) Typefaces.of(context, hand.script)?.let { Repaint(written, it, letterSize) } else null
        }
        val coloured = if (repaint != null) rules else emptyList()
        // A word that has been recited dims, and its colours with it.
        val strength by animateFloatAsState(if (state == WordState.RECITED) 0.45f else 1f, tween(420), label = "colours")
        Text(
            written, fontFamily = hand.font, fontSize = hand.size * textScale, lineHeight = hand.line * textScale, color = ink,
            onTextLayout = { layout = it },
            modifier = if (coloured.isEmpty() && pointed.isEmpty() && (lit == 0f || timing == null)) Modifier else Modifier.drawWithContent {
                val placed = layout
                val uttered = if (lit > 0f && timing != null) timing.utteredAt(position(), endMs) else null
                if (placed == null || (repaint == null && uttered == null && pointed.isEmpty())) {
                    drawContent()
                    return@drawWithContent
                }
                val edge = placed.getLineRight(0)
                val baseline = placed.firstBaseline
                val room = 12.dp.toPx()
                val box = android.graphics.RectF(-room, -room, size.width + room, size.height + room)
                // Where characters of the shown word lie across it, as the whole letters they belong
                // to: a vowel sign lights the letter it sits on.
                fun letters(from: Int, to: Int): IntRange {
                    var first = from.coerceIn(0, written.length)
                    while (first > 0 && first < written.length && written[first].isMark()) first--
                    var end = to.coerceIn(first, written.length)
                    while (end < written.length && written[end].isMark()) end++
                    if (end == first && end < written.length) end++
                    return first until end
                }
                fun across(range: IntRange): Pair<Float, Float> {
                    val end = range.last + 1
                    if (repaint != null) return (edge - repaint.reach(end)) to (edge - repaint.reach(range.first))
                    val a = placed.getHorizontalPosition(range.first, usePrimaryDirection = true)
                    val b = placed.getHorizontalPosition(end, usePrimaryDirection = true)
                    return minOf(a, b) to maxOf(a, b)
                }
                // Without exact places, the Uthmani place is taken in proportion to the shown word's length.
                val rough = written.length.toFloat() / word.arabic.length.coerceAtLeast(1)
                fun shown(index: Int) = if (repaint != null) spelling.at(index) else (index * rough).roundToInt()

                if (uttered == null) {
                    drawContent()
                    drawIntoCanvas { canvas ->
                        for (rule in coloured) {
                            repaint?.draw(canvas.nativeCanvas, shown(rule.from), shown(rule.to), edge, baseline, box, tajweed.ink(rule.rule.family).faded(strength, colors.background))
                        }
                    }
                    // The letters of a rule the reader is to go over: a line under them in the rule's colour.
                    for (rule in pointed) {
                        val (left, right) = across(letters(shown(rule.from), shown(rule.to)))
                        val y = size.height - 2.dp.toPx()
                        drawLine(tajweed.ink(rule.rule.family), Offset(left, y), Offset(maxOf(right, left + 6.dp.toPx()), y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    }
                    return@drawWithContent
                }

                val from = shown(uttered.from)
                val to = shown(uttered.to)
                // A stretch that is only signs: the vowel of the letter just uttered.
                val signs = repaint != null && repaint.signsApart && to > from && (from until to).all { written[it].isMark() }
                // The rule the stretch falls under gives the light its colour and its motion.
                val family = coloured.firstOrNull { it.from < uttered.to && uttered.from < it.to }?.rule?.family
                val glow = family?.let(tajweed::light) ?: amber
                val now = letters(from, to)
                val was = if (uttered.before == uttered.from) now else letters(shown(uttered.before), from)
                val moved = (uttered.sinceMs / GLIDE_MS).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
                val (nowLeft, nowRight) = across(now)
                val (wasLeft, wasRight) = across(was)
                var left = wasLeft + (nowLeft - wasLeft) * moved
                var right = wasRight + (nowRight - wasRight) * moved
                // A hairline of a letter still gets a light wide enough to see.
                val least = 9.dp.toPx()
                if (right - left < least) {
                    val middle = (left + right) / 2
                    left = middle - least / 2
                    right = middle + least / 2
                }
                // Full strength over the body of the letter, easing off across each end, where it joins its neighbours.
                val ease = ((right - left) * 0.45f).coerceIn(3.dp.toPx(), 10.dp.toPx())
                val whole = right - left + ease
                // A soft pool of light behind the letter, moving as its rule asks; then the word; then the letter itself lit up.
                pool(family, glow, Offset((left + right) / 2, baseline - 8.dp.toPx()), uttered, lit)
                drawContent()
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    for (rule in coloured) {
                        repaint?.draw(native, shown(rule.from), shown(rule.to), edge, baseline, box, tajweed.ink(rule.rule.family).faded(strength, colors.background))
                    }
                    // The letter is painted once more in the light's colour, exactly over itself: it
                    // isn't laid out again and gets no bloom, so it neither moves nor looks any bigger.
                    // While its vowel sign is being uttered the letter stays half lit under it.
                    val margin = room
                    canvas.saveLayer(Rect(-margin, -margin, size.width + margin, size.height + margin), Paint())
                    if (repaint != null) {
                        if (moved < 1f && was != now) repaint.draw(native, was.first, was.last + 1, edge, baseline, box, glow)
                        repaint.draw(native, now.first, now.last + 1, edge, baseline, box, glow)
                    } else {
                        drawText(placed, color = glow)
                    }
                    val full = lit * if (signs) 0.5f else 1f
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent, ease / whole to Color.Black.copy(alpha = full),
                            1f - ease / whole to Color.Black.copy(alpha = full), 1f to Color.Transparent,
                            startX = left - ease / 2, endX = right + ease / 2,
                        ),
                        topLeft = Offset(-margin, -margin), size = Size(size.width + 2 * margin, size.height + 2 * margin),
                        blendMode = BlendMode.DstIn,
                    )
                    canvas.restore()
                    // The vowel sign itself, with a small light of its own round it.
                    if (signs) repaint?.draw(native, from, to, edge, baseline, box, glow.copy(alpha = lit * moved), glow = 1.6.dp.toPx())
                }
            },
        )
        if (showMeaning) {
            val meaningInk by animateColorAsState(
                when (state) {
                    WordState.RESTING -> colors.onSurfaceVariant
                    WordState.RECITED -> colors.onSurfaceVariant.faded(0.6f, colors.background)
                    WordState.CURRENT -> colors.primary
                },
                tween(320), label = "meaning",
            )
            Text(
                word.meaning, Modifier.widthIn(max = 132.dp * textScale).padding(bottom = 4.dp), textAlign = TextAlign.Center,
                style = TextStyle(fontSize = 12.sp * textScale, lineHeight = 15.sp * textScale, textDirection = TextDirection.Ltr),
                color = meaningInk,
            )
        }
    }
}

/**
 * The player, on the bottom bar, in one slim row: previous verse, play, next verse, and
 * the time line with the two times under it. Between the times, in pictures: what is being
 * done to this recording and how fast the listening goes, or how far it got before it was
 * left off; and the verses chosen to be recited, when that isn't the whole surah ([range]).
 * What is being done to other surahs isn't in it: that stands in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerBar(
    placedUntil: Int?,
    listening: Boolean,
    heard: List<IntRange>?,
    speed: Float,
    waiting: Boolean,
    surah: Int,
    listeningAt: Int,
    verseStarts: List<Int>,
    range: IntRange?,
    onChooseRange: () -> Unit,
    playing: Boolean,
    position: () -> Int,
    duration: Int,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    // Four steps a second is plenty for a slider and a clock.
    val shown by remember { derivedStateOf { position() / 250 * 250 } }
    var dragged by remember { mutableStateOf<Float?>(null) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val band = LocalBanner.current
    val doing = LocalDoing.current
    val here = (doing?.status as? Status.Working)?.takeIf { it.surah == surah }
    BottomBand {
        Row(Modifier.padding(start = 2.dp, end = 10.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            // Previous verse on the side reading starts from, next verse on the other; the arrows turn round with them.
            IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                Icon(AppIcons.SkipPrevious, stringResource(R.string.previous_verse), Modifier.scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f), tint = band.content)
            }
            PlayButton(playing, onPlayPause)
            IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                Icon(AppIcons.SkipNext, stringResource(R.string.next_verse), Modifier.scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f), tint = band.content)
            }
            Spacer(Modifier.width(6.dp))
            // The time line runs left to right in every language, as on other players: a thin
            // line with a small bead on it, and under it the time gone and the whole time.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(Modifier.weight(1f)) {
                    val line = SliderDefaults.colors(
                        thumbColor = band.content, activeTrackColor = band.content,
                        inactiveTrackColor = band.content.faded(0.25f, band.background),
                    )
                    Slider(
                        value = dragged ?: if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { dragged = it },
                        onValueChangeFinished = {
                            dragged?.let { onSeek((it * duration).toInt()) }
                            dragged = null
                        },
                        modifier = Modifier.fillMaxWidth().height(22.dp),
                        colors = line,
                        // The bead is drawn with the line, round the line's own middle, so it can't sit
                        // above or below it; the slider only keeps the room for it. (Left to the slider
                        // to place, it came out a little above the line.)
                        thumb = { Spacer(Modifier.size(BEAD)) },
                        track = { state ->
                            // Grey where the recording hasn't been heard yet; the band's colour where it
                            // has, faint ahead of the bead and full behind it. The line is cut where
                            // each verse begins, so it reads verse by verse.
                            val ready = band.content.faded(0.5f, band.background)
                            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                                val played = state.value.coerceIn(0f, 1f) * size.width
                                drawRect(UNHEARD, size = size)
                                val ranges = heard?.takeIf { duration > 0 } ?: listOf(0..maxOf(duration, 1))
                                for (range in ranges) {
                                    val from = (range.first.toFloat() / maxOf(duration, 1)).coerceIn(0f, 1f) * size.width
                                    val to = (range.last.toFloat() / maxOf(duration, 1)).coerceIn(0f, 1f) * size.width
                                    if (to <= from) continue
                                    drawRect(ready, Offset(from, 0f), Size(to - from, size.height))
                                    if (played > from) drawRect(band.content, Offset(from, 0f), Size(minOf(to, played) - from, size.height))
                                }
                                // Verses too short to tell apart on the line share a piece of it.
                                var cut = 0f
                                for (start in verseStarts) {
                                    val x = start.toFloat() / maxOf(duration, 1) * size.width
                                    if (x - cut < NARROWEST.toPx() || size.width - x < NARROWEST.toPx()) continue
                                    drawRect(band.background, Offset(x - CUT.toPx() / 2, 0f), Size(CUT.toPx(), size.height))
                                    cut = x
                                }
                                // Where the listening is right now: a small mark standing on the line.
                                if (listeningAt in 0..duration && duration > 0) {
                                    val x = listeningAt.toFloat() / duration * size.width
                                    drawRect(band.content, Offset(x - 1.dp.toPx(), -5.dp.toPx()), Size(2.dp.toPx(), size.height + 5.dp.toPx()))
                                    drawCircle(band.content, 2.5.dp.toPx(), Offset(x, -5.dp.toPx()))
                                }
                                drawCircle(band.content, BEAD.toPx() / 2, Offset(played, size.height / 2))
                            }
                        },
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val small = MaterialTheme.typography.labelSmall
                        Text(clock(dragged?.let { (it * duration).toInt() } ?: shown), style = small, color = band.quiet)
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                            // Which verses will be recited, when not all of them; tap to choose again.
                            if (range != null) {
                                val label = stringResource(R.string.range_label, range.first + 1, range.last + 1)
                                Row(
                                    Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onChooseRange).padding(horizontal = 4.dp).clearAndSetSemantics { contentDescription = label },
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(AppIcons.Range, null, Modifier.size(13.dp), tint = band.content)
                                    Text(stringResource(R.string.range_short, range.first + 1, range.last + 1), style = small, color = band.content, maxLines = 1)
                                }
                            }
                            // The line above is grey where the recording hasn't been heard yet. Here, in
                            // pictures: what is being done to it, how fast the listening goes, or how far it got.
                            when {
                                // A verse that hasn't been heard was asked for: the recitation waits for the listening to get there.
                                waiting -> WorkIcon(Picture(AppIcons.Ear), stringResource(R.string.listening_first), size = 16.dp, color = band.content, ground = band.background)
                                placedUntil == null -> {}
                                here != null -> {
                                    WorkIcon(here.picture(), here.says(), size = 16.dp, color = band.content, ground = band.background)
                                    if (here.progress.speed > 0f) Text(times(here.progress.speed), style = small, color = band.quiet)
                                }
                                listening && speed > 0f -> {
                                    Icon(AppIcons.Ear, stringResource(R.string.listening_at_speed, String.format(java.util.Locale.getDefault(), "%.1f", speed)), Modifier.size(14.dp), tint = band.content)
                                    Text(times(speed), style = small, color = band.quiet)
                                }
                                // Waiting its turn: what the work is on meanwhile is in the top bar.
                                listening -> Icon(AppIcons.Hourglass, stringResource(R.string.queued), Modifier.size(13.dp), tint = band.quiet)
                                else -> {
                                    val says = if (placedUntil > 0) stringResource(R.string.listening_stopped, clock(placedUntil)) else stringResource(R.string.listening_stopped_none)
                                    Icon(AppIcons.SoundPart, says, Modifier.size(14.dp), tint = band.quiet)
                                    if (placedUntil > 0) Text(clock(placedUntil), style = small, color = band.quiet)
                                }
                            }
                        }
                        Text(clock(duration), style = small, color = band.quiet)
                    }
                }
            }
        }
    }
}

/**
 * The play button: a disc of the bar's accent inside a thin ring of it. The ring and the disc
 * are drawn together, round one centre, so they can't sit askew of each other. (As a ring
 * drawn round a separate button they came out a pixel or two off on some screens, because
 * each was rounded to whole pixels on its own.)
 */
@Composable
internal fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val band = LocalBanner.current
    Box(
        Modifier.padding(horizontal = 4.dp).size(44.dp).clip(CircleShape)
            .drawBehind {
                val ring = 1.2.dp.toPx()
                drawCircle(band.accent, radius = size.minDimension / 2 - ring / 2, style = Stroke(ring))
                drawCircle(band.accent, radius = size.minDimension / 2 - 4.dp.toPx())
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (playing) {
            Icon(AppIcons.Pause, stringResource(R.string.pause), tint = band.onAccent)
        } else {
            Icon(AppIcons.Play, stringResource(R.string.play), tint = band.onAccent)
        }
    }
}

/**
 * The surah's title page, above its first verse: between two hairlines that run nearly
 * from edge to edge, its name in calligraphy, what the name means, how long it is and
 * where it was revealed; and then the basmala drawn as one piece. It is at least
 * [minHeight] tall.
 */
@Composable
private fun TitlePage(surah: Surah, minHeight: Dp, basmalaSize: TextUnit) {
    val colors = MaterialTheme.colorScheme
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    Column(
        Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        // The rules are as wide as the rules between the verses below, so the page lines up.
        HeadingRule(Modifier.fillMaxWidth().padding(horizontal = 8.dp))
        SurahTitle(surah, 54.sp, colors.primary, Modifier.padding(top = 12.dp, bottom = 10.dp), withWord = true)
        if (!arabic) {
            Text(stringResource(R.string.name_and_meaning, stringResource(R.string.surah_chip, surah.number, surah.latin), surah.meaning), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Text(
            stringResource(
                R.string.surah_details,
                pluralStringResource(R.plurals.verses, surah.verses, surah.verses),
                stringResource(if (surah.madinan) R.string.revealed_madinah else R.string.revealed_makkah),
                surah.revealed,
            ),
            Modifier.padding(top = 4.dp, bottom = 16.dp),
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        HeadingRule(Modifier.fillMaxWidth().padding(horizontal = 8.dp))
        // A surah opens with the basmala: all but At-Tawbah, which has none, and Al-Fatihah,
        // whose first verse it is, so that it isn't on the page twice.
        if (surah.number != 9 && surah.number != 1) {
            Text(
                "\uFDFD", Modifier.fillMaxWidth().padding(top = 18.dp).semantics { contentDescription = Recital.BASMALA },
                textAlign = TextAlign.Center, fontFamily = QuranFont, fontSize = basmalaSize, lineHeight = basmalaSize * 2,
                maxLines = 1, softWrap = false, color = colors.primary,
            )
        }
    }
}

/** The play bar's line where the recording hasn't been listened to yet. */
private val UNHEARD = Color(0xFF8C8C8C).copy(alpha = 0.5f)

/** How long the stream under a passage said again takes to come up and to fade, and how long one of its swells takes to pass a place, in ms. */
private const val STREAM_IN_MS = 300f
private const val STREAM_OUT_MS = 400f
private const val STREAM_MS = 1_800f

/** How wide the bead on the play line is. */
private val BEAD = 12.dp

/** The gap in the play line where a verse begins, and the shortest piece of line left between two gaps. */
private val CUT = 1.5.dp
private val NARROWEST = 5.dp

/** The sizes the verses can be drawn at, as multiples of the usual size. */
val TEXT_SCALES = 0.7f..2.0f

/** The font a script is drawn in, and how large, so that the scripts come out about the same size on the page. */
private class Hand(val script: Script, val font: FontFamily, val size: TextUnit, val line: TextUnit)

private fun hand(script: Script, indopak: FontFamily?): Hand = when (script) {
    Script.MADINAH -> Hand(script, MadinahFont, 27.sp, 50.sp)
    Script.AMIRI -> Hand(script, QuranFont, 26.sp, 46.sp)
    Script.INDOPAK -> Hand(script, indopak ?: QuranFont, 27.sp, 54.sp)
    Script.EVERYDAY -> Hand(script, QuranFont, 26.sp, 46.sp)
}

/** What marks the verses of the open surah: the names of the pins on each verse (counted from 0), and the verses kept in a collection. */
class VerseMarks(val pins: Map<Int, List<String>> = emptyMap(), val saved: Set<Int> = emptySet())

/** Smaller, the size as a percentage, larger. */
@Composable
fun TextSizeControls(scale: Float, onScale: (Float) -> Unit) {
    // Left to right in every language: smaller on the left, larger on the right.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onScale(step(scale, -1)) }, enabled = scale > TEXT_SCALES.start + 0.01f) {
                Icon(AppIcons.Remove, stringResource(R.string.smaller))
            }
            Text(
                stringResource(R.string.percent, (scale * 100).roundToInt()), Modifier.width(64.dp),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { onScale(step(scale, +1)) }, enabled = scale < TEXT_SCALES.endInclusive - 0.01f) {
                Icon(AppIcons.Add, stringResource(R.string.larger))
            }
        }
    }
}

/** One step smaller or larger: a tenth of the usual size, kept within [TEXT_SCALES]. */
private fun step(scale: Float, direction: Int): Float =
    (((scale * 10).roundToInt() + direction) / 10f).coerceIn(TEXT_SCALES.start, TEXT_SCALES.endInclusive)

/** Chooses the first and last verse to recite. Verses are counted from 0 in [range], shown from 1. */
@Composable
private fun RangeDialog(verses: Int, range: IntRange, onDone: (IntRange) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(range) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.range_title)) },
        text = { RangeChooser(verses, chosen) { chosen = it } },
        confirmButton = { TextButton(onClick = { onDone(chosen) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = { onDone(0..verses - 1) }) { Text(stringResource(R.string.range_whole)) } },
    )
}

/** The inside of [RangeDialog]: a stepper for each end, and for long surahs a bar to drag either end along. */
@Composable
fun RangeChooser(verses: Int, range: IntRange, onChange: (IntRange) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VerseStepper(stringResource(R.string.range_from), range.first, 0..range.last) { onChange(it..range.last) }
        VerseStepper(stringResource(R.string.range_to), range.last, range.first..verses - 1) { onChange(range.first..it) }
        if (verses > 2) {
            // Drag either end, then fine-tune with the buttons above.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                RangeSlider(
                    value = range.first.toFloat()..range.last.toFloat(),
                    onValueChange = {
                        val first = it.start.roundToInt().coerceIn(0, verses - 1)
                        val moved = first..it.endInclusive.roundToInt().coerceIn(first, verses - 1)
                        if (moved != range) onChange(moved)
                    },
                    valueRange = 0f..(verses - 1).toFloat(),
                )
            }
        }
    }
}

@Composable
private fun VerseStepper(label: String, verse: Int, allowed: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { onChange(verse - 1) }, enabled = verse > allowed.first) { Icon(AppIcons.Remove, stringResource(R.string.smaller)) }
        Text(stringResource(R.string.number, verse + 1), Modifier.width(44.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(verse + 1) }, enabled = verse < allowed.last) { Icon(AppIcons.Add, stringResource(R.string.larger)) }
    }
}

/** Offers to go back to where the recitation was left. [verse] is the verse it had reached. */
@Composable
fun LastReadBar(verse: Int, onGo: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.last_read_at, verse), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        ThemeButton(stringResource(R.string.move_to_last_read), onGo, icon = AppIcons.Bookmark, filled = true)
        IconButton(onClick = onDismiss) { Icon(AppIcons.Close, stringResource(R.string.close)) }
    }
}

/**
 * What sits where the player would be when a surah is open without a recording: a line
 * saying how its recitation stands, and a button when there is something to do about it.
 */
@Composable
fun NoRecitationBar(text: String, action: String? = null, icon: ImageVector? = null, onAction: () -> Unit = {}) {
    BottomBand {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            DoingChip()
            if (action != null) {
                Spacer(Modifier.width(12.dp))
                ThemeButton(action, onAction, icon = icon, filled = true, onBand = true)
            }
        }
    }
}

/**
 * The same bar while the surah's recitation is being got ready or is waiting for that: in
 * pictures, not words. [status] is what the app is at; [here] is whether that is this
 * surah's recording. When it isn't, this one waits its turn; what is being done
 * meanwhile stands in the top bar.
 */
@Composable
fun RecitationComingBar(status: Status?, here: Boolean) {
    val band = LocalBanner.current
    BottomBand {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val working = (status as? Status.Working)?.takeIf { here }
            if (working != null) {
                WorkIcon(working.picture(), working.says(), size = 36.dp, color = band.content, ground = band.background)
                Stages(working.progress.step, color = band.accent, onColor = band.onAccent, faint = band.content.faded(0.45f, band.background))
                if (working.progress.speed > 0f) Text(times(working.progress.speed), style = MaterialTheme.typography.labelMedium, color = band.quiet)
                Spacer(Modifier.weight(1f))
            } else {
                Icon(AppIcons.Hourglass, stringResource(if (status == Status.Paused) R.string.paused else R.string.queued), Modifier.size(24.dp), tint = band.content)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * The bar while the reader recites the surah to the phone, in the player's place: the
 * microphone in its turning ring, the verse they are heard to be at, how many of the words
 * gone over so far were sound, and the button that says they are done. When they are, the
 * ear in its ring while the last verse is gone over. What couldn't be done is said here too.
 */
@Composable
fun ReciteBar(recital: PageRecital) {
    val band = LocalBanner.current
    BottomBand {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("recite-bar"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val state = recital.state) {
                is Reciting.Failed -> {
                    Icon(AppIcons.Warning, null, Modifier.size(24.dp), tint = band.content)
                    Text(state.message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = band.content)
                    IconButton(onClick = recital.onToggle) { Icon(AppIcons.Close, stringResource(R.string.cancel), tint = band.content) }
                }
                is Reciting.Fetching -> {
                    val known = state.fraction.takeIf { it >= 0f }
                    WorkIcon(Picture(AppIcons.Mic, AppIcons.Download, known), stringResource(state.what, ((known ?: 0f) * 100).toInt()), size = 36.dp, color = band.content, ground = band.background)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = recital.onToggle) { Icon(AppIcons.Close, stringResource(R.string.cancel), tint = band.content) }
                }
                else -> {
                    val checking = state == Reciting.Checking
                    WorkIcon(
                        Picture(if (checking) AppIcons.Ear else AppIcons.Mic), stringResource(if (checking) R.string.recite_checking else R.string.recite_listening),
                        size = 36.dp, color = band.content, ground = band.background,
                    )
                    // The verse the reader is heard to be at, once that is known.
                    if (recital.verse > 0) {
                        val label = stringResource(R.string.verse, recital.verse)
                        StarNumber(recital.verse, Modifier.testTag("recite-verse").semantics { contentDescription = label }, size = 34.dp, color = band.content, ground = band.background)
                    }
                    // How the verses gone over so far went: how many of their words were sound.
                    val words = recital.heard.values.flatMap { byWord(it, (it.maxOfOrNull { word -> word.index } ?: -1) + 1).values }
                    if (words.isNotEmpty()) {
                        val sound = words.count { it.sound }
                        val says = stringResource(R.string.recite_sound_words, sound, words.size)
                        Row(
                            Modifier.testTag("recite-tally").semantics(mergeDescendants = true) { contentDescription = says },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(if (sound == words.size) AppIcons.Check else AppIcons.Ear, null, Modifier.size(16.dp), tint = band.content)
                            Text("$sound/${words.size}", style = MaterialTheme.typography.labelLarge, color = band.content, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!checking) {
                        val label = stringResource(R.string.recite_stop)
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(band.accent).clickable(role = Role.Button, onClickLabel = label, onClick = recital.onToggle).testTag("recite-stop"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(AppIcons.Stop, label, Modifier.size(20.dp), tint = band.onAccent)
                        }
                    }
                }
            }
        }
    }
}

/** While a surah's words load, or when they couldn't be fetched ([failed]). */
@Composable
fun ReaderLoading(failed: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Banner {
            IconButton(onClick = onBack, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
        }
        if (failed) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.surah_failed), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                ThemeButton(stringResource(R.string.try_again), onRetry, icon = AppIcons.Retry)
            }
        } else {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

/** 75_000 → "1:15", 3_725_000 → "1:02:05". */
fun clock(ms: Int): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

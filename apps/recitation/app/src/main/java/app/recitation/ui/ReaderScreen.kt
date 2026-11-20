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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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

    // While the recording is still being listened to, words are placed only this far.
    private val placedUntil = if (timings.complete) Int.MAX_VALUE else timings.heardMs

    /** Which saying of a word is under way at [ms]: the last to have started; -1 before the first, or past where words have been placed. */
    fun said(ms: Int): Int {
        if (ms > placedUntil) return -1
        var lo = 0
        var hi = saidAt.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (saidAt[mid] <= ms) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }

    /** The word being said at [ms]; -1 as for [said]. */
    fun at(ms: Int): Int = saidWord.getOrElse(said(ms)) { -1 }

    /** The timing of that saying of the word, and when the next thing is said. */
    fun timing(said: Int): WordTiming? = saidTiming.getOrNull(said)
    fun ends(said: Int): Int = saidAt.getOrElse(said + 1) { Int.MAX_VALUE }

    fun verse(word: Int): Int = verseOf.getOrElse(word) { -1 }

    /** When [verse] starts, or null if the listening hasn't reached it yet. */
    fun startOfVerse(verse: Int): Int? = firstOfVerse.getOrNull(verse)?.let(starts::getOrNull)
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
    onScript: (Script) -> Unit = {},
    indopak: FontFamily? = null,
    startVerse: Int? = null,
    marks: VerseMarks = VerseMarks(),
    onMark: (Int) -> Unit = {},
    appearance: Appearance = Appearance.AUTO,
    onAppearance: (Appearance) -> Unit = {},
    tajweed: Boolean = true,
    onTajweed: (Boolean) -> Unit = {},
) {
    val surah = SURAHS[reading.surah - 1]
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val verses = pluralStringResource(R.plurals.verses, surah.verses, surah.verses)
    KeepScreenOn(playing)
    // With no recording there are no timings: the words are simply shown, and nothing is lit.
    val index = remember(reading) { reading.timings?.let { WordIndex(it, reading.verses.map { verse -> verse.words.size }) } }
    val current by remember(index) { derivedStateOf { index?.at(position()) ?: -1 } }
    // A passage said again has timings of its own: which saying of the word this is.
    val said by remember(index) { derivedStateOf { index?.said(position()) ?: -1 } }
    val currentVerse = index?.verse(current) ?: -1
    val above = 1 // the surah's title page comes before the first verse in the list
    // Opened at a verse (from a pin or a collection): that verse is at the top of the page.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = startVerse?.let { it + above } ?: 0)
    // The IndoPak script needs its own font, which is fetched; until it is here, the Madinah mushaf's.
    val hand = hand(if (script == Script.INDOPAK && indopak == null) Script.MADINAH else script, indopak)

    // The page follows the recitation until the reader scrolls it themselves. Opened at a
    // verse the recitation can't be moved to, it stays on that verse instead.
    var follow by remember { mutableStateOf(startVerse == null || index?.startOfVerse(startVerse) != null) }
    LaunchedEffect(list) {
        list.interactionSource.interactions.collect { if (it is DragInteraction.Start) follow = false }
    }
    // The word being recited is kept at the middle of the page, always in the same place
    // for a quick glance. The page glides after it like a camera: it speeds up and slows
    // down gently and never restarts when the next word comes. A jump too far to glide
    // (a seek to another part of the surah) is covered by a short dissolve instead.
    val place = remember { WordPlace() }
    with(LocalDensity.current) {
        place.margin = 4.dp.toPx()
        // Between a verse's last line and the next verse's first there is the rule with its star, and perhaps a translation.
        place.gap = 120.dp.toPx()
    }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(follow, index) {
        if (!follow || index == null) {
            fade.snapTo(1f)
            return@LaunchedEffect
        }
        var speed = 0f // px per second, kept from one word to the next
        snapshotFlow { current }.collectLatest { word ->
            if (word < 0) return@collectLatest
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
                    fade.animateTo(0f, tween(140))
                    list.scrollToItem(index.verse(word) + above)
                    repeat(4) { if (place.offCentre(word) == null) withFrameNanos { } }
                    place.offCentre(word)?.let { list.scrollBy(it) }
                    fade.animateTo(1f, tween(260))
                    speed = 0f
                    break
                }
                waited = 0
                if (abs(off) > place.pageHeight() * 1.5f) {
                    fade.animateTo(0f, tween(140))
                    list.scrollBy(off)
                    fade.animateTo(1f, tween(260))
                    speed = 0f
                    break
                }
                if (fade.value < 1f) fade.snapTo(1f)
                if (abs(off) < 1f && abs(speed) < 8f) break // there
                // A critically damped spring: pulled toward the word, never overshooting.
                speed += (STIFFNESS * STIFFNESS * off - 2 * STIFFNESS * speed) * dt
                val step = speed * dt
                if (abs(list.dispatchRawDelta(step)) < abs(step) * 0.5f) {
                    speed = 0f // the page can't scroll any further that way
                    break
                }
            }
        }
    }

    // Whether the word being recited is above where the page now is: the way the follow button points.
    val recitedAbove by remember(index) {
        derivedStateOf {
            val item = (index?.verse(current) ?: -1) + above
            val showing = list.layoutInfo.visibleItemsInfo
            when {
                item < above || showing.isEmpty() -> false
                item < showing.first().index -> true
                item > showing.last().index -> false
                else -> (place.offCentre(current) ?: 0f) < 0f
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

    // The menu slides in from the side its button is on, and Back closes it. A drawer comes in
    // from where the reading starts, so it is handed the page the other way round, and what is
    // in it and under it is put back the right way.
    val menu = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(enabled = menu.isOpen) { scope.launch { menu.close() } }
    val direction = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides if (direction == LayoutDirection.Ltr) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        ModalNavigationDrawer(
            drawerState = menu,
            // Only the button opens it: a swipe from the edge is the phone's own Back.
            gesturesEnabled = menu.isOpen,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 16.dp).testTag("reader-menu")) {
                            Text(
                                stringResource(R.string.menu), Modifier.padding(horizontal = 24.dp),
                                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
                            )
                            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            ReadingChoices(showMeanings, onMeanings, textScale, onTextScale, script, onScript, withIndoPak = indopak != null, appearance, onAppearance, tajweed, onTajweed)
                        }
                    }
                }
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Scaffold(
                topBar = {
                    Banner {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                            val onBanner = LocalBanner.current.content
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                SurahTitle(surah, 24.sp, onBanner, Modifier.testTag("surah-title"), withWord = true)
                                Text(
                                    if (arabic) verses else stringResource(R.string.name_and_verses, surah.latin, verses),
                                    style = MaterialTheme.typography.bodySmall, color = LocalBanner.current.quiet,
                                )
                            }
                            // One button at each end, so the title between them is in the middle of the bar.
                            IconButton(onClick = { scope.launch { menu.open() } }) { Icon(AppIcons.Menu, stringResource(R.string.menu)) }
                        }
                    }
                },
                bottomBar = {
                    Column {
                        suggestion()
                        if (index == null) {
                            withoutRecitation()
                        } else PlayerBar(
                            // Still being listened to: say how far the words can light up.
                            placedUntil = reading.timings?.takeIf { !it.complete }?.heardMs, listening = listening,
                            range = range, verses = reading.verses.size, onRange = onRange,
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
                    if (!follow && index != null) FollowButton(up = recitedAbove, onClick = { follow = true })
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (reading.timings?.matches == false) {
                        Text(
                            stringResource(R.string.mismatch, surahName(surah)),
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Verses(reading, index, list, current, currentVerse, index?.timing(said), index?.ends(said) ?: Int.MAX_VALUE, position, showMeanings, textScale, hand, tajweed, marks, onMark, place, { fade.value }, translated, { translated = it; taps++ }) { ms ->
                        onSeek(ms)
                        follow = true
                    }
                }
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
 * Takes up following the recitation again, after the page was scrolled away from it: the
 * eight-point star that frames the verse numbers, filled, with an arrow pointing the way
 * to the word being recited ([up] the page, or down it).
 */
@Composable
fun FollowButton(up: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.follow)
    Box(
        Modifier.size(60.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(54.dp)) {
            // Two squares, one turned an eighth of a turn.
            val side = size.minDimension * 0.7f
            val corner = Offset((size.width - side) / 2, (size.height - side) / 2)
            drawRect(colors.primary, corner, Size(side, side))
            rotate(45f) { drawRect(colors.primary, corner, Size(side, side)) }
            val reach = size.minDimension * 0.17f
            val tip = center.y + if (up) -reach else reach
            val tail = center.y + if (up) reach else -reach
            val barb = if (up) reach * 0.8f else -reach * 0.8f
            val arrow = Path().apply {
                moveTo(center.x, tail)
                lineTo(center.x, tip)
                moveTo(center.x - reach * 0.8f, tip + barb)
                lineTo(center.x, tip)
                lineTo(center.x + reach * 0.8f, tip + barb)
            }
            drawPath(arrow, colors.onPrimary, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
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
        val centred = now.center.y - page.size.height / 2f
        val before = lineBefore?.takeIf { it.isAttached }?.let { page.localBoundingBoxOf(it, clipBounds = false) } ?: return centred
        // Only the line right above counts: after a jump, what was recited before is somewhere else.
        if (before.top >= now.top || now.top - before.bottom > gap) return centred
        if (before.top - centred >= margin) return centred
        return maxOf(before.top - margin, now.bottom - (page.size.height - margin))
    }
}

@Composable
private fun Verses(
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
    translated: Int?,
    onTranslate: (Int) -> Unit,
    onSeek: (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // With a recitation, the title page fills the top half, so the first verse starts at
        // the middle of the page, where the word being recited is kept; and half a page of
        // room below the last verse lets its words reach the middle too.
        val room = if (index != null) (maxHeight / 2 - 40.dp).coerceAtLeast(0.dp) else 0.dp
        // The basmala is one very wide glyph, 11.5 times as wide as its font size: sized to span the page.
        val basmalaSize = with(LocalDensity.current) { ((maxWidth - 32.dp) / 11.5f).toSp() }
        LazyColumn(
            Modifier.fillMaxSize().testTag("verses").onGloballyPositioned { place.list = it }.graphicsLayer { alpha = fade() },
            state = list,
            contentPadding = PaddingValues(bottom = room + 24.dp),
        ) {
            item { TitlePage(SURAHS[reading.surah - 1], minHeight = room, basmalaSize = basmalaSize) }
            itemsIndexed(reading.verses) { v, verse ->
                val first = index?.firstOfVerse?.getOrElse(v) { 0 } ?: 0
                VerseBlock(
                    number = v + 1,
                    verse = verse,
                    timings = reading.timings?.verses?.getOrElse(v) { emptyList() }.orEmpty(),
                    // The word of this verse being recited; -1 when the recitation is elsewhere.
                    current = if (currentVerse == v) current - first else -1,
                    now = if (currentVerse == v) now else null,
                    nowEnds = nowEnds,
                    position = position,
                    showMeanings = showMeanings,
                    textScale = textScale,
                    hand = hand,
                    // The rules of recitation in each of the verse's words, when their colours are wanted.
                    rules = if (tajweed) reading.rules.getOrNull(v).orEmpty() else emptyList(),
                    pins = marks.pins[v].orEmpty(),
                    saved = v in marks.saved,
                    onMark = { onMark(v) },
                    showTranslation = translated == v,
                    onPlaced = { w, tile -> place.placed(first + w, tile) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VerseBlock(
    number: Int,
    verse: Verse,
    timings: List<WordTiming>,
    current: Int,
    now: WordTiming?,
    nowEnds: Int,
    position: () -> Int,
    showMeanings: Boolean,
    textScale: Float,
    hand: Hand,
    rules: List<List<Mark>>,
    pins: List<String>,
    saved: Boolean,
    onMark: () -> Unit,
    showTranslation: Boolean,
    onPlaced: (Int, LayoutCoordinates) -> Unit,
    onTap: () -> Unit,
    onWord: (Int) -> Unit,
) {
    // The whole verse answers a tap, quietly: no ripple across a block of scripture. A long
    // press, like a tap on its number, opens its pins and collections.
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap, onLongClick = onMark),
    ) {
        // The verse's number sits on the rule above it, at the left in every language.
        val label = stringResource(R.string.verse, number)
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
                // What marks the verse: each pin left on it, by name, and a bookmark if it is in a collection.
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
                if (saved) Icon(AppIcons.Bookmark, stringResource(R.string.saved_mark), Modifier.padding(end = 6.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        // Arabic runs from the right, whatever the phone's language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            // Which words are on the verse's first row and which on its last, as the blocks were
            // last laid out: those blocks' fill reaches on to the rule above and the rule below.
            var firstRowEnds by remember { mutableIntStateOf(Int.MAX_VALUE) }
            var lastRowStarts by remember { mutableIntStateOf(0) }
            WordBlocks(Modifier.fillMaxWidth().padding(horizontal = EDGE_MARGIN), onRows = { firstEnds, lastStarts -> firstRowEnds = firstEnds; lastRowStarts = lastStarts }) {
                verse.words.forEachIndexed { w, word ->
                    WordTile(
                        word = word,
                        // The word being recited goes by this saying of it, which may be a second one.
                        timing = if (w == current && now != null) now else timings.getOrNull(w),
                        state = when {
                            current < 0 -> WordState.RESTING
                            w < current -> WordState.RECITED
                            w == current -> WordState.CURRENT
                            else -> WordState.RESTING
                        },
                        position = position,
                        // The word's last sound runs until the next word begins.
                        endMs = if (w == current && now != null) nowEnds else timings.getOrNull(w + 1)?.startMs ?: Int.MAX_VALUE,
                        showMeaning = showMeanings,
                        textScale = textScale,
                        hand = hand,
                        rules = rules.getOrNull(w).orEmpty(),
                        // Up to this verse's rule, and down to the next verse's, each short of it by a
                        // hair. Not down over the verse's translation while that is showing.
                        reachUp = { if (w <= firstRowEnds) RULE_TO_WORDS - RULE_MARGIN else 0.dp },
                        reachDown = { if (w >= lastRowStarts && !showTranslation) WORDS_TO_NEXT_RULE - RULE_MARGIN else 0.dp },
                        onPlaced = { onPlaced(w, it) },
                        onClick = { onWord(w) },
                        onLongClick = onMark,
                    )
                }
            }
        }
        // The verse's translation opens and closes softly rather than popping in.
        AnimatedVisibility(
            visible = showTranslation && verse.translation.isNotEmpty(),
            enter = fadeIn(tween(260)) + expandVertically(tween(260)),
            exit = fadeOut(tween(420)) + shrinkVertically(tween(420)),
        ) {
            Text(
                verse.translation, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
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
    reachUp: () -> Dp,
    reachDown: () -> Dp,
    onPlaced: (LayoutCoordinates) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val written = word.shownIn(hand.script)
    val isCurrent = state == WordState.CURRENT
    // Nothing switches on or off: the block's fill and the light fade from one word to the
    // next, and a word dims gently once it has been recited.
    val lit by animateFloatAsState(if (isCurrent) 1f else 0f, tween(if (isCurrent) 220 else 420), label = "lit")
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = (if (showMeaning) 8.dp else 14.dp) * textScale, vertical = 3.dp * textScale),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The letters themselves are never recoloured: recolouring part of a joined-up word
        // makes Android shape it again, and the letters visibly shift. Colour is painted over
        // them instead: the rules of recitation once, and the letter being uttered afresh every
        // frame, with nothing laid out again.
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        val gold = LocalHighlight.current
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
            modifier = if (coloured.isEmpty() && (lit == 0f || timing == null)) Modifier else Modifier.drawWithContent {
                val placed = layout
                val uttered = if (lit > 0f && timing != null) timing.utteredAt(position(), endMs) else null
                if (placed == null || (repaint == null && uttered == null)) {
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
                        for (mark in coloured) {
                            repaint?.draw(canvas.nativeCanvas, shown(mark.from), shown(mark.to), edge, baseline, box, tajweed.ink(mark.rule.family).faded(strength, colors.background))
                        }
                    }
                    return@drawWithContent
                }

                val from = shown(uttered.from)
                val to = shown(uttered.to)
                // A stretch that is only signs: the vowel of the letter just uttered.
                val signs = repaint != null && repaint.signsApart && to > from && (from until to).all { written[it].isMark() }
                // The rule the stretch falls under gives the light its colour and its motion.
                val family = coloured.firstOrNull { it.from < uttered.to && uttered.from < it.to }?.rule?.family
                val glow = family?.let(tajweed::light) ?: gold
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
                    for (mark in coloured) {
                        repaint?.draw(native, shown(mark.from), shown(mark.to), edge, baseline, box, tajweed.ink(mark.rule.family).faded(strength, colors.background))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerBar(
    placedUntil: Int?,
    listening: Boolean,
    range: IntRange?,
    verses: Int,
    onRange: (IntRange) -> Unit,
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
    var choosing by remember { mutableStateOf(false) }
    if (choosing && range != null) {
        RangeDialog(verses, range, onDone = { choosing = false; onRange(it) }, onDismiss = { choosing = false })
    }
    BottomBand {
        Column(Modifier.padding(horizontal = 12.dp).padding(top = 2.dp, bottom = 2.dp)) {
            if (placedUntil != null) {
                Text(
                    when {
                        listening && placedUntil > 0 -> stringResource(R.string.listening_ahead, clock(placedUntil))
                        listening -> stringResource(R.string.listening_not_yet)
                        placedUntil > 0 -> stringResource(R.string.listening_stopped, clock(placedUntil))
                        else -> stringResource(R.string.listening_stopped_none)
                    },
                    Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = band.quiet,
                )
            }
            // The time line runs left to right in every language, as on other players: a thin
            // line with a small bead on it.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(clock(dragged?.let { (it * duration).toInt() } ?: shown), style = MaterialTheme.typography.labelMedium, color = band.quiet)
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
                        modifier = Modifier.weight(1f).height(24.dp),
                        colors = line,
                        thumb = { Box(Modifier.size(12.dp).background(band.content, CircleShape)) },
                        track = { state ->
                            SliderDefaults.Track(
                                state, Modifier.height(3.dp), colors = line,
                                drawStopIndicator = null, thumbTrackGapSize = 0.dp, trackInsideCornerSize = 0.dp,
                            )
                        },
                    )
                    Text(clock(duration), style = MaterialTheme.typography.labelMedium, color = band.quiet)
                }
            }
            Box(Modifier.fillMaxWidth()) {
                // Which verses will be recited; tap to choose where to start and where to stop.
                if (range != null) {
                    val label = stringResource(R.string.range_label, range.first + 1, range.last + 1)
                    TextButton(
                        onClick = { choosing = true }, modifier = Modifier.align(Alignment.CenterStart).semantics { contentDescription = label },
                        colors = ButtonDefaults.textButtonColors(contentColor = band.content), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Icon(AppIcons.Range, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.range_short, range.first + 1, range.last + 1), maxLines = 1)
                    }
                }
                // Previous verse on the side reading starts from, next verse on the other; the arrows turn round with them.
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious) {
                        Icon(AppIcons.SkipPrevious, stringResource(R.string.previous_verse), Modifier.scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f), tint = band.content)
                    }
                    PlayButton(playing, onPlayPause)
                    IconButton(onClick = onNext) {
                        Icon(AppIcons.SkipNext, stringResource(R.string.next_verse), Modifier.scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f), tint = band.content)
                    }
                }
            }
        }
    }
}

/**
 * The play button: a gold disc inside a thin gold ring, like a seal. The ring and the disc
 * are drawn together, round one centre, so they can't sit askew of each other. (As a ring
 * drawn round a separate button they came out a pixel or two off on some screens, because
 * each was rounded to whole pixels on its own.)
 */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val band = LocalBanner.current
    Box(
        Modifier.padding(horizontal = 10.dp).size(46.dp).clip(CircleShape)
            .drawBehind {
                val ring = 1.2.dp.toPx()
                drawCircle(band.rule, radius = size.minDimension / 2 - ring / 2, style = Stroke(ring))
                drawCircle(band.rule, radius = size.minDimension / 2 - 4.dp.toPx())
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (playing) {
            Icon(AppIcons.Pause, stringResource(R.string.pause), tint = band.onRule)
        } else {
            Icon(AppIcons.Play, stringResource(R.string.play), tint = band.onRule)
        }
    }
}

/**
 * The surah's title page, above its first verse: its number, its name in calligraphy,
 * what the name means, how long it is and where it was revealed, inside a ruled frame,
 * and then the basmala drawn as one piece. It is at least [minHeight] tall.
 */
@Composable
private fun TitlePage(surah: Surah, minHeight: Dp, basmalaSize: TextUnit) {
    val colors = MaterialTheme.colorScheme
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    Column(
        Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        // Two rules, one inside the other, as round the heading of a surah in a mushaf.
        Column(
            Modifier.fillMaxWidth().border(1.5.dp, colors.primary, RoundedCornerShape(18.dp)).padding(5.dp)
                .border(0.8.dp, colors.primary.faded(0.55f, colors.background), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ArchNumber(surah.number)
            SurahTitle(surah, 46.sp, colors.primary, withWord = true)
            if (!arabic) {
                Text(stringResource(R.string.name_and_meaning, surah.latin, surah.meaning), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            Text(
                stringResource(
                    R.string.surah_details,
                    pluralStringResource(R.plurals.verses, surah.verses, surah.verses),
                    stringResource(if (surah.madinan) R.string.revealed_madinah else R.string.revealed_makkah),
                    surah.revealed,
                ),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
        // Every surah but At-Tawbah opens with the basmala. In Al-Fatihah it is also the first verse.
        if (surah.number != 9) {
            Text(
                "\uFDFD", Modifier.fillMaxWidth().padding(top = 10.dp).semantics { contentDescription = Recital.BASMALA },
                textAlign = TextAlign.Center, fontFamily = QuranFont, fontSize = basmalaSize, lineHeight = basmalaSize * 2,
                maxLines = 1, softWrap = false, color = colors.primary,
            )
        }
    }
}

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

/** The scripts the verses can be written in; the IndoPak one only once its font is on the phone ([withIndoPak]). */
@Composable
fun ScriptChooser(script: Script, withIndoPak: Boolean, onScript: (Script) -> Unit) {
    Column(Modifier.padding(horizontal = 8.dp)) {
        Text(stringResource(R.string.script_title), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        for (each in Script.entries) {
            if (each == Script.INDOPAK && !withIndoPak) continue
            Row(
                Modifier.fillMaxWidth().selectable(selected = each == script, role = Role.RadioButton, onClick = { onScript(each) }).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = each == script, onClick = null, modifier = Modifier.padding(10.dp))
                Text(
                    stringResource(
                        when (each) {
                            Script.MADINAH -> R.string.script_madinah
                            Script.AMIRI -> R.string.script_amiri
                            Script.INDOPAK -> R.string.script_indopak
                            Script.EVERYDAY -> R.string.script_everyday
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
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
            if (action != null) {
                Spacer(Modifier.width(12.dp))
                ThemeButton(action, onAction, icon = icon, filled = true, onBand = true)
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

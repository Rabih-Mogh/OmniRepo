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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import app.recitation.R
import app.recitation.Reading
import app.recitation.core.Recital
import app.recitation.core.SURAHS
import app.recitation.core.Surah
import app.recitation.core.Timings
import app.recitation.core.WordTiming
import app.recitation.data.Verse
import app.recitation.data.Word

/** Finds the word being recited at a moment of the recording. */
private class WordIndex(timings: Timings) {
    val starts: IntArray = timings.verses.flatMap { verse -> verse.map { it.startMs } }.toIntArray()
    val firstOfVerse: IntArray
    private val verseOf: IntArray

    init {
        val first = ArrayList<Int>()
        val of = ArrayList<Int>()
        for ((v, verse) in timings.verses.withIndex()) {
            first += of.size
            repeat(verse.size) { of += v }
        }
        firstOfVerse = first.toIntArray()
        verseOf = of.toIntArray()
    }

    /** The last word that has started by [ms], or -1 before the first. */
    fun at(ms: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (starts[mid] <= ms) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }

    fun verse(word: Int): Int = if (word < 0) -1 else verseOf[word]
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
    onRange: (IntRange) -> Unit = {},
) {
    val surah = SURAHS[reading.surah - 1]
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val verses = pluralStringResource(R.plurals.verses, surah.verses, surah.verses)
    // With no recording there are no timings: the words are simply shown, and nothing is lit.
    val index = remember(reading) { reading.timings?.let(::WordIndex) }
    val current by remember(index) { derivedStateOf { index?.at(position()) ?: -1 } }
    val currentVerse = index?.verse(current) ?: -1
    val list = rememberLazyListState()
    val above = 1 // the surah's title page comes before the first verse in the list

    // The page follows the recitation until the reader scrolls it themselves.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        list.interactionSource.interactions.collect { if (it is DragInteraction.Start) follow = false }
    }
    // The word being recited is kept at the middle of the page, always in the same place
    // for a quick glance. The page glides after it like a camera: it speeds up and slows
    // down gently and never restarts when the next word comes. A jump too far to glide
    // (a seek to another part of the surah) is covered by a short dissolve instead.
    val place = remember { WordPlace() }
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

    // A verse's translation shows when the verse is tapped, and goes away by itself after
    // long enough to read it. Tapping again while it shows starts the wait afresh.
    var translated by remember(reading) { mutableStateOf<Int?>(null) }
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(translated, taps) {
        val verse = translated ?: return@LaunchedEffect
        delay(readingTime(reading.verses.getOrNull(verse)?.translation.orEmpty()))
        translated = null
    }

    fun seekToVerse(verse: Int) {
        val words = index ?: return
        val v = verse.coerceIn(0, words.firstOfVerse.size - 1)
        onSeek(words.starts[words.firstOfVerse[v]])
        follow = true
    }

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
                    TextSizeButton(textScale, onTextScale, onBanner)
                    // The button would otherwise colour itself green, on the green banner.
                    IconToggleButton(checked = showMeanings, onCheckedChange = onMeanings) {
                        Icon(AppIcons.Meanings, stringResource(R.string.meanings), tint = onBanner.copy(alpha = if (showMeanings) 1f else 0.45f))
                    }
                }
            }
        },
        bottomBar = {
            Column {
                suggestion()
                if (index == null) {
                    withoutRecitation()
                } else PlayerBar(
                    range = range, verses = reading.verses.size, onRange = onRange,
                    playing = playing, position = position, duration = duration, onPlayPause = onPlayPause,
                    onSeek = { onSeek(it); follow = true },
                    onPrevious = {
                        // Back to the start of this verse, or to the one before if it has only just begun.
                        val start = if (currentVerse >= 0) index.starts[index.firstOfVerse[currentVerse]] else 0
                        seekToVerse(if (position() - start > 2_000) currentVerse else currentVerse - 1)
                    },
                    onNext = { seekToVerse(currentVerse + 1) },
                )
            }
        },
        floatingActionButton = {
            if (!follow && index != null) {
                ThemeButton(stringResource(R.string.follow), onClick = { follow = true }, icon = AppIcons.Follow, filled = true)
            }
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
            Verses(reading, index, list, current, currentVerse, position, showMeanings, textScale, place, { fade.value }, translated, { translated = it; taps++ }) { ms ->
                onSeek(ms)
                follow = true
            }
        }
    }
}

/** Where the word being recited sits on the page, so the page can be moved to centre it. */
private class WordPlace {
    var list: LayoutCoordinates? = null
    private var word = -1
    private var tile: LayoutCoordinates? = null

    fun placed(word: Int, tile: LayoutCoordinates) {
        this.word = word
        this.tile = tile
    }

    fun pageHeight(): Float = list?.size?.height?.toFloat() ?: 0f

    /** How far below the middle of the page [word]'s tile is, in px; null if it isn't laid out. */
    fun offCentre(word: Int): Float? {
        val page = list?.takeIf { it.isAttached } ?: return null
        val tile = tile?.takeIf { this.word == word && it.isAttached } ?: return null
        return page.localBoundingBoxOf(tile, clipBounds = false).center.y - page.size.height / 2f
    }
}

@Composable
private fun Verses(
    reading: Reading,
    index: WordIndex?,
    list: androidx.compose.foundation.lazy.LazyListState,
    current: Int,
    currentVerse: Int,
    position: () -> Int,
    showMeanings: Boolean,
    textScale: Float,
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
                    position = position,
                    showMeanings = showMeanings,
                    textScale = textScale,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerseBlock(
    number: Int,
    verse: Verse,
    timings: List<WordTiming>,
    current: Int,
    position: () -> Int,
    showMeanings: Boolean,
    textScale: Float,
    showTranslation: Boolean,
    onPlaced: (Int, LayoutCoordinates) -> Unit,
    onTap: () -> Unit,
    onWord: (Int) -> Unit,
) {
    // The whole verse answers a tap, quietly: no ripple across a block of scripture.
    Column(
        Modifier.fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .padding(horizontal = 12.dp),
    ) {
        // The verse's number sits on the rule above it, at the left in every language.
        val label = stringResource(R.string.verse, number)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.width(8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                StarNumber(number, Modifier.padding(horizontal = 4.dp).semantics { contentDescription = label })
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        // Arabic runs from the right, whatever the phone's language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                verse.words.forEachIndexed { w, word ->
                    WordTile(
                        word = word,
                        timing = timings.getOrNull(w),
                        state = when {
                            current < 0 -> WordState.RESTING
                            w < current -> WordState.RECITED
                            w == current -> WordState.CURRENT
                            else -> WordState.RESTING
                        },
                        position = position,
                        // The word's last sound runs until the next word begins.
                        endMs = timings.getOrNull(w + 1)?.startMs ?: Int.MAX_VALUE,
                        showMeaning = showMeanings,
                        textScale = textScale,
                        onPlaced = { onPlaced(w, it) },
                        onClick = { onWord(w) },
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
                verse.translation, Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                fontSize = 14.sp * textScale, lineHeight = 20.sp * textScale,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class WordState { RESTING, RECITED, CURRENT }

/** How eagerly the page follows the word: higher is quicker. At 8 it settles in about two thirds of a second. */
private const val STIFFNESS = 8f

/** How long a translation stays up, in ms: time to read it, between 6 and 25 seconds. */
internal fun readingTime(translation: String): Long = (3_000L + 70L * translation.length).coerceIn(6_000L, 25_000L)

/** A soft round light that fades to nothing at its edge, squashed to [flatten] of its height. */
private fun DrawScope.spot(color: Color, center: Offset, radius: Float, flatten: Float) {
    scale(scaleX = 1f, scaleY = flatten, pivot = center) {
        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius), radius = radius, center = center)
    }
}

@Composable
private fun WordTile(
    word: Word,
    timing: WordTiming?,
    state: WordState,
    position: () -> Int,
    endMs: Int,
    showMeaning: Boolean,
    textScale: Float,
    onPlaced: (LayoutCoordinates) -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isCurrent = state == WordState.CURRENT
    // Nothing switches on or off: the frame and the light fade from one word to the next,
    // and a word dims gently once it has been recited.
    val lit by animateFloatAsState(if (isCurrent) 1f else 0f, tween(if (isCurrent) 220 else 420), label = "lit")
    val ink by animateColorAsState(
        // Solid, not see-through: see-through ink shows dark patches where the letters join.
        if (state == WordState.RECITED) colors.onSurface.faded(0.45f, colors.background) else colors.onSurface, tween(420), label = "ink",
    )
    val shape = RoundedCornerShape(10.dp)
    Column(
        // The word being recited sits in a lightly tinted frame that takes in its meaning too,
        // and tells the page where it is so the page can keep it in the middle.
        (if (isCurrent) Modifier.onGloballyPositioned(onPlaced) else Modifier)
            .then(if (lit > 0f) Modifier.border(1.5.dp, colors.primary.copy(alpha = lit), shape).background(colors.primary.copy(alpha = 0.12f * lit), shape) else Modifier)
            .clip(shape).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The letters themselves never change: recolouring part of a joined-up word makes
        // Android shape it again, and the letters visibly shift. A pool of light glides
        // under them instead, drawn afresh every frame without laying anything out again.
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        val glow = colors.primary
        Text(
            word.arabic, fontFamily = QuranFont, fontSize = 26.sp * textScale, lineHeight = 46.sp * textScale, color = ink,
            onTextLayout = { layout = it },
            modifier = if (lit == 0f || timing == null) Modifier else Modifier.drawWithContent {
                val placed = layout
                val uttered = timing.utteredAt(position(), endMs)
                if (placed == null || uttered == null) {
                    drawContent()
                    return@drawWithContent
                }
                val length = placed.layoutInput.text.length
                val from = placed.getHorizontalPosition(uttered.from.coerceIn(0, length), usePrimaryDirection = true)
                val to = placed.getHorizontalPosition(uttered.to.coerceIn(0, length), usePrimaryDirection = true)
                val x = from + (to - from) * uttered.fraction
                val reach = maxOf(abs(to - from) * 0.75f, 12.dp.toPx())
                // A halo behind the letter being uttered, then the word, then the letter itself lit up.
                spot(glow.copy(alpha = 0.38f * lit), Offset(x, placed.firstBaseline - 8.dp.toPx()), radius = 26.dp.toPx(), flatten = 0.95f)
                drawContent()
                // The word is drawn once more in the glowing colour, exactly over itself, and all of
                // it but the stretch around the letter is wiped away. The letter changes colour and
                // nothing else: it isn't laid out again and gets no bloom, so it neither moves nor
                // looks any bigger.
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(-reach, -reach, size.width + reach, size.height + reach), Paint())
                    drawText(placed, color = glow)
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent, 0.5f to Color.Black.copy(alpha = lit), 1f to Color.Transparent,
                            startX = x - reach, endX = x + reach,
                        ),
                        topLeft = Offset(-reach, -reach), size = Size(size.width + 2 * reach, size.height + 2 * reach),
                        blendMode = BlendMode.DstIn,
                    )
                    canvas.restore()
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

@Composable
private fun PlayerBar(
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
        Column(Modifier.padding(horizontal = 12.dp).padding(top = 2.dp, bottom = 6.dp)) {
            // Which verses will be recited; tap to choose where to start and where to stop.
            if (range != null) {
                TextButton(
                    onClick = { choosing = true }, modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.textButtonColors(contentColor = band.rule), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    Icon(AppIcons.Range, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.range_label, range.first + 1, range.last + 1))
                }
            }
            // The time line runs left to right in every language, as on other players.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(clock(dragged?.let { (it * duration).toInt() } ?: shown), style = MaterialTheme.typography.labelMedium, color = band.quiet)
                    Slider(
                        value = dragged ?: if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { dragged = it },
                        onValueChangeFinished = {
                            dragged?.let { onSeek((it * duration).toInt()) }
                            dragged = null
                        },
                        modifier = Modifier.weight(1f).height(36.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = band.rule, activeTrackColor = band.rule,
                            inactiveTrackColor = band.content.faded(0.25f, band.background),
                        ),
                    )
                    Text(clock(duration), style = MaterialTheme.typography.labelMedium, color = band.quiet)
                }
            }
            // Previous verse on the side reading starts from, next verse on the other; the arrows turn round with them.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrevious, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColors(contentColor = band.content)) {
                    Icon(AppIcons.SkipPrevious, null, Modifier.size(20.dp).scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.previous_verse), maxLines = 1)
                }
                // The play button wears the band's gold, ringed like a seal.
                Box(Modifier.padding(horizontal = 8.dp).size(58.dp).border(1.5.dp, band.rule, CircleShape), contentAlignment = Alignment.Center) {
                    FilledIconButton(
                        onClick = onPlayPause, modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = band.rule, contentColor = band.background),
                    ) {
                        if (playing) Icon(AppIcons.Pause, stringResource(R.string.pause)) else Icon(AppIcons.Play, stringResource(R.string.play))
                    }
                }
                TextButton(onClick = onNext, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColors(contentColor = band.content)) {
                    Text(stringResource(R.string.next_verse), maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Icon(AppIcons.SkipNext, null, Modifier.size(20.dp).scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f))
                }
            }
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

/** On the band: opens a small menu to make the verses smaller or larger. */
@Composable
private fun TextSizeButton(scale: Float, onScale: (Float) -> Unit, tint: Color) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AppIcons.TextSize, stringResource(R.string.text_size), tint = tint) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { TextSizeControls(scale, onScale) }
    }
}

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

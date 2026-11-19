package app.recitation.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recitation.R
import app.recitation.Reading
import app.recitation.core.Recital
import app.recitation.core.SURAHS
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
 * The word being recited is lit and fills in letter by letter, and the page scrolls to keep
 * its verse in view. [position] is where the recording is, in ms; it is read as often as
 * the screen redraws, so it is a function rather than a value.
 */
@OptIn(ExperimentalFoundationApi::class)
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
) {
    val surah = SURAHS[reading.surah - 1]
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val verses = pluralStringResource(R.plurals.verses, surah.verses, surah.verses)
    // With no recording there are no timings: the words are simply shown, and nothing is lit.
    val index = remember(reading) { reading.timings?.let(::WordIndex) }
    val current by remember(index) { derivedStateOf { index?.at(position()) ?: -1 } }
    val currentVerse = index?.verse(current) ?: -1
    val list = rememberLazyListState()
    val basmala = surah.number != 1 && surah.number != 9
    val above = if (basmala) 1 else 0 // items in the list before the first verse

    // The page follows the recitation until the reader scrolls it themselves.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        list.interactionSource.interactions.collect { if (it is DragInteraction.Start) follow = false }
    }
    val wordInView = remember { BringIntoViewRequester() }
    val atTop = remember { intArrayOf(-1) } // the verse last brought to the top of the page
    LaunchedEffect(current, follow) {
        if (!follow || current < 0) return@LaunchedEffect
        val item = currentVerse + above
        // A new verse starts at the top of the page, so what comes next can be read ahead.
        if (currentVerse != atTop[0] || list.layoutInfo.visibleItemsInfo.none { it.index == item }) {
            list.animateScrollToItem(item, scrollOffset = -24)
            atTop[0] = currentVerse
        }
        // A verse can be taller than the screen. Keep the word being recited in view: when
        // it runs off the bottom, scroll just far enough to show its tile there.
        withFrameNanos { }
        runCatching { wordInView.bringIntoView() }
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
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.surah_title, surah.arabic), fontFamily = QuranFont, fontSize = 20.sp, lineHeight = 34.sp)
                        Text(
                            if (arabic) verses else stringResource(R.string.name_and_verses, surah.latin, verses),
                            style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.8f),
                        )
                    }
                    // The button would otherwise colour itself green, on the green banner.
                    val onBanner = LocalBanner.current.content
                    IconToggleButton(checked = showMeanings, onCheckedChange = onMeanings) {
                        Icon(AppIcons.Translate, stringResource(R.string.meanings), tint = onBanner.copy(alpha = if (showMeanings) 1f else 0.45f))
                    }
                }
            }
        },
        bottomBar = {
            if (index == null) {
                withoutRecitation()
            } else {
                PlayerBar(
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
                ExtendedFloatingActionButton(onClick = { follow = true }) { Text(stringResource(R.string.follow)) }
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
            Verses(reading, index, list, basmala, current, currentVerse, position, showMeanings, wordInView) { ms ->
                onSeek(ms)
                follow = true
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Verses(
    reading: Reading,
    index: WordIndex?,
    list: androidx.compose.foundation.lazy.LazyListState,
    basmala: Boolean,
    current: Int,
    currentVerse: Int,
    position: () -> Int,
    showMeanings: Boolean,
    wordInView: BringIntoViewRequester,
    onSeek: (Int) -> Unit,
) {
    run {
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            if (basmala) {
                item {
                    Text(
                        Recital.BASMALA, Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center,
                        fontFamily = QuranFont, fontSize = 22.sp, lineHeight = 44.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
                    wordInView = wordInView,
                    onWord = { w -> index?.starts?.getOrNull(first + w)?.let(onSeek) },
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun VerseBlock(
    number: Int,
    verse: Verse,
    timings: List<WordTiming>,
    current: Int,
    position: () -> Int,
    showMeanings: Boolean,
    wordInView: BringIntoViewRequester,
    onWord: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 6.dp)) {
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
                        showMeaning = showMeanings,
                        wordInView = wordInView,
                        onClick = { onWord(w) },
                    )
                }
            }
        }
        if (showMeanings && verse.translation.isNotEmpty()) {
            Text(
                verse.translation, Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The verse's number closes it, as the mark at the end of a verse does in a mushaf.
        val label = stringResource(R.string.verse, number)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                Modifier.padding(horizontal = 8.dp).defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                    .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp).semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.number, number), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private enum class WordState { RESTING, RECITED, CURRENT }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordTile(
    word: Word,
    timing: WordTiming?,
    state: WordState,
    position: () -> Int,
    showMeaning: Boolean,
    wordInView: BringIntoViewRequester,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isCurrent = state == WordState.CURRENT
    val ink = when (state) {
        WordState.RESTING -> colors.onSurface
        WordState.RECITED -> colors.onSurface.copy(alpha = 0.45f)
        WordState.CURRENT -> colors.onSurface.copy(alpha = 0.7f)
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        // The page scrolls to whichever tile holds this: the word being recited.
        // The word being recited sits in a lightly tinted frame that takes in its meaning too.
        (if (isCurrent) Modifier.bringIntoViewRequester(wordInView).border(1.5.dp, colors.primary, shape).background(colors.primary.copy(alpha = 0.12f), shape) else Modifier)
            .clip(shape).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val text = if (isCurrent && timing != null) {
            // The letters recited so far take the theme's colour. Colour alone doesn't break the joined-up script.
            val reached by remember(timing) { derivedStateOf { timing.reachedAt(position()).coerceIn(0, word.arabic.length) } }
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.primary)) { append(word.arabic.substring(0, reached)) }
                append(word.arabic.substring(reached))
            }
        } else {
            AnnotatedString(word.arabic)
        }
        Text(text, fontFamily = QuranFont, fontSize = 26.sp, lineHeight = 46.sp, color = ink)
        if (showMeaning) {
            Text(
                word.meaning, Modifier.widthIn(max = 132.dp).padding(bottom = 4.dp), textAlign = TextAlign.Center,
                style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, textDirection = TextDirection.Ltr),
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                color = when (state) {
                    WordState.RESTING -> colors.onSurfaceVariant
                    WordState.RECITED -> colors.onSurfaceVariant.copy(alpha = 0.6f)
                    WordState.CURRENT -> colors.primary
                },
            )
        }
    }
}

@Composable
private fun PlayerBar(
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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.dp).padding(top = 2.dp, bottom = 6.dp)) {
            // The time line runs left to right in every language, as on other players.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(clock(dragged?.let { (it * duration).toInt() } ?: shown), style = MaterialTheme.typography.labelMedium, color = muted)
                    Slider(
                        value = dragged ?: if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { dragged = it },
                        onValueChangeFinished = {
                            dragged?.let { onSeek((it * duration).toInt()) }
                            dragged = null
                        },
                        modifier = Modifier.weight(1f).height(36.dp),
                    )
                    Text(clock(duration), style = MaterialTheme.typography.labelMedium, color = muted)
                }
            }
            // Previous verse on the side reading starts from, next verse on the other; the arrows turn round with them.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrevious, modifier = Modifier.weight(1f)) {
                    Icon(AppIcons.SkipPrevious, null, Modifier.size(20.dp).scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.previous_verse), maxLines = 1)
                }
                FilledIconButton(onClick = onPlayPause, modifier = Modifier.padding(horizontal = 8.dp).size(52.dp)) {
                    if (playing) Icon(AppIcons.Pause, stringResource(R.string.pause)) else Icon(Icons.Filled.PlayArrow, stringResource(R.string.play))
                }
                TextButton(onClick = onNext, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.next_verse), maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Icon(AppIcons.SkipNext, null, Modifier.size(20.dp).scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f))
                }
            }
        }
    }
}

/**
 * What sits where the player would be when a surah is open without a recording: a line
 * saying how its recitation stands, and a button when there is something to do about it.
 */
@Composable
fun NoRecitationBar(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.navigationBars).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(onClick = onAction) { Text(action, maxLines = 1) }
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
                FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
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

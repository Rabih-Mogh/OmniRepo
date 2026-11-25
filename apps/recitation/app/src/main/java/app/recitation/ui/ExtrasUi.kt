package app.recitation.ui

import android.text.Html
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.recitation.AppModel
import app.recitation.R
import app.recitation.core.SURAHS
import app.recitation.data.Explained
import app.recitation.data.Kind
import app.recitation.data.Pack
import app.recitation.data.Script
import app.recitation.data.VerseRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** What the reader has chosen of each kind, and the way to choose another ([open]): for the menus that show it. */
class Choosing(val chosen: Map<Kind, Pack?>, val open: (Kind) -> Unit)

/** Null where nothing can be chosen (a preview, a test of another thing). */
val LocalChoosing = staticCompositionLocalOf<Choosing?> { null }

/** Opens the exegesis of a verse of the surah being read, by its number; null where there is none to open. */
val LocalExplain = staticCompositionLocalOf<((verse: Int) -> Unit)?> { null }

/** Where a thing to choose stands: with the app, on the phone, on its way, or still to be fetched. */
sealed interface PackState {
    data object InApp : PackState
    data class OnPhone(val bytes: Long) : PackState
    data class Fetching(val fraction: Float) : PackState
    data object Waiting : PackState
    data object ToFetch : PackState
}

@Composable
private fun Kind.title(): String = stringResource(
    when (this) {
        Kind.SCRIPT -> R.string.script_title
        Kind.MEANINGS -> R.string.choose_meanings
        Kind.TRANSLATION -> R.string.choose_translation
        Kind.EXEGESIS -> R.string.choose_exegesis
    },
)

private fun Kind.icon() = when (this) {
    Kind.SCRIPT -> AppIcons.Script
    Kind.MEANINGS -> AppIcons.Meanings
    Kind.TRANSLATION -> AppIcons.Translate
    Kind.EXEGESIS -> AppIcons.Exegesis
}

/** What a thing to choose is called: a script by the app's own name for it, the rest by their own. */
@Composable
fun Pack.shownName(): String = if (kind != Kind.SCRIPT) name else stringResource(
    when (Script.of(id)) {
        Script.MADINAH -> R.string.script_madinah
        Script.AMIRI -> R.string.script_amiri
        Script.INDOPAK -> R.string.script_indopak
        Script.EVERYDAY -> R.string.script_everyday
    },
)

/**
 * What the page is made of, a row for each kind: the script, the language of the words'
 * meanings, the translation and the exegesis, each with what is chosen now. A tap opens
 * the list to choose from.
 */
@Composable
fun ChoiceRows() {
    val choosing = LocalChoosing.current ?: return
    Column {
        for (kind in Kind.entries) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { choosing.open(kind) }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("choose-${kind.key}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(kind.icon(), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(kind.title(), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        choosing.chosen[kind]?.shownName() ?: stringResource(R.string.choose_none),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The list to choose one of a [kind] from. [packs] is null while it is being asked for;
 * [listed] is false when it couldn't be, and only what is on the phone is shown. Each has
 * its state at its end ([stateOf]): a tick on the one chosen, how much room one on the
 * phone takes (with the way to take it off, [onDelete], unless it is the one chosen), a
 * ring filling for one on its way, an arrow for one that a tap will fetch. Translations
 * and exegeses are many: they stand under their languages, the phone's own first, and can
 * be searched.
 */
@Composable
fun PickerContent(
    kind: Kind,
    packs: List<Pack>?,
    listed: Boolean,
    chosen: Pack?,
    stateOf: (Pack) -> PackState,
    onPick: (Pack) -> Unit,
    onNone: (() -> Unit)?,
    onDelete: (Pack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val mine = LocalConfiguration.current.locales[0].getDisplayLanguage(Locale.ENGLISH).lowercase()
    val grouped = kind == Kind.TRANSLATION || kind == Kind.EXEGESIS
    val shown = remember(packs, query, mine) {
        val wanted = query.trim().lowercase()
        packs.orEmpty().filter { wanted.isEmpty() || wanted in it.name.lowercase() || wanted in it.language || wanted in it.author.lowercase() }
            .let { all -> if (!grouped) all else all.sortedWith(compareBy({ it.language != mine }, { it.language != "arabic" }, { it.language != "english" }, { it.language }, { it.name })) }
    }
    Column(modifier) {
        if (grouped && packs != null && packs.size > SEARCH_FROM) {
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("pack-search"), singleLine = true,
                placeholder = { Text(stringResource(R.string.choose_search)) }, leadingIcon = { Icon(AppIcons.Search, null) },
            )
        }
        if (!listed) Text(stringResource(R.string.choose_no_list), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (packs == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { WorkIcon(Picture(AppIcons.Hourglass), stringResource(R.string.choose_listing), size = 44.dp) }
            return
        }
        LazyColumn(Modifier.weight(1f, fill = false)) {
            if (onNone != null && query.isBlank()) {
                item { PackRow(stringResource(R.string.choose_none), null, chosen == null, null, { onNone() }, null) }
            }
            var language = ""
            for (pack in shown) {
                if (grouped && pack.language != language) {
                    language = pack.language
                    val heading = language.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
                    item(key = "language-$language") {
                        Text(heading, Modifier.padding(top = 12.dp, bottom = 2.dp).semantics { heading() }, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                }
                item(key = pack.key) {
                    val state = stateOf(pack)
                    PackRow(
                        pack.shownName(), pack.author.takeIf { it.isNotBlank() && it != pack.name }, chosen?.key == pack.key, state, { onPick(pack) },
                        // Not the one in use, and not what the app comes with: that can be taken off the phone.
                        if (state is PackState.OnPhone && chosen?.key != pack.key) ({ onDelete(pack) }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun PackRow(name: String, author: String?, chosen: Boolean, state: PackState?, onPick: () -> Unit, onDelete: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (chosen) colors.primary.copy(alpha = 0.1f) else colors.surface.copy(alpha = 0f))
            .clickable(role = Role.RadioButton, onClick = onPick).padding(horizontal = 10.dp, vertical = 8.dp).testTag("pack"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (chosen) Icon(AppIcons.Check, stringResource(R.string.choose_chosen), Modifier.size(20.dp), tint = colors.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal)
            if (author != null) Text(author, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
        when (state) {
            null -> {}
            PackState.InApp -> Text(stringResource(R.string.choose_in_app), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            is PackState.OnPhone -> {
                Text(Formatter.formatShortFileSize(context, state.bytes), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                if (onDelete != null) {
                    val label = stringResource(R.string.choose_delete, name)
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onDelete), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Delete, label, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                    }
                }
            }
            is PackState.Fetching -> WorkIcon(Picture(AppIcons.Download, fraction = state.fraction), stringResource(R.string.choose_fetching, (state.fraction * 100).toInt()), size = 30.dp)
            PackState.Waiting -> Icon(AppIcons.Hourglass, stringResource(R.string.choose_waiting), Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            PackState.ToFetch -> Icon(AppIcons.Download, stringResource(R.string.choose_to_fetch), Modifier.size(20.dp), tint = colors.outline)
        }
    }
}

/** The list of a [kind], as a dialog over whatever screen asked for it: what there is, where each stands, and the choosing. */
@Composable
fun PackPicker(kind: Kind, model: AppModel, script: Script, onScript: (Script) -> Unit, onMeanings: () -> Unit, onDismiss: () -> Unit) {
    val listing by produceState<Pair<List<Pack>, Boolean>?>(null, kind) { value = model.packs(kind) }
    val wanted by model.extras.wanted.collectAsState()
    val fetching by model.extras.progress.collectAsState()
    // How much room each takes is counted once the list is here, and again when something arrives or goes.
    var forgotten by remember { mutableStateOf(0) }
    val sizes by produceState(emptyMap<String, Pair<Boolean, Long>>(), listing, wanted, forgotten) {
        value = withContext(Dispatchers.IO) { listing?.first.orEmpty().filter { !it.inApp }.associate { it.key to (model.extras.complete(it) to model.extras.bytes(it)) } }
    }
    val chosen = if (kind == Kind.SCRIPT) Pack.of(script) else model.chosen[kind]
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.86f),
        title = { Text(kind.title()) },
        text = {
            PickerContent(
                kind, listing?.first, listing?.second != false, chosen,
                stateOf = { pack ->
                    val size = sizes[pack.key]
                    when {
                        pack.inApp -> PackState.InApp
                        fetching?.pack?.key == pack.key -> PackState.Fetching(fetching!!.fraction)
                        wanted.any { it.key == pack.key } -> PackState.Waiting
                        size != null && size.first -> PackState.OnPhone(size.second)
                        else -> PackState.ToFetch
                    }
                },
                onPick = { pack ->
                    if (kind == Kind.SCRIPT) onScript(Script.of(pack.id))
                    // A language chosen for the words' meanings is a wish to see them.
                    if (kind == Kind.MEANINGS) onMeanings()
                    model.pick(pack)
                },
                onNone = if (kind == Kind.EXEGESIS) ({ model.unpick(kind) }) else null,
                onDelete = { pack -> model.forget(pack); forgotten++ },
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** A stretch of an exegesis: a heading, or a paragraph. */
class Passage(val heading: Boolean, val text: String)

/**
 * An exegesis as Quran.com gives it, in web markup, as passages to read: headings told
 * from paragraphs, footnote marks and everything else of the markup taken out.
 */
fun passages(markup: String): List<Passage> {
    val out = ArrayList<Passage>()
    val clean = markup.replace(Regex("<sup[^>]*>.*?</sup>", RegexOption.DOT_MATCHES_ALL), "")
    var heading = false
    var from = 0
    fun put(until: Int) {
        val text = Html.fromHtml(clean.substring(from, until), Html.FROM_HTML_MODE_COMPACT).toString().replace(Regex("[ \\t\\u00a0]+"), " ").trim()
        for (line in text.split('\n')) if (line.isNotBlank()) out += Passage(heading, line.trim())
    }
    for (tag in Regex("<(/?)(h[1-6]|p|div|li|ul|ol|blockquote|br)\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(clean)) {
        put(tag.range.first)
        from = tag.range.last + 1
        if (tag.groupValues[2].startsWith("h", ignoreCase = true)) heading = tag.groupValues[1].isEmpty()
    }
    put(clean.length)
    return out
}

/**
 * The exegesis of a verse. [explained] is null while it is being fetched; [failed] when
 * it couldn't be. With none chosen ([pack] null) it says so and offers the list.
 */
@Composable
fun ExegesisContent(pack: Pack?, verse: Int, explained: Explained?, failed: Boolean, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Which exegesis this is: a tap changes it.
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(colors.primary.copy(alpha = 0.1f)).clickable(role = Role.Button, onClickLabel = stringResource(R.string.choose_exegesis), onClick = onChoose)
                .padding(horizontal = 12.dp, vertical = 6.dp).testTag("exegesis-which"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(AppIcons.Exegesis, null, Modifier.size(16.dp), tint = colors.primary)
            Text(pack?.name ?: stringResource(R.string.exegesis_choose), style = MaterialTheme.typography.labelLarge, color = colors.primary, maxLines = 1)
        }
        when {
            pack == null -> Text(stringResource(R.string.exegesis_none), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            failed -> Text(stringResource(R.string.exegesis_failed), style = MaterialTheme.typography.bodyMedium, color = colors.error)
            explained == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                WorkIcon(Picture(AppIcons.Exegesis, AppIcons.Download), stringResource(R.string.exegesis_fetching), size = 48.dp)
            }
            else -> {
                val text = remember(explained.text) { passages(explained.text) }
                if (explained.from != verse) Text(stringResource(R.string.exegesis_with, explained.from), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (text.isEmpty()) Text(stringResource(R.string.exegesis_empty), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                // It reads the way its own language does, whatever the phone's.
                CompositionLocalProvider(LocalLayoutDirection provides if (pack.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("exegesis-text"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (passage in text) {
                            Text(
                                passage.text, Modifier.fillMaxWidth().then(if (passage.heading) Modifier.semantics { heading() } else Modifier),
                                style = if (passage.heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                color = if (passage.heading) colors.primary else colors.onSurface,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/** The exegesis of [verse], as a dialog over the surah: fetched if its surah isn't on the phone yet. */
@Composable
fun ExegesisDialog(verse: VerseRef, model: AppModel, onChoose: () -> Unit, onDismiss: () -> Unit) {
    val pack = model.chosen[Kind.EXEGESIS]
    var explained by remember { mutableStateOf<Explained?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(verse, pack?.key) {
        explained = null
        failed = false
        if (pack != null) {
            explained = model.explain(verse)
            failed = explained == null
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.9f).padding(horizontal = 12.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.exegesis_of, surahName(SURAHS[verse.surah - 1]), verse.verse), style = MaterialTheme.typography.titleLarge) },
        text = { ExegesisContent(pack, verse.verse, explained, failed, onChoose) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** With more than this many to choose from, the list can be searched. */
private const val SEARCH_FROM = 12

package app.recitation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recitation.R
import app.recitation.core.SURAHS
import app.recitation.data.Pin
import app.recitation.data.Verse
import app.recitation.data.VerseCollection
import app.recitation.data.VerseRef

/** What a collection is called: the built-in one is named by the app, in the phone's language. */
@Composable
fun collectionName(collection: VerseCollection): String =
    if (collection.builtIn) stringResource(R.string.collection_later) else collection.name

/** "Al-Baqarah · verse 255". */
@Composable
fun verseName(verse: VerseRef): String = stringResource(R.string.verse_of, surahName(SURAHS[verse.surah - 1]), verse.verse)

/** What can be done with the marks of one verse: handed to [VerseMarksContent]. */
class MarkActions(
    val newPin: (String) -> Unit = {},
    val movePin: (Pin) -> Unit = {},
    val removePin: (Pin) -> Unit = {},
    val keep: (VerseCollection, Boolean) -> Unit = { _, _ -> },
    val newCollection: (String) -> Unit = {},
)

/** A verse's pins and collections, opened by holding the verse or tapping its number. */
@Composable
fun VerseMarksDialog(verse: VerseRef, pins: List<Pin>, collections: List<VerseCollection>, actions: MarkActions, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(verseName(verse)) },
        text = { VerseMarksContent(verse, pins, collections, actions) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

/**
 * The inside of [VerseMarksDialog]. Pins: each can be moved to this verse (a pin is on one
 * verse only), or taken off it, and a new one can be left here. Collections: each can
 * take this verse in or let it go, and a new one can be started with it.
 */
@Composable
fun VerseMarksContent(verse: VerseRef, pins: List<Pin>, collections: List<VerseCollection>, actions: MarkActions) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.pins_title), style = MaterialTheme.typography.titleSmall, color = colors.primary)
        Text(stringResource(R.string.pins_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        for (pin in pins) {
            val here = pin.at == verse
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Pin, null, Modifier.size(20.dp), tint = if (here) colors.primary else colors.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(pin.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (here) stringResource(R.string.pin_here) else verseName(pin.at), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (here) {
                    TextButton(onClick = { actions.removePin(pin) }) { Text(stringResource(R.string.pin_remove)) }
                } else {
                    TextButton(onClick = { actions.movePin(pin) }) { Text(stringResource(R.string.pin_move)) }
                }
            }
        }
        // A pin left without a name is called "Pin 1", "Pin 2", and so on.
        val unnamed = stringResource(R.string.pin_default, pins.size + 1)
        NameAndAdd(stringResource(R.string.pin_new), unnamed, stringResource(R.string.pin_add), mayBeEmpty = true) { actions.newPin(it.ifBlank { unnamed }) }

        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = colors.outlineVariant)
        Text(stringResource(R.string.collections_title), style = MaterialTheme.typography.titleSmall, color = colors.primary)
        for (collection in collections) {
            val kept = verse in collection.verses
            Row(
                Modifier.fillMaxWidth().toggleable(value = kept, role = Role.Checkbox, onValueChange = { actions.keep(collection, it) }).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (kept) AppIcons.Bookmark else AppIcons.BookmarkOutline, null, Modifier.size(22.dp), tint = if (kept) colors.primary else colors.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(collectionName(collection), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (collection.builtIn) {
                        Text(stringResource(R.string.collection_later_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.number, collection.verses.size), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
        }
        NameAndAdd(stringResource(R.string.collection_new), null, stringResource(R.string.collection_add), mayBeEmpty = false, actions.newCollection)
    }
}

/** A box for a name with a plus beside it. [placeholder] is the name it gets when left empty, where that is allowed. */
@Composable
private fun NameAndAdd(label: String, placeholder: String?, add: String, mayBeEmpty: Boolean, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) }, modifier = Modifier.weight(1f), singleLine = true,
            label = { Text(label) }, placeholder = placeholder?.let { { Text(it) } },
        )
        IconButton(onClick = { onAdd(name.trim()); name = "" }, enabled = mayBeEmpty || name.isNotBlank()) {
            Icon(AppIcons.Add, add, tint = if (mayBeEmpty || name.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
    }
}

/** The third tab of the first screen: the pins, each with the verse it is on, and the collections. */
@Composable
fun SavedTab(
    pins: List<Pin>,
    collections: List<VerseCollection>,
    onVerse: (VerseRef) -> Unit,
    onRemovePin: (Pin) -> Unit,
    onCollection: (VerseCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.pins_title), style = MaterialTheme.typography.titleMedium, color = colors.primary) }
        if (pins.isEmpty()) {
            item { Text(stringResource(R.string.saved_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
        }
        items(pins, key = { "pin-" + it.id }) { pin ->
            Card(Modifier.fillMaxWidth().clickable { onVerse(pin.at) }, colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
                Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Pin, null, Modifier.size(22.dp), tint = colors.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pin.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(verseName(pin.at), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemovePin(pin) }) { Icon(AppIcons.Close, stringResource(R.string.pin_remove), tint = colors.onSurfaceVariant) }
                }
            }
        }
        item { Text(stringResource(R.string.collections_title), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, color = colors.primary) }
        items(collections, key = { "collection-" + it.id }) { collection ->
            Card(Modifier.fillMaxWidth().clickable { onCollection(collection) }, colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Bookmark, null, Modifier.size(22.dp), tint = colors.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(collectionName(collection), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            pluralStringResource(R.plurals.verses, collection.verses.size, collection.verses.size),
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (pins.isNotEmpty()) {
            item { Text(stringResource(R.string.saved_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        }
    }
}

/**
 * One collection: its verses in the Quran's order, each with its words and translation.
 * [text] fetches a verse's words, which may take a moment or fail (null). Tapping a verse opens its surah there.
 */
@Composable
fun CollectionContent(
    collection: VerseCollection,
    text: suspend (VerseRef) -> Verse?,
    onBack: () -> Unit,
    onVerse: (VerseRef) -> Unit,
    onRemove: (VerseRef) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        Banner {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(collectionName(collection), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        pluralStringResource(R.plurals.verses, collection.verses.size, collection.verses.size),
                        style = MaterialTheme.typography.bodySmall, color = LocalBanner.current.quiet,
                    )
                }
                // The built-in collection stays; the others can be deleted.
                if (!collection.builtIn) IconButton(onClick = { deleting = true }) { Icon(AppIcons.Close, stringResource(R.string.collection_delete)) }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (collection.verses.isEmpty()) {
                item {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (collection.builtIn) Text(stringResource(R.string.collection_later_note), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.collection_empty), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
            items(collection.verses, key = { "${it.surah}:${it.verse}" }) { ref ->
                val verse by produceState<Verse?>(null, ref) { value = text(ref) }
                Row(Modifier.fillMaxWidth().clickable { onVerse(ref) }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(verseName(ref), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        verse?.let { loaded ->
                            // Arabic runs from the right, whatever the phone's language.
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Text(
                                    loaded.words.joinToString(" ") { it.madinah }, Modifier.fillMaxWidth(),
                                    fontFamily = MadinahFont, fontSize = 22.sp, lineHeight = 40.sp, color = colors.onSurface,
                                )
                            }
                            if (loaded.translation.isNotEmpty()) {
                                Text(
                                    loaded.translation, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    IconButton(onClick = { onRemove(ref) }) { Icon(AppIcons.Close, stringResource(R.string.collection_take_out), tint = colors.onSurfaceVariant) }
                }
                HorizontalDivider(color = colors.outlineVariant)
            }
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.collection_delete)) },
            text = { Text(stringResource(R.string.collection_delete_body)) },
            confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

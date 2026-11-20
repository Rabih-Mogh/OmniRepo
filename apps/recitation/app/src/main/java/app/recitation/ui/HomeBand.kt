package app.recitation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recitation.R
import app.recitation.core.Surah
import app.recitation.core.SurahSearch
import app.recitation.data.Hit
import app.recitation.data.Script
import app.recitation.data.VerseRef

/** The app's Arabic name, "at-tilawah", with its vowel marks, as calligraphy writes a title. */
private const val NAME_WITH_VOWELS = "التِّلَاوَة"

/**
 * The band at the top of the first screen: the menu button, the app's name beside it, and
 * the search button at the far end. While searching ([query] isn't null) it is the search box instead, with
 * an arrow to leave it.
 */
@Composable
fun TitleBand(query: String?, onQuery: (String?) -> Unit, onMenu: () -> Unit) {
    val band = LocalBanner.current
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    Banner {
        if (query == null) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenu) { Icon(AppIcons.Menu, stringResource(R.string.menu)) }
                // The name sits beside the menu button, on the side reading starts from. In Arabic it
                // is written out with its vowels in the hand the verses' basmala is in: the bold
                // Ruqaa it used to be in ran its letters and dots together.
                if (arabic) {
                    val plain = stringResource(R.string.app_name)
                    Text(
                        NAME_WITH_VOWELS, Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading(); contentDescription = plain },
                        fontFamily = QuranFont, fontSize = 26.sp, lineHeight = 40.sp, color = band.content, maxLines = 1,
                    )
                } else {
                    Text(
                        stringResource(R.string.app_name), Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall, color = band.content, maxLines = 1,
                    )
                }
                IconButton(onClick = { onQuery("") }) { Icon(AppIcons.Search, stringResource(R.string.search)) }
            }
        } else {
            val focus = remember { FocusRequester() }
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onQuery(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                TextField(
                    value = query, onValueChange = onQuery, modifier = Modifier.weight(1f).focusRequester(focus), singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_hint), color = band.quiet) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // The box is the band itself: no fill and no line of its own.
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = band.content, unfocusedTextColor = band.content, cursorColor = band.content,
                    ),
                )
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(AppIcons.Close, stringResource(R.string.search_clear)) }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
    }
}

/**
 * What [query] finds: a verse asked for by its numbers, the surahs whose number or name
 * matches, and [hits], the verses whose words or translation contain it.
 */
@Composable
fun SearchResults(
    query: String,
    hits: List<Hit>,
    onSurah: (Int) -> Unit,
    onVerse: (VerseRef) -> Unit,
    modifier: Modifier = Modifier,
    surahRow: @Composable (Surah) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val found = remember(query) { SurahSearch.find(query) }
    LazyColumn(modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (query.isBlank()) {
            item { Text(stringResource(R.string.search_help), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant) }
            return@LazyColumn
        }
        found.verse?.let { (surah, verse) ->
            val at = VerseRef(surah, verse)
            item {
                Row(Modifier.fillMaxWidth().clickable { onVerse(at) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StarNumber(verse)
                    Spacer(Modifier.width(12.dp))
                    Text(verseName(at), style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider(color = colors.outlineVariant)
            }
        }
        items(found.surahs, key = { "surah-${it.number}" }) { surah ->
            surahRow(surah)
            HorizontalDivider(color = colors.outlineVariant)
        }
        if (hits.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.search_in_verses), Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall, color = colors.primary,
                )
            }
        }
        items(hits, key = { "verse-${it.at.surah}:${it.at.verse}" }) { hit ->
            Column(Modifier.fillMaxWidth().clickable { onVerse(hit.at) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(verseName(hit.at), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                // Arabic runs from the right, whatever the phone's language.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        hit.arabic, Modifier.fillMaxWidth(), fontFamily = MadinahFont, fontSize = 20.sp, lineHeight = 36.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, color = colors.onSurface,
                    )
                }
                if (hit.translation.isNotEmpty()) {
                    Text(
                        hit.translation, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                        maxLines = 2, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
        }
        if (found.verse == null && found.surahs.isEmpty() && hits.isEmpty()) {
            item {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.search_none), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.search_words_note), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * The menu behind the menu button: the script the verses are written in, their size,
 * whether each word's meaning shows, and [footer], the app's version and its updates.
 */
@Composable
fun MenuContent(
    script: Script,
    onScript: (Script) -> Unit,
    withIndoPak: Boolean,
    textScale: Float,
    onTextScale: (Float) -> Unit,
    meanings: Boolean,
    onMeanings: (Boolean) -> Unit,
    footer: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = colors.primary)
            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
        ScriptChooser(script, withIndoPak, onScript)
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.text_size), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextSizeControls(textScale, onTextScale)
        }
        Row(
            Modifier.fillMaxWidth().toggleable(value = meanings, role = Role.Switch, onValueChange = onMeanings).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.meanings), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = meanings, onCheckedChange = null)
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = colors.outlineVariant)
        footer()
    }
}

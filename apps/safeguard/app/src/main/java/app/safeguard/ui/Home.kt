package app.safeguard.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safeguard.R
import app.safeguard.core.Book
import app.safeguard.core.Change
import app.safeguard.core.Limit
import app.safeguard.core.Listed
import app.safeguard.core.Page
import app.safeguard.data.Store
import app.safeguard.guard.Admin
import app.safeguard.guard.Watch
import kotlinx.coroutines.delay
import java.time.LocalDate

/** The pages of the app: the first one lists the others, and each kind of rule has its own. */
private enum class Screen { HOME, LIMITS, APPS, SITES, WORDS, LOCK, SETUP }

/** What the phone has switched on for the app. */
internal data class Setup(val watching: Boolean, val admin: Boolean, val installs: Boolean)

/** The app's screens, and the way between them. */
@Composable
fun Home() {
    val context = LocalContext.current
    val book by Store.book.collectAsStateWithLifecycle()
    val used by Store.used.collectAsStateWithLifecycle()
    val rules = book.rules

    // Looked at again each time the screen comes back.
    fun switchedOn() = Setup(Watch.on(context), Admin.active(context), context.packageManager.canRequestPackageInstalls())
    var setup by remember { mutableStateOf(switchedOn()) }
    LifecycleResumeEffect(Unit) {
        setup = switchedOn()
        Store.tick(context)
        onPauseOrDispose {}
    }
    // Counts the waits down while they are on screen.
    if (book.pending.isNotEmpty()) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                Store.tick(context)
            }
        }
    }

    val ask: (Change) -> Unit = { change ->
        val waits = rules.locked && change.loosens(rules)
        Store.ask(context, change)
        if (waits) {
            Toast.makeText(context, context.getString(R.string.queued, Words.period(context, rules.waitHours)), Toast.LENGTH_LONG).show()
        }
    }

    UpdateDialog()

    var page by rememberSaveable { mutableStateOf(Screen.HOME) }
    val back = { page = Screen.HOME }
    BackHandler(enabled = page != Screen.HOME, onBack = back)

    // A limit being changed has the screen to itself, over the page of limits.
    var editing by remember { mutableStateOf<Limit?>(null) }
    editing?.let { limit ->
        LimitEditor(
            limit = limit,
            fresh = rules.limits.none { it.id == limit.id },
            wait = if (rules.locked) Words.period(context, rules.waitHours) else null,
            onSave = {
                ask(Change.PutLimit(it))
                editing = null
            },
            onRemove = {
                ask(Change.DropLimit(limit.id))
                editing = null
            },
            onClose = { editing = null },
        )
        return
    }

    val leaving = book.pending.mapNotNull { it.change as? Change.Drop }
    when (page) {
        Screen.HOME -> Overview(book, setup) { page = it }
        Screen.LIMITS -> LimitsPage(
            limits = rules.limits,
            today = used.millis.takeIf { used.day == LocalDate.now().toString() }.orEmpty(),
            onOpen = { editing = it },
            onBack = back,
        )
        Screen.APPS -> AppsPage(rules.apps, leaving.values(Listed.APP), ask, back)
        Screen.SITES -> ListPage(
            stringResource(R.string.sites_title), stringResource(R.string.sites_about), stringResource(R.string.sites_hint),
            items = rules.sites, leaving = leaving.values(Listed.SITE), clean = Page::site, error = stringResource(R.string.sites_error),
            onAdd = { ask(Change.Add(Listed.SITE, it)) }, onDrop = { ask(Change.Drop(Listed.SITE, it)) }, onBack = back,
        )
        Screen.WORDS -> ListPage(
            stringResource(R.string.keywords_title), stringResource(R.string.keywords_about), stringResource(R.string.keywords_hint),
            items = rules.keywords, leaving = leaving.values(Listed.KEYWORD), clean = { it.trim().lowercase().ifEmpty { null } },
            onAdd = { ask(Change.Add(Listed.KEYWORD, it)) }, onDrop = { ask(Change.Drop(Listed.KEYWORD, it)) }, onBack = back,
        )
        Screen.LOCK -> LockPage(book, ask, back)
        Screen.SETUP -> SetupPage(
            setup = setup,
            locked = rules.locked,
            onStopAdmin = {
                Admin.stop(context)
                setup = setup.copy(admin = false)
            },
            onBack = back,
        )
    }
}

private fun List<Change.Drop>.values(list: Listed): Set<String> = filter { it.list == list }.mapTo(HashSet()) { it.value }

/** The first page: one row for each of the others, saying what is on it. */
@Composable
private fun Overview(book: Book, setup: Setup, open: (Screen) -> Unit) {
    val context = LocalContext.current
    val rules = book.rules
    val comma = stringResource(R.string.comma)
    val none = stringResource(R.string.none_yet)
    val colors = MaterialTheme.colorScheme

    PageFrame(stringResource(R.string.app_name), onBack = null, spacing = 12) {
        val missing = listOfNotNull(
            stringResource(R.string.setup_watch).takeIf { !setup.watching },
            stringResource(R.string.setup_admin).takeIf { !setup.admin },
            stringResource(R.string.setup_installs).takeIf { !setup.installs },
        )
        // Until the phone has everything switched on, that comes first.
        if (missing.isNotEmpty()) {
            Entry(
                stringResource(R.string.setup_title), listOf(stringResource(R.string.setup_missing, missing.joinToString(comma))),
                CardDefaults.cardColors(colors.errorContainer, colors.onErrorContainer),
            ) { open(Screen.SETUP) }
        }

        Entry(stringResource(R.string.limits_title), listOf(rules.limits.joinToString(comma) { it.name }.ifEmpty { none })) { open(Screen.LIMITS) }
        Entry(stringResource(R.string.apps_title), listOf(rules.apps.joinToString(comma).ifEmpty { none })) { open(Screen.APPS) }
        Entry(stringResource(R.string.sites_title), listOf(rules.sites.joinToString(comma).ifEmpty { none })) { open(Screen.SITES) }
        Entry(stringResource(R.string.keywords_title), listOf(rules.keywords.joinToString(comma).ifEmpty { none })) { open(Screen.WORDS) }

        Entry(
            stringResource(R.string.lock_page),
            listOfNotNull(
                stringResource(if (rules.locked) R.string.lock_on_title else R.string.lock_off_title),
                stringResource(R.string.wait_change, Words.period(context, rules.waitHours)),
                book.pending.takeIf { it.isNotEmpty() }
                    ?.let { waiting -> stringResource(R.string.waiting_list, waiting.joinToString(comma) { Words.change(context, it.change, rules) }) },
            ),
            if (rules.locked) CardDefaults.cardColors(colors.primaryContainer, colors.onPrimaryContainer) else CardDefaults.cardColors(),
        ) { open(Screen.LOCK) }

        if (missing.isEmpty()) Entry(stringResource(R.string.setup_title), listOf(stringResource(R.string.setup_done))) { open(Screen.SETUP) }

        VersionFooter()
    }
}

/** A row of the first page: the name of a page, what is on it in a line or more, and an arrow into it. */
@Composable
private fun Entry(title: String, lines: List<String>, colors: CardColors = CardDefaults.cardColors(), onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = colors) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                for (line in lines) {
                    Text(line, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Arrow(back = false)
        }
    }
}

/** A page: its name, the way back when there is one, and what is on it. */
@Composable
internal fun PageFrame(title: String, onBack: (() -> Unit)?, spacing: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                val back = stringResource(R.string.back)
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = back }) { Arrow(back = true) }
            }
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        content()
    }
}

/** An arrow head pointing into a page, or [back] out of one, whichever way the language reads. */
@Composable
private fun Arrow(back: Boolean) {
    val color = LocalContentColor.current
    val toTheLeft = back == (LocalLayoutDirection.current == LayoutDirection.Ltr)
    Canvas(Modifier.size(20.dp)) {
        val tip = if (toTheLeft) size.width * 0.3f else size.width * 0.7f
        val tail = size.width - tip
        val width = 2.dp.toPx()
        drawLine(color, Offset(tail, size.height * 0.15f), Offset(tip, size.height * 0.5f), width, StrokeCap.Round)
        drawLine(color, Offset(tip, size.height * 0.5f), Offset(tail, size.height * 0.85f), width, StrokeCap.Round)
    }
}

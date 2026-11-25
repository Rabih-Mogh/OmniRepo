package app.safeguard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safeguard.R
import app.safeguard.core.Change
import app.safeguard.core.Limit
import app.safeguard.core.Listed
import app.safeguard.core.Page
import app.safeguard.data.Store
import app.safeguard.guard.Admin
import app.safeguard.guard.Watch
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * The one screen: what still has to be switched on, the lock, the changes that are
 * waiting, and the lists of what is limited and blocked.
 */
@Composable
fun Home() {
    val context = LocalContext.current
    val book by Store.book.collectAsStateWithLifecycle()
    val used by Store.used.collectAsStateWithLifecycle()
    val rules = book.rules

    // What the phone has switched on for the app, looked at again each time the screen comes back.
    var watching by remember { mutableStateOf(Watch.on(context)) }
    var admin by remember { mutableStateOf(Admin.active(context)) }
    var installs by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    LifecycleResumeEffect(Unit) {
        watching = Watch.on(context)
        admin = Admin.active(context)
        installs = context.packageManager.canRequestPackageInstalls()
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

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

        if (!watching) {
            Notice(
                stringResource(R.string.watch_off_title), stringResource(R.string.watch_off_body),
                stringResource(R.string.watch_open), { context.open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            ) {
                TextButton(onClick = { context.open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, context.me())) }) {
                    Text(stringResource(R.string.watch_info), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        if (!admin) {
            Notice(
                stringResource(R.string.admin_off_title), stringResource(R.string.admin_off_body),
                stringResource(R.string.admin_allow), { context.open(Admin.ask(context)) },
            )
        }
        if (!installs) {
            Notice(
                stringResource(R.string.installs_off_title), stringResource(R.string.installs_off_body),
                stringResource(R.string.installs_allow), { context.open(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, context.me())) },
            )
        }

        Lock(
            locked = rules.locked,
            period = Words.period(context, rules.waitHours),
            waitHours = rules.waitHours,
            unlocking = book.pending.any { it.change == Change.Unlock },
            admin = admin,
            ask = ask,
            onStopAdmin = {
                Admin.stop(context)
                admin = false
            },
        )

        if (book.pending.isNotEmpty()) {
            Section(stringResource(R.string.waiting_title)) {
                for (pending in book.pending) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Words.change(context, pending.change, rules))
                            Text(
                                stringResource(R.string.waiting_in, Words.left(context, pending.left)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        TextButton(onClick = { Store.cancel(context, pending.change) }) { Text(stringResource(R.string.withdraw)) }
                    }
                }
            }
        }

        Section(stringResource(R.string.limits_title), stringResource(R.string.limits_about)) {
            val today = used.millis.takeIf { used.day == LocalDate.now().toString() }.orEmpty()
            for (limit in rules.limits) {
                Card(onClick = { editing = limit }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(limit.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            limit.minutesPerDay?.let { stringResource(R.string.limit_used, (today[limit.id] ?: 0) / 60_000, it) }
                                ?: stringResource(R.string.limit_no_allowance),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            Words.hours(context, limit.hours),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedButton(onClick = { editing = Limit(id = "limit-" + System.currentTimeMillis(), name = "") }) {
                Text(stringResource(R.string.limit_add))
            }
        }

        val leaving = book.pending.mapNotNull { it.change as? Change.Drop }
        // Apps are blocked by name, so one that isn't on the phone can be blocked as well as one that is.
        var choosing by remember { mutableStateOf(false) }
        var ticked by remember { mutableStateOf(emptySet<String>()) }
        WordList(
            stringResource(R.string.apps_title), stringResource(R.string.apps_about), stringResource(R.string.apps_hint),
            items = rules.apps, leaving = leaving.values(Listed.APP), clean = { it.trim().ifEmpty { null } },
            onAdd = { ask(Change.Add(Listed.APP, it)) }, onDrop = { ask(Change.Drop(Listed.APP, it)) },
        ) {
            OutlinedButton(onClick = { choosing = true }) { Text(stringResource(R.string.apps_choose)) }
        }
        if (choosing) {
            AppPicker(
                chosen = ticked,
                onChange = { ticked = it },
                onClose = {
                    ticked.forEach { ask(Change.Add(Listed.APP, it)) }
                    ticked = emptySet()
                    choosing = false
                },
                title = stringResource(R.string.apps_choose),
                byName = true,
                fixed = rules.apps,
            )
        }
        WordList(
            stringResource(R.string.sites_title), stringResource(R.string.sites_about), stringResource(R.string.sites_hint),
            items = rules.sites, leaving = leaving.values(Listed.SITE), clean = Page::site, error = stringResource(R.string.sites_error),
            onAdd = { ask(Change.Add(Listed.SITE, it)) }, onDrop = { ask(Change.Drop(Listed.SITE, it)) },
        )
        WordList(
            stringResource(R.string.keywords_title), stringResource(R.string.keywords_about), stringResource(R.string.keywords_hint),
            items = rules.keywords, leaving = leaving.values(Listed.KEYWORD), clean = { it.trim().lowercase().ifEmpty { null } },
            onAdd = { ask(Change.Add(Listed.KEYWORD, it)) }, onDrop = { ask(Change.Drop(Listed.KEYWORD, it)) },
        )

        VersionFooter()
    }
}

private fun List<Change.Drop>.values(list: Listed): Set<String> = filter { it.list == list }.mapTo(HashSet()) { it.value }

private fun Context.me(): Uri = Uri.parse("package:$packageName")

// Phones differ in which settings pages they have; a missing one is not worth a crash.
private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: Exception) {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            // No settings to open.
        }
    }
}

/** A heading, a line about what is under it, and what is under it. */
@Composable
private fun Section(title: String, about: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        about?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
    }
}

/** Something the phone still has to be told before the app can do its work. */
@Composable
private fun Notice(title: String, body: String, action: String, onAction: () -> Unit, more: @Composable () -> Unit = {}) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction) { Text(action) }
            more()
        }
    }
}

/** Whether the rules are locked, with the way in and the way out. */
@Composable
private fun Lock(
    locked: Boolean,
    period: String,
    waitHours: Int,
    unlocking: Boolean,
    admin: Boolean,
    ask: (Change) -> Unit,
    onStopAdmin: () -> Unit,
) {
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        colors = if (locked) CardDefaults.cardColors(colors.primaryContainer, colors.onPrimaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (locked) R.string.lock_on_title else R.string.lock_off_title), style = MaterialTheme.typography.titleMedium)
            Text(
                if (locked) stringResource(R.string.lock_on_body, period) else stringResource(R.string.lock_off_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (locked) {
                OutlinedButton(onClick = { ask(Change.Unlock) }, enabled = !unlocking) { Text(stringResource(R.string.unlock_ask)) }
            } else {
                Button(onClick = { confirming = true }) { Text(stringResource(R.string.lock)) }
            }
            TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.wait_change, period)) }
            // Unlocked, this is the first step of removing the app.
            if (!locked && admin) TextButton(onClick = onStopAdmin) { Text(stringResource(R.string.admin_stop)) }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.lock_confirm_title)) },
            text = { Text(stringResource(R.string.lock_confirm_body, period)) },
            confirmButton = {
                TextButton(onClick = {
                    ask(Change.Lock)
                    confirming = false
                }) { Text(stringResource(R.string.lock)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.wait_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.wait_about), style = MaterialTheme.typography.bodyMedium)
                    for (hours in WAITS) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                ask(Change.Wait(hours))
                                choosing = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = hours == waitHours, onClick = null, modifier = Modifier.padding(12.dp))
                            Text(Words.period(context, hours))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

// The waiting periods on offer, in hours: from one hour to a week.
private val WAITS = listOf(1, 6, 12, 24, 48, 72, 168)

/**
 * A list of short entries with a box to add one. Tapping an entry takes it off; one that
 * is waiting to come off ([leaving]) is struck through until it does. [more] goes under the box.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordList(
    title: String,
    about: String,
    hint: String,
    items: Set<String>,
    leaving: Set<String>,
    clean: (String) -> String?,
    onAdd: (String) -> Unit,
    onDrop: (String) -> Unit,
    error: String? = null,
    more: @Composable () -> Unit = {},
) {
    var entry by rememberSaveable { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val add = {
        val value = clean(entry)
        if (value == null) {
            wrong = entry.isNotBlank()
        } else {
            onAdd(value)
            entry = ""
        }
    }

    Section(title, about) {
        if (items.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in items) {
                    val remove = stringResource(R.string.remove_item, item)
                    InputChip(
                        selected = false,
                        onClick = { onDrop(item) },
                        label = { Text(item, textDecoration = if (item in leaving) TextDecoration.LineThrough else null) },
                        trailingIcon = { Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.semantics { contentDescription = remove },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = entry,
                onValueChange = {
                    entry = it
                    wrong = false
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(hint) },
                isError = wrong,
                supportingText = if (wrong && error != null) ({ Text(error) }) else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            Button(onClick = add, enabled = entry.isNotBlank(), modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.add)) }
        }
        more()
    }
}

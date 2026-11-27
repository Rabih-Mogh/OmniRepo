package app.safeguard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import app.safeguard.R
import app.safeguard.core.Book
import app.safeguard.core.Change
import app.safeguard.core.Limit
import app.safeguard.core.Listed
import app.safeguard.data.Store
import app.safeguard.guard.Admin

/** The time limits: each one's use today and closed hours, opening to be changed. */
@Composable
internal fun LimitsPage(limits: List<Limit>, today: Map<String, Long>, onOpen: (Limit) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    PageFrame(stringResource(R.string.limits_title), onBack) {
        About(stringResource(R.string.limits_about))
        for (limit in limits) {
            Card(onClick = { onOpen(limit) }, modifier = Modifier.fillMaxWidth()) {
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
        OutlinedButton(onClick = { onOpen(Limit(id = "limit-" + System.currentTimeMillis(), name = "")) }) {
            Text(stringResource(R.string.limit_add))
        }
    }
}

/** The blocked apps. They go by name, so one that isn't on the phone can be blocked as well as one that is. */
@Composable
internal fun AppsPage(apps: Set<String>, leaving: Set<String>, ask: (Change) -> Unit, onBack: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    var ticked by remember { mutableStateOf(emptySet<String>()) }
    ListPage(
        stringResource(R.string.apps_title), stringResource(R.string.apps_about), stringResource(R.string.apps_hint),
        items = apps, leaving = leaving, clean = { it.trim().ifEmpty { null } },
        onAdd = { ask(Change.Add(Listed.APP, it)) }, onDrop = { ask(Change.Drop(Listed.APP, it)) }, onBack = onBack,
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
            fixed = apps,
        )
    }
}

/**
 * A page holding a list of short entries with a box to add one. Tapping an entry takes it
 * off; one that is waiting to come off ([leaving]) is struck through until it does. [more]
 * goes under the box.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ListPage(
    title: String,
    about: String,
    hint: String,
    items: Set<String>,
    leaving: Set<String>,
    clean: (String) -> String?,
    onAdd: (String) -> Unit,
    onDrop: (String) -> Unit,
    onBack: () -> Unit,
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

    PageFrame(title, onBack) {
        About(about)
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

/** Whether the rules are locked, with the way in and the way out; the changes that are waiting; and how long they wait. */
@Composable
internal fun LockPage(book: Book, ask: (Change) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val rules = book.rules
    val period = Words.period(context, rules.waitHours)
    var confirming by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    PageFrame(stringResource(R.string.lock_page), onBack) {
        Card(
            Modifier.fillMaxWidth(),
            colors = if (rules.locked) CardDefaults.cardColors(colors.primaryContainer, colors.onPrimaryContainer) else CardDefaults.cardColors(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (rules.locked) R.string.lock_on_title else R.string.lock_off_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (rules.locked) stringResource(R.string.lock_on_body, period) else stringResource(R.string.lock_off_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (rules.locked) {
                    OutlinedButton(onClick = { ask(Change.Unlock) }, enabled = book.pending.none { it.change == Change.Unlock }) {
                        Text(stringResource(R.string.unlock_ask))
                    }
                } else {
                    Button(onClick = { confirming = true }) { Text(stringResource(R.string.lock)) }
                }
            }
        }

        if (book.pending.isNotEmpty()) {
            Section(stringResource(R.string.waiting_title)) {
                for (pending in book.pending) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Words.change(context, pending.change, rules))
                            Text(
                                stringResource(R.string.waiting_in, Words.left(context, pending.left)),
                                style = MaterialTheme.typography.bodySmall, color = colors.tertiary,
                            )
                        }
                        TextButton(onClick = { Store.cancel(context, pending.change) }) { Text(stringResource(R.string.withdraw)) }
                    }
                }
            }
        }

        Section(stringResource(R.string.wait_title), stringResource(R.string.wait_about)) {
            Column {
                for (hours in WAITS) {
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = hours != rules.waitHours) { ask(Change.Wait(hours)) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = hours == rules.waitHours, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(Words.period(context, hours))
                    }
                }
            }
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
}

// The waiting periods on offer, in hours: from one hour to a week.
private val WAITS = listOf(1, 6, 12, 24, 48, 72, 168)

/** What the phone has to have switched on for the app to do its work, each with the way to its settings page. */
@Composable
internal fun SetupPage(setup: Setup, locked: Boolean, onStopAdmin: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    PageFrame(stringResource(R.string.setup_title), onBack) {
        Step(
            on = setup.watching, onTitle = stringResource(R.string.watch_on_title),
            offTitle = stringResource(R.string.watch_off_title), body = stringResource(R.string.watch_off_body),
            action = stringResource(R.string.watch_open), onAction = { context.open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        ) {
            if (!setup.watching) {
                TextButton(onClick = { context.open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, context.me())) }) {
                    Text(stringResource(R.string.watch_info), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        Step(
            on = setup.admin, onTitle = stringResource(R.string.admin_on_title),
            offTitle = stringResource(R.string.admin_off_title), body = stringResource(R.string.admin_off_body),
            action = stringResource(R.string.admin_allow), onAction = { context.open(Admin.ask(context)) },
        ) {
            // Unlocked, this is the first step of removing the app.
            if (setup.admin && !locked) TextButton(onClick = onStopAdmin) { Text(stringResource(R.string.admin_stop)) }
        }
        Step(
            on = setup.installs, onTitle = stringResource(R.string.installs_on_title),
            offTitle = stringResource(R.string.installs_off_title), body = stringResource(R.string.installs_off_body),
            action = stringResource(R.string.installs_allow),
            onAction = { context.open(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, context.me())) },
        )
    }
}

/** One thing the phone has to be told: said in a line once it is [on], and with what to do about it until then. */
@Composable
private fun Step(
    on: Boolean,
    onTitle: String,
    offTitle: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    more: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        colors = if (on) CardDefaults.cardColors() else CardDefaults.cardColors(colors.errorContainer, colors.onErrorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (on) onTitle else offTitle, style = MaterialTheme.typography.titleMedium)
            if (!on) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onAction) { Text(action) }
            }
            more()
        }
    }
}

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

/** A line about what a page is for. */
@Composable
private fun About(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A heading, a line about what is under it, and what is under it. */
@Composable
private fun Section(title: String, about: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        about?.let { About(it) }
        content()
    }
}

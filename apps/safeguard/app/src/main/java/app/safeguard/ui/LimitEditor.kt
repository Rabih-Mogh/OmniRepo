package app.safeguard.ui

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.safeguard.R
import app.safeguard.core.Hours
import app.safeguard.core.Limit
import app.safeguard.core.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.util.EnumSet

/**
 * One limit, to set up or change: what it covers, how many minutes a day it allows, and
 * the hours it is closed. [wait] is the waiting period in words when the rules are locked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LimitEditor(limit: Limit, fresh: Boolean, wait: String?, onSave: (Limit) -> Unit, onRemove: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)

    var name by remember { mutableStateOf(limit.name) }
    var apps by remember { mutableStateOf(limit.apps) }
    var sites by remember { mutableStateOf(limit.sites.joinToString(", ")) }
    var allowance by remember { mutableStateOf(fresh || limit.minutesPerDay != null) }
    var minutes by remember { mutableStateOf((limit.minutesPerDay ?: 30).toString()) }
    var closed by remember { mutableStateOf(limit.hours != null) }
    var from by remember { mutableStateOf(limit.hours?.from ?: (9 * 60)) }
    var to by remember { mutableStateOf(limit.hours?.to ?: (17 * 60)) }
    var days by remember { mutableStateOf<Set<DayOfWeek>>(limit.hours?.days ?: EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)) }

    var choosing by remember { mutableStateOf(false) }
    // The end of the closed hours that is being set: true for the start, false for the end.
    var setting by remember { mutableStateOf<Boolean?>(null) }

    val websites = sites.split(',', ' ', '\n', '،').mapNotNull(Page::site).toSet()
    val save = {
        onSave(
            limit.copy(
                name = name.trim().ifEmpty { apps.firstOrNull()?.let { label(context, it) } ?: websites.first() },
                apps = apps,
                sites = websites,
                minutesPerDay = if (allowance) (minutes.toIntOrNull() ?: 0).coerceIn(0, 24 * 60) else null,
                hours = if (closed && days.isNotEmpty()) Hours(from, to, days) else null,
            ),
        )
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(if (fresh) stringResource(R.string.limit_new) else limit.name, style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.limit_name)) },
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.limit_apps), style = MaterialTheme.typography.titleMedium)
            if (apps.isEmpty()) {
                Text(stringResource(R.string.limit_apps_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (app in apps) {
                        val shown = remember(app) { label(context, app) }
                        val remove = stringResource(R.string.remove_item, shown)
                        InputChip(
                            selected = false,
                            onClick = { apps = apps - app },
                            label = { Text(shown) },
                            trailingIcon = { Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.semantics { contentDescription = remove },
                        )
                    }
                }
            }
            OutlinedButton(onClick = { choosing = true }) { Text(stringResource(R.string.limit_choose)) }
        }

        OutlinedTextField(
            value = sites, onValueChange = { sites = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.limit_sites)) },
            supportingText = { Text(stringResource(R.string.limit_sites_about)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        Toggle(stringResource(R.string.limit_allowance), allowance) { allowance = it }
        if (allowance) {
            OutlinedTextField(
                value = minutes, onValueChange = { text -> minutes = text.filter(Char::isDigit).take(4) }, singleLine = true,
                label = { Text(stringResource(R.string.limit_minutes)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        Toggle(stringResource(R.string.limit_closed), closed) { closed = it }
        if (closed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { setting = true }) { Text(stringResource(R.string.limit_from, Words.time(context, from))) }
                OutlinedButton(onClick = { setting = false }) { Text(stringResource(R.string.limit_to, Words.time(context, to))) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (day in DayOfWeek.values()) {
                    FilterChip(
                        selected = day in days,
                        onClick = { days = if (day in days) days - day else days + day },
                        label = { Text(Words.day(context, day)) },
                    )
                }
            }
        }

        wait?.let {
            Text(
                stringResource(R.string.limit_locked_note, it),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // A limit has to be on something.
            Button(onClick = save, enabled = apps.isNotEmpty() || websites.isNotEmpty()) { Text(stringResource(R.string.save)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.weight(1f))
            if (!fresh) TextButton(onClick = onRemove) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
        }
    }

    if (choosing) AppPicker(chosen = apps, onChange = { apps = it }, onClose = { choosing = false })
    setting?.let { start ->
        TimeDialog(
            minute = if (start) from else to,
            onPick = {
                if (start) from = it else to = it
                setting = null
            },
            onClose = { setting = null },
        )
    }
}

@Composable
private fun Toggle(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = on, onCheckedChange = onChange)
    }
}

/**
 * The apps on the phone's home screen, to tick some of them. [chosen] holds package names,
 * or the apps' names when [byName]. An app in [fixed] is shown ticked and can't be unticked here.
 */
@Composable
internal fun AppPicker(
    chosen: Set<String>,
    onChange: (Set<String>) -> Unit,
    onClose: () -> Unit,
    title: String = stringResource(R.string.limit_choose),
    byName: Boolean = false,
    fixed: Set<String> = emptySet(),
) {
    val context = LocalContext.current
    var all by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { all = withContext(Dispatchers.IO) { launchable(context) } }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(stringResource(R.string.search)) },
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(all.filter { it.second.contains(search.trim(), ignoreCase = true) }, key = { it.first }) { (app, shown) ->
                        val entry = if (byName) shown else app
                        val settled = fixed.any { it.equals(entry, ignoreCase = true) }
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !settled) {
                                onChange(if (entry in chosen) chosen - entry else chosen + entry)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = settled || entry in chosen, onCheckedChange = null, enabled = !settled, modifier = Modifier.padding(12.dp))
                            Text(shown)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.done)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minute: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val state = rememberTimePickerState(minute / 60, minute % 60, DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onClose,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}

/** An app's name as the phone shows it; its package name when it isn't on the phone. */
private fun label(context: Context, app: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(app, 0)).toString()
} catch (_: Exception) {
    app
}

/** Every app that has an icon on the home screen, as package name and name, in the order of their names. */
private fun launchable(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .filter { it.first != context.packageName }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }
}

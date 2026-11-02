package app.fajralarm.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.fajralarm.alarm.AlarmScheduler
import app.fajralarm.alarm.AlarmService
import app.fajralarm.core.AlarmAnchor
import app.fajralarm.core.AlarmPlanner
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.CalculationMethod
import app.fajralarm.core.DismissMission
import app.fajralarm.core.MatchStrictness
import app.fajralarm.core.HighLatitudeRule
import app.fajralarm.core.PrayerTimeCalculator
import app.fajralarm.data.LocationHelper
import app.fajralarm.data.SettingsStore
import app.fajralarm.data.SinkPhotos
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FajrTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var resumeTick by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        resumeTick++
        AlarmScheduler.reschedule(context)
        onPauseOrDispose { }
    }

    fun update(new: AlarmSettings) {
        settings = new
        store.save(new)
        AlarmScheduler.reschedule(context)
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        resumeTick++
    }
    var locating by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            locating = true
            fetchLocation(context) { lat, lng ->
                locating = false
                if (lat != null && lng != null) {
                    update(settings.copy(latitude = lat, longitude = lng, locationLabel = "Current location"))
                }
            }
        } else {
            Toast.makeText(context, "Enter your coordinates manually below", Toast.LENGTH_LONG).show()
        }
    }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val uri = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        update(settings.copy(soundUri = uri?.toString()))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Fajr Alarm", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        // resumeTick makes this re-read permission state whenever we return to the screen.
        PermissionWarnings(context, resumeTick, onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        })

        SummaryCard(context, settings, resumeTick)

        Section("Alarm") {
            SwitchRow("Wake me for Fajr", settings.enabled) { on ->
                if (on && !settings.hasLocation) {
                    Toast.makeText(context, "Set your location first", Toast.LENGTH_SHORT).show()
                }
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !NotificationManagerCompat.from(context).areNotificationsEnabled()
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                update(settings.copy(enabled = on))
            }

            Text("Ring relative to", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlarmAnchor.values().forEach { a ->
                    FilterChip(
                        selected = settings.anchor == a,
                        onClick = { update(settings.copy(anchor = a)) },
                        label = { Text(a.displayName) },
                    )
                }
            }

            Text(settings.describeOffset(), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = settings.offsetMinutes.toFloat(),
                onValueChange = { v ->
                    val snapped = (v / 5f).roundToInt() * 5
                    if (snapped != settings.offsetMinutes) settings = settings.copy(offsetMinutes = snapped)
                },
                onValueChangeFinished = { update(settings) },
                valueRange = -120f..60f,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("2 h before", style = MaterialTheme.typography.bodySmall)
                Text("1 h after", style = MaterialTheme.typography.bodySmall)
            }

            Text("Days", style = MaterialTheme.typography.labelLarge)
            DayChips(settings.days) { update(settings.copy(days = it)) }
        }

        Section("Location") {
            Text(
                if (settings.hasLocation) {
                    "${settings.locationLabel.ifBlank { "Saved location" }}: " +
                        "%.4f, %.4f".format(Locale.US, settings.latitude, settings.longitude)
                } else {
                    "No location set — Fajr time depends on where you are."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                enabled = !locating,
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        locating = true
                        fetchLocation(context) { lat, lng ->
                            locating = false
                            if (lat != null && lng != null) {
                                update(settings.copy(latitude = lat, longitude = lng, locationLabel = "Current location"))
                            }
                        }
                    } else {
                        locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                },
            ) { Text(if (locating) "Locating…" else "Use my current location") }

            ManualLocation(settings) { lat, lng ->
                update(settings.copy(latitude = lat, longitude = lng, locationLabel = "Manual"))
            }
        }

        Section("Calculation") {
            ChoiceRow(
                title = "Method",
                current = settings.method,
                options = CalculationMethod.values().toList(),
                label = { m -> if (m == CalculationMethod.CUSTOM) m.displayName else "${m.displayName} (${m.fajrAngle}°)" },
            ) { update(settings.copy(method = it)) }

            if (settings.method == CalculationMethod.CUSTOM) {
                Text("Fajr angle: %.1f°".format(settings.customFajrAngle), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.customFajrAngle.toFloat(),
                    onValueChange = { settings = settings.copy(customFajrAngle = (it * 2).roundToInt() / 2.0) },
                    onValueChangeFinished = { update(settings) },
                    valueRange = 10f..21f,
                )
            }

            ChoiceRow(
                title = "High-latitude rule",
                current = settings.highLatitudeRule,
                options = HighLatitudeRule.values().toList(),
                label = { it.displayName },
            ) { update(settings.copy(highLatitudeRule = it)) }

            Stepper(
                title = "Fajr adjustment",
                value = settings.fajrAdjustMinutes,
                suffix = "min",
                range = -30..30,
            ) { update(settings.copy(fajrAdjustMinutes = it)) }
            Text(
                "Use this to match your local mosque's timetable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("Sound & wake-up") {
            val soundName = remember(settings.soundUri) { soundTitle(context, settings.soundUri) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Fajr alarm sound")
                            .putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                settings.soundUri?.let(Uri::parse)
                                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                            )
                        soundPicker.launch(intent)
                    }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Alarm sound")
                Text(soundName, color = MaterialTheme.colorScheme.primary)
            }
            SwitchRow("Vibrate", settings.vibrate) { update(settings.copy(vibrate = it)) }
            SwitchRow("Gentle start (fade in volume)", settings.gradualVolume) { update(settings.copy(gradualVolume = it)) }

            ChoiceRow(
                title = "Snooze",
                current = settings.snoozeMinutes,
                options = listOf(0, 3, 5, 10, 15),
                label = { if (it == 0) "Off" else "$it minutes" },
            ) { update(settings.copy(snoozeMinutes = it)) }
            if (settings.snoozeMinutes > 0) {
                Stepper("Max snoozes", settings.maxSnoozes, "", 1..10) { update(settings.copy(maxSnoozes = it)) }
            }

        }

        Section("To turn the alarm off") {
            ChoiceRow(
                title = "Mission",
                current = settings.dismissMission,
                options = DismissMission.values().toList(),
                label = { it.displayName },
            ) { mission ->
                update(settings.copy(dismissMission = mission))
                if (mission == DismissMission.SINK_PHOTO && SinkPhotos.count(context) == 0) {
                    context.startActivity(Intent(context, SinkSetupActivity::class.java))
                }
            }
            Text(
                settings.dismissMission.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (settings.dismissMission == DismissMission.SINK_PHOTO) {
                val photoCount = remember(resumeTick) { SinkPhotos.count(context) }
                val cameraOk = remember(resumeTick) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (photoCount == 0) "No sink photos yet" else "$photoCount reference photo${if (photoCount == 1) "" else "s"}",
                        Modifier.weight(1f),
                    )
                    TextButton(onClick = { context.startActivity(Intent(context, SinkSetupActivity::class.java)) }) {
                        Text(if (photoCount == 0) "Set up" else "Manage / test")
                    }
                }
                if (photoCount == 0 || !cameraOk) {
                    Text(
                        if (!cameraOk) "Camera permission is off. Until it's on, the alarm falls back to a sum."
                        else "Until you add a photo, the alarm falls back to a sum.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ChoiceRow(
                    title = "Match strictness",
                    current = settings.photoStrictness,
                    options = MatchStrictness.values().toList(),
                    label = { it.displayName },
                ) { update(settings.copy(photoStrictness = it)) }
                if (settings.snoozeMinutes > 0) {
                    Text(
                        "Tip: set Snooze to Off so the photo is the only way to stop the alarm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !NotificationManagerCompat.from(context).areNotificationsEnabled()
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    ContextCompat.startForegroundService(context, AlarmService.startIntent(context))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Test the alarm now") }

        Spacer(Modifier.height(24.dp))
    }
}

private fun fetchLocation(context: Context, done: (Double?, Double?) -> Unit) {
    LocationHelper.requestOnce(context) { loc ->
        if (loc == null) {
            Toast.makeText(context, "Couldn't get a location. Is location turned on?", Toast.LENGTH_LONG).show()
            done(null, null)
        } else {
            done(loc.latitude, loc.longitude)
        }
    }
}

@Composable
private fun SummaryCard(context: Context, settings: AlarmSettings, tick: Int) {
    val zone = ZoneId.systemDefault()
    val now = remember(settings, tick) { ZonedDateTime.now(zone) }
    val params = settings.calcParams()
    val planned = remember(settings, now) { AlarmPlanner.nextAlarm(settings, now) }
    val times = remember(settings, now) {
        planned?.times ?: params?.let { PrayerTimeCalculator.compute(LocalDate.now(zone), zone, it) }
    }
    val snoozeUntil = remember(tick) { SettingsStore(context).snoozeUntil }
    val timeFmt = remember { android.text.format.DateFormat.getTimeFormat(context) }
    fun fmt(t: ZonedDateTime?) = t?.let { timeFmt.format(Date.from(it.toInstant())) } ?: "—"
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                !settings.hasLocation -> Text("Set your location to calculate Fajr.", style = MaterialTheme.typography.titleMedium)
                planned != null -> {
                    Text("Next alarm", style = MaterialTheme.typography.labelLarge)
                    Text(fmt(planned.triggerAt), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                    Text(relativeDay(planned.triggerAt, now, dayFmt), style = MaterialTheme.typography.bodyLarge)
                }
                settings.enabled && settings.days.isEmpty() -> Text("No days selected.", style = MaterialTheme.typography.titleMedium)
                settings.enabled -> Text(
                    "Fajr can't be determined at this location right now (polar day/night).",
                    style = MaterialTheme.typography.titleMedium,
                )
                else -> Text("Alarm is off", style = MaterialTheme.typography.titleMedium)
            }
            if (times != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${relativeDay(times.date.atStartOfDay(zone), now, dayFmt)} · Fajr ${fmt(times.fajr)} · Sunrise ${fmt(times.sunrise)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (snoozeUntil > System.currentTimeMillis()) {
                Text("Snoozed until ${timeFmt.format(Date(snoozeUntil))}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun relativeDay(t: ZonedDateTime, now: ZonedDateTime, fmt: DateTimeFormatter): String {
    val d = t.toLocalDate()
    val today = now.toLocalDate()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> t.format(fmt)
    }
}

@Composable
private fun PermissionWarnings(
    context: Context,
    @Suppress("UNUSED_PARAMETER") tick: Int,
    onRequestNotifications: () -> Unit,
) {
    val notificationsOff = !NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exactOff = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val fullScreenOff = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    if (notificationsOff) {
        Warning("Notifications are off, so the alarm can't show.", "Allow", onRequestNotifications)
    }
    if (exactOff && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Warning("Exact alarms are not allowed; the alarm may ring late.", "Fix") {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
            )
        }
    }
    if (fullScreenOff && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Warning("Full-screen alarms are blocked; you'll only get a notification.", "Fix") {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
            )
        }
    }
}

@Composable
private fun Warning(text: String, action: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    // Start the week on the user's locale-preferred first day.
    val first = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val ordered = (0L until 7L).map { first.plus(it) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ordered.forEach { d ->
            FilterChip(
                selected = d in days,
                onClick = { onChange(if (d in days) days - d else days + d) },
                label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
            )
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    current: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title)
        Spacer(Modifier.width(16.dp))
        Text(label(current), color = MaterialTheme.colorScheme.primary)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { opt ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = opt == current, onClick = { onSelect(opt); open = false })
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = opt == current, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label(opt))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun Stepper(title: String, value: Int, suffix: String, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        TextButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Text("−") }
        Text(
            (if (value > 0 && range.first < 0) "+$value" else "$value") + if (suffix.isNotEmpty()) " $suffix" else "",
            Modifier.width(64.dp),
        )
        TextButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Text("+") }
    }
}

@Composable
private fun ManualLocation(settings: AlarmSettings, onSave: (Double, Double) -> Unit) {
    var lat by remember(settings.latitude) { mutableStateOf(settings.latitude?.toString() ?: "") }
    var lng by remember(settings.longitude) { mutableStateOf(settings.longitude?.toString() ?: "") }
    val latVal = lat.trim().replace(',', '.').toDoubleOrNull()
    val lngVal = lng.trim().replace(',', '.').toDoubleOrNull()
    val valid = latVal != null && lngVal != null && latVal in -90.0..90.0 && lngVal in -180.0..180.0

    Text("Or enter coordinates", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = lat, onValueChange = { lat = it }, label = { Text("Latitude") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = lng, onValueChange = { lng = it }, label = { Text("Longitude") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
    TextButton(enabled = valid, onClick = { onSave(latVal!!, lngVal!!) }) { Text("Save coordinates") }
}

private fun soundTitle(context: Context, uri: String?): String =
    if (uri == null) {
        "Default alarm"
    } else {
        runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull() ?: "Custom sound"
    }

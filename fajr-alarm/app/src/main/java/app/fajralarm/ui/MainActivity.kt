package app.fajralarm.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import app.fajralarm.core.HighLatitudeRule
import app.fajralarm.core.PrayerTimeCalculator
import app.fajralarm.data.LocationHelper
import app.fajralarm.data.SettingsStore
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
        enableEdgeToEdge()
        setContent {
            FajrTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        Text(
            "Fajr Alarm",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )

        // resumeTick makes this re-read permission state whenever we return to the screen.
        PermissionWarnings(context, resumeTick, onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        })

        SummaryCard(context, settings, resumeTick) { on ->
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

        Section("Alarm") {
            Text("Ring relative to", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val anchors = AlarmAnchor.values()
                anchors.forEachIndexed { i, a ->
                    SegmentedButton(
                        selected = settings.anchor == a,
                        onClick = { update(settings.copy(anchor = a)) },
                        shape = SegmentedButtonDefaults.itemShape(i, anchors.size),
                    ) { Text(a.displayName) }
                }
            }

            Text(
                settings.describeOffset(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
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
                Text("2 h before", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("1 h after", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("Days", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            DayPicker(settings.days) { update(settings.copy(days = it)) }
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
            FilledTonalButton(
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
            ) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (locating) "Locating…" else "Use my current location")
            }

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
                Text("Alarm sound", style = MaterialTheme.typography.bodyLarge)
                ValueWithChevron(soundName)
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
                // The test screen asks for the camera, which the mission can't work without.
                if (mission == DismissMission.SINK_PHOTO &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                ) {
                    context.startActivity(Intent(context, SinkSetupActivity::class.java))
                }
            }
            Text(
                settings.dismissMission.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (settings.dismissMission == DismissMission.SINK_PHOTO) {
                val cameraOk = remember(resumeTick) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Any real sink works, no photos needed", Modifier.weight(1f))
                    TextButton(onClick = { context.startActivity(Intent(context, SinkSetupActivity::class.java)) }) {
                        Text("Test it")
                    }
                }
                if (!cameraOk) {
                    Text(
                        "Camera permission is off. Until it's on, the alarm falls back to a sum.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (settings.snoozeMinutes > 0) {
                    Text(
                        "Tip: set Snooze to Off so finding a sink is the only way to stop the alarm.",
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
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Test the alarm now")
        }

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
private fun SummaryCard(context: Context, settings: AlarmSettings, tick: Int, onEnabledChange: (Boolean) -> Unit) {
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
    val onHero = Silhouette

    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(GoldenMorning),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (settings.enabled) "NEXT ALARM" else "ALARM OFF",
                    style = MaterialTheme.typography.labelMedium,
                    color = onHero.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Silhouette,
                        checkedThumbColor = Color(0xFFFFE59A),
                        checkedIconColor = Silhouette,
                    ),
                )
            }
            when {
                !settings.hasLocation -> Text("Set your location to calculate Fajr.", style = MaterialTheme.typography.titleLarge, color = onHero)
                planned != null -> {
                    Text(fmt(planned.triggerAt), style = MaterialTheme.typography.displayLarge, color = onHero)
                    Text(relativeDay(planned.triggerAt, now, dayFmt), style = MaterialTheme.typography.titleMedium, color = onHero.copy(alpha = 0.85f))
                }
                settings.enabled && settings.days.isEmpty() -> Text("No days selected.", style = MaterialTheme.typography.titleLarge, color = onHero)
                settings.enabled -> Text(
                    "Fajr can't be determined at this location right now (polar day/night).",
                    style = MaterialTheme.typography.titleMedium,
                    color = onHero,
                )
                else -> Text("Turn it on to wake for Fajr.", style = MaterialTheme.typography.titleLarge, color = onHero)
            }
            if (times != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimePill("Fajr", fmt(times.fajr), Modifier.weight(1f))
                    TimePill("Sunrise", fmt(times.sunrise), Modifier.weight(1f))
                }
                Text(
                    "Prayer times for ${relativeDay(times.date.atStartOfDay(zone), now, dayFmt).lowercase(Locale.getDefault())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = onHero.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (snoozeUntil > System.currentTimeMillis()) {
                Text(
                    "Snoozed until ${timeFmt.format(Date(snoozeUntil))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHero,
                )
            }
        }
    }
}

@Composable
private fun TimePill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Color.White.copy(alpha = 0.38f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Silhouette.copy(alpha = 0.75f))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Silhouette)
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
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp),
        )
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
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

/** Seven equal circles on one line, whatever the screen width or language. */
@Composable
private fun DayPicker(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    // Start the week on the user's locale-preferred first day.
    val first = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val ordered = (0L until 7L).map { first.plus(it) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ordered.forEach { d ->
            val on = d in days
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .toggleable(value = on, role = Role.Checkbox) { onChange(if (on) days - d else days + d) }
                    .semantics { contentDescription = d.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ValueWithChevron(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(16.dp))
        ValueWithChevron(label(current))
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
                                .clip(MaterialTheme.shapes.small)
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
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Text("−") }
        Text(
            (if (value > 0 && range.first < 0) "+$value" else "$value") + if (suffix.isNotEmpty()) " $suffix" else "",
            Modifier.width(72.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Text("+") }
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

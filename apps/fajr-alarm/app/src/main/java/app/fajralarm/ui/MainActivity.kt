package app.fajralarm.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.fajralarm.R
import app.fajralarm.alarm.AlarmScheduler
import app.fajralarm.alarm.AlarmService
import app.fajralarm.core.AlarmAnchor
import app.fajralarm.core.AlarmPlanner
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.AsrEnd
import app.fajralarm.core.AsrRule
import app.fajralarm.core.IshaEnd
import app.fajralarm.core.WidgetColors
import app.fajralarm.core.WidgetTheme
import app.fajralarm.core.CalculationMethod
import app.fajralarm.core.DismissMission
import app.fajralarm.core.HighLatitudeRule
import app.fajralarm.core.PrayerTime
import app.fajralarm.core.PrayerTimeCalculator
import app.fajralarm.core.parseCoordinate
import app.fajralarm.data.LocationHelper
import app.fajralarm.data.SettingsStore
import app.fajralarm.data.SinkProof
import app.fajralarm.data.StopReason
import app.fajralarm.data.appExitSince
import app.fajralarm.widget.CurrentPrayerWidget
import app.fajralarm.widget.PrayerTimesWidget
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
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
private const val PAGE_PRAYERS = 0

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
    /** Asks for the notification permission if it is still missing; the setting is kept either way. */
    fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var locating by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            locating = true
            fetchLocation(context) { lat, lng ->
                locating = false
                if (lat != null && lng != null) {
                    update(settings.copy(latitude = lat, longitude = lng, locationLabel = LOCATION_CURRENT))
                }
            }
        } else {
            Toast.makeText(context, R.string.toast_enter_coordinates, Toast.LENGTH_LONG).show()
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
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )

        UpdateDialog()

        // resumeTick makes this re-read permission state whenever we return to the screen.
        PermissionWarnings(context, resumeTick, onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        })

        // Two pages: the day's prayers, and the alarm that wakes you for Fajr. The settings
        // on each are folded up to a line that says what is set.
        var page by rememberSaveable { mutableIntStateOf(PAGE_PRAYERS) }
        TabRow(selectedTabIndex = page, containerColor = Color.Transparent) {
            listOf(R.string.tab_prayers, R.string.tab_fajr_alarm).forEachIndexed { index, title ->
                Tab(selected = page == index, onClick = { page = index }, text = { Text(stringResource(title)) })
            }
        }

        if (page == PAGE_PRAYERS) {
            PrayerTimesCard(
                settings, resumeTick,
                onFullScreenChange = { on ->
                    if (on) askForNotifications()
                    update(settings.copy(fullScreenAlerts = on))
                },
                onAlertSoundChange = { update(settings.copy(alertSound = it)) },
            ) { prayer, on ->
                if (on) askForNotifications()
                update(settings.copy(alerts = if (on) settings.alerts + prayer else settings.alerts - prayer))
            }

            MoonCard(settings.latitude, resumeTick)

            val locationText = if (settings.hasLocation) {
                stringResource(
                    R.string.location_value,
                    settings.locationName(context),
                    "%.4f, %.4f".format(Locale.US, settings.latitude, settings.longitude).leftToRight(),
                )
            } else {
                stringResource(R.string.location_none)
            }
            Section(stringResource(R.string.section_location), summary = locationText) {
                Text(locationText, style = MaterialTheme.typography.bodyMedium)
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
                                    update(settings.copy(latitude = lat, longitude = lng, locationLabel = LOCATION_CURRENT))
                                }
                            }
                        } else {
                            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                ) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (locating) R.string.locating else R.string.use_current_location))
                }

                ManualLocation(settings) { lat, lng ->
                    update(settings.copy(latitude = lat, longitude = lng, locationLabel = LOCATION_MANUAL))
                }
            }

            val methodLabel: @Composable (CalculationMethod) -> String = { m ->
                if (m == CalculationMethod.CUSTOM) {
                    stringResource(m.label)
                } else {
                    stringResource(R.string.method_with_angle, stringResource(m.label), m.fajrAngle)
                }
            }
            Section(stringResource(R.string.section_calculation), summary = methodLabel(settings.method)) {
                ChoiceRow(
                    title = stringResource(R.string.method),
                    current = settings.method,
                    options = CalculationMethod.values().toList(),
                    label = methodLabel,
                ) { update(settings.copy(method = it)) }
                if (settings.method == CalculationMethod.DAR_AL_FATWA_LEBANON) {
                    Text(
                        stringResource(R.string.method_dar_al_fatwa_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (settings.method == CalculationMethod.CUSTOM) {
                    Text(stringResource(R.string.fajr_angle, settings.customFajrAngle), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.customFajrAngle.toFloat(),
                        onValueChange = { settings = settings.copy(customFajrAngle = (it * 2).roundToInt() / 2.0) },
                        onValueChangeFinished = { update(settings) },
                        valueRange = 10f..21f,
                    )
                }

                if (settings.method == CalculationMethod.CUSTOM) {
                    Text(stringResource(R.string.isha_angle, settings.customIshaAngle), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.customIshaAngle.toFloat(),
                        onValueChange = { settings = settings.copy(customIshaAngle = (it * 2).roundToInt() / 2.0) },
                        onValueChangeFinished = { update(settings) },
                        valueRange = 10f..21f,
                    )
                }

                ChoiceRow(
                    title = stringResource(R.string.asr_rule),
                    current = settings.asrRule,
                    options = AsrRule.values().toList(),
                    label = { stringResource(it.label) },
                ) { update(settings.copy(asrRule = it)) }

                ChoiceRow(
                    title = stringResource(R.string.asr_end),
                    current = settings.asrEnd,
                    options = AsrEnd.values().toList(),
                    label = { stringResource(it.label) },
                ) { update(settings.copy(asrEnd = it)) }

                ChoiceRow(
                    title = stringResource(R.string.isha_end),
                    current = settings.ishaEnd,
                    options = IshaEnd.values().toList(),
                    label = { stringResource(it.label) },
                ) { update(settings.copy(ishaEnd = it)) }
                Text(
                    stringResource(R.string.ends_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ChoiceRow(
                    title = stringResource(R.string.high_latitude_rule),
                    current = settings.highLatitudeRule,
                    options = HighLatitudeRule.values().toList(),
                    label = { stringResource(it.label) },
                ) { update(settings.copy(highLatitudeRule = it)) }

                Stepper(
                    title = stringResource(R.string.imsak_minutes),
                    value = settings.imsakMinutes,
                    suffix = stringResource(R.string.minutes_short),
                    range = 0..30,
                ) { update(settings.copy(imsakMinutes = it)) }
                Text(
                    stringResource(R.string.imsak_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // The minutes that are not zero, e.g. "Fajr +2, Maghrib -1".
                val adjusted = (listOf(PrayerTime.FAJR to settings.fajrAdjustMinutes) + settings.adjustMinutes.toList())
                    .filter { it.second != 0 }
                    .map { (prayer, minutes) -> stringResource(prayer.label) + " " + "%+d".format(Locale.US, minutes).leftToRight() }
                Fold(
                    stringResource(R.string.adjustments_title),
                    summary = adjusted.joinToString().ifEmpty { stringResource(R.string.adjustments_none) },
                ) {
                    Stepper(
                        title = stringResource(R.string.fajr_adjustment),
                        value = settings.fajrAdjustMinutes,
                        suffix = stringResource(R.string.minutes_short),
                        range = -30..30,
                    ) { update(settings.copy(fajrAdjustMinutes = it)) }
                    listOf(PrayerTime.DHUHR, PrayerTime.ASR, PrayerTime.MAGHRIB, PrayerTime.ISHA).forEach { prayer ->
                        Stepper(
                            title = stringResource(R.string.prayer_adjustment, stringResource(prayer.label)),
                            value = settings.adjustMinutes[prayer] ?: 0,
                            suffix = stringResource(R.string.minutes_short),
                            range = -30..30,
                        ) { update(settings.copy(adjustMinutes = settings.adjustMinutes + (prayer to it))) }
                    }
                    Text(
                        stringResource(R.string.fajr_adjustment_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Section(stringResource(R.string.section_lock_widgets), summary = stringResource(settings.widgetTheme.label)) {
                SwitchRow(
                    stringResource(R.string.lock_screen_countdown),
                    settings.lockScreenCountdown,
                    subtitle = stringResource(R.string.lock_screen_countdown_hint),
                ) { on ->
                    if (on) askForNotifications()
                    update(settings.copy(lockScreenCountdown = on))
                }
                Text(
                    stringResource(R.string.widgets_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ChoiceRow(
                    title = stringResource(R.string.widget_theme),
                    current = settings.widgetTheme,
                    options = WidgetTheme.values().toList(),
                    label = { stringResource(it.label) },
                ) { update(settings.copy(widgetTheme = it)) }
                if (settings.widgetTheme == WidgetTheme.CUSTOM) {
                    WidgetColorPicker(settings.widgetColors) { update(settings.copy(widgetColors = it)) }
                }
                TextButton(onClick = { addWidget(context, PrayerTimesWidget::class.java) }) {
                    Text(stringResource(R.string.widget_add_times))
                }
                TextButton(onClick = { addWidget(context, CurrentPrayerWidget::class.java) }) {
                    Text(stringResource(R.string.widget_add_now))
                }
                Text(
                    stringResource(R.string.widget_add_by_hand),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SummaryCard(context, settings, resumeTick) { on ->
                if (on && !settings.hasLocation) {
                    Toast.makeText(context, R.string.toast_set_location_first, Toast.LENGTH_SHORT).show()
                }
                if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !NotificationManagerCompat.from(context).areNotificationsEnabled()
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                update(settings.copy(enabled = on))
            }

            LastAlarmCard(context, resumeTick)

            Section(stringResource(R.string.section_alarm), summary = settings.describeOffset(context)) {
                Text(stringResource(R.string.ring_relative_to), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val anchors = AlarmAnchor.values()
                    anchors.forEachIndexed { i, a ->
                        SegmentedButton(
                            selected = settings.anchor == a,
                            onClick = { update(settings.copy(anchor = a)) },
                            shape = SegmentedButtonDefaults.itemShape(i, anchors.size),
                        ) { Text(stringResource(a.label)) }
                    }
                }

                Text(
                    settings.describeOffset(context),
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
                    Text(
                        stringResource(R.string.offset_earliest),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.offset_latest),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(stringResource(R.string.days), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                DayPicker(settings.days) { update(settings.copy(days = it)) }
            }

            val soundName = remember(settings.soundUri) { soundTitle(context, settings.soundUri) }
            val snoozeLabel: @Composable (Int) -> String =
                { if (it == 0) stringResource(R.string.snooze_off) else pluralStringResource(R.plurals.minutes, it, it) }
            Section(
                stringResource(R.string.section_sound),
                summary = "$soundName · ${stringResource(R.string.snooze)}: ${snoozeLabel(settings.snoozeMinutes)}",
            ) {
                val pickerTitle = stringResource(R.string.sound_picker_title)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, pickerTitle)
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
                    Text(stringResource(R.string.alarm_sound), style = MaterialTheme.typography.bodyLarge)
                    ValueWithChevron(soundName)
                }
                SwitchRow(stringResource(R.string.vibrate), settings.vibrate) { update(settings.copy(vibrate = it)) }
                SwitchRow(stringResource(R.string.gentle_start), settings.gradualVolume) { update(settings.copy(gradualVolume = it)) }

                ChoiceRow(
                    title = stringResource(R.string.snooze),
                    current = settings.snoozeMinutes,
                    options = listOf(0, 3, 5, 10, 15),
                    label = snoozeLabel,
                ) { update(settings.copy(snoozeMinutes = it)) }
                if (settings.snoozeMinutes > 0) {
                    Stepper(stringResource(R.string.max_snoozes), settings.maxSnoozes, "", 1..10) { update(settings.copy(maxSnoozes = it)) }
                }
            }

            Section(stringResource(R.string.section_turn_off), summary = stringResource(settings.dismissMission.label)) {
                ChoiceRow(
                    title = stringResource(R.string.mission),
                    current = settings.dismissMission,
                    options = DismissMission.values().toList(),
                    label = { stringResource(it.label) },
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
                    stringResource(settings.dismissMission.summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (settings.dismissMission == DismissMission.SINK_PHOTO) {
                    val cameraOk = remember(resumeTick) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.sink_no_photos), Modifier.weight(1f))
                        TextButton(onClick = { context.startActivity(Intent(context, SinkSetupActivity::class.java)) }) {
                            Text(stringResource(R.string.test_it))
                        }
                    }
                    if (!cameraOk) {
                        Text(
                            stringResource(R.string.camera_off_falls_back),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (settings.snoozeMinutes > 0) {
                        Text(
                            stringResource(R.string.sink_snooze_sunrise),
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
                Text(stringResource(R.string.test_alarm_now))
            }
        }

        VersionFooter()
        Spacer(Modifier.height(24.dp))
    }
}

/** Asks the home screen to place [widget]; where it can't, says how to add it by hand. */
private fun addWidget(context: Context, widget: Class<*>) {
    val manager = AppWidgetManager.getInstance(context)
    if (manager == null || !manager.isRequestPinAppWidgetSupported ||
        !manager.requestPinAppWidget(ComponentName(context, widget), null, null)
    ) {
        Toast.makeText(context, R.string.widget_add_by_hand, Toast.LENGTH_LONG).show()
    }
}

private fun fetchLocation(context: Context, done: (Double?, Double?) -> Unit) {
    LocationHelper.requestOnce(context) { loc ->
        if (loc == null) {
            Toast.makeText(context, R.string.toast_location_failed, Toast.LENGTH_LONG).show()
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
    val dayFmt = remember {
        // The phone's own digits, to match the times beside it.
        val locale = Locale.getDefault()
        DateTimeFormatter.ofPattern("EEEE d MMM", locale).withDecimalStyle(DecimalStyle.of(locale))
    }
    val today = now.toLocalDate()
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
                    stringResource(if (settings.enabled) R.string.next_alarm else R.string.alarm_off),
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
                !settings.hasLocation -> Text(
                    stringResource(R.string.summary_set_location),
                    style = MaterialTheme.typography.titleLarge,
                    color = onHero,
                )
                planned != null -> {
                    Text(fmt(planned.triggerAt), style = MaterialTheme.typography.displayLarge, color = onHero)
                    Text(
                        when (planned.triggerAt.toLocalDate()) {
                            today -> stringResource(R.string.today)
                            today.plusDays(1) -> stringResource(R.string.tomorrow)
                            else -> planned.triggerAt.format(dayFmt)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = onHero.copy(alpha = 0.85f),
                    )
                }
                settings.enabled && settings.days.isEmpty() -> Text(
                    stringResource(R.string.summary_no_days),
                    style = MaterialTheme.typography.titleLarge,
                    color = onHero,
                )
                settings.enabled -> Text(
                    stringResource(R.string.summary_polar),
                    style = MaterialTheme.typography.titleMedium,
                    color = onHero,
                )
                else -> Text(stringResource(R.string.summary_turn_on), style = MaterialTheme.typography.titleLarge, color = onHero)
            }
            if (times != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimePill(stringResource(R.string.anchor_fajr), fmt(times.fajr), Modifier.weight(1f))
                    TimePill(stringResource(R.string.anchor_sunrise), fmt(times.sunrise), Modifier.weight(1f))
                }
                Text(
                    when (times.date) {
                        today -> stringResource(R.string.prayer_times_today)
                        today.plusDays(1) -> stringResource(R.string.prayer_times_tomorrow)
                        else -> stringResource(R.string.prayer_times_date, times.date.format(dayFmt))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = onHero.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (snoozeUntil > System.currentTimeMillis()) {
                Text(
                    stringResource(R.string.snoozed_until, timeFmt.format(Date(snoozeUntil))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHero,
                )
            }
        }
    }
}

/** What happened the last time the alarm rang, so an alarm that stopped by itself can be explained. */
@Composable
private fun LastAlarmCard(context: Context, tick: Int) {
    val last = remember(tick) { SettingsStore(context).lastAlarm() } ?: return
    if (AlarmService.ringing.value) return
    val exit = remember(tick) { if (last.reason == null) appExitSince(context, last.rangAt) else null }
    val proof = remember(tick) {
        SinkProof.path(context)?.takeIf { last.reason == StopReason.SINK }
            ?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
    }
    val timeFmt = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val dateFmt = remember { android.text.format.DateFormat.getMediumDateFormat(context) }
    val rangAt = timeFmt.format(Date(last.rangAt))
    val rangOn = dateFmt.format(Date(last.rangAt))
    Section(stringResource(R.string.section_last_alarm)) {
        Text(
            when {
                last.reason != null && last.stoppedAt > 0 ->
                    stringResource(last.reason.sentence, rangAt, rangOn, timeFmt.format(Date(last.stoppedAt)))
                // The app was closed while ringing without getting the chance to note why.
                exit != null -> stringResource(exit, rangAt, rangOn)
                else -> stringResource(R.string.last_unknown, rangAt, rangOn)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        proof?.let {
            Text(
                stringResource(R.string.sink_proof_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Image(
                it,
                contentDescription = stringResource(R.string.sink_proof_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium),
            )
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

/**
 * A titled card. With a [summary] it starts folded up to that one line and opens when
 * tapped, so the screen stays short until a setting is wanted.
 */
@Composable
internal fun Section(title: String, summary: String? = null, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(summary == null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(MaterialTheme.shapes.small)
                .then(if (summary != null) Modifier.clickable { open = !open } else Modifier)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (summary != null) FoldArrow(open)
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (open) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    content()
                }
            } else {
                Text(
                    summary.orEmpty(),
                    Modifier
                        .fillMaxWidth()
                        .clickable { open = true }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A row inside a section that opens to show more: its title, and what is set while it is closed. */
@Composable
internal fun Fold(title: String, summary: String? = null, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (!open && summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        FoldArrow(open)
    }
    if (open) content()
}

@Composable
private fun FoldArrow(open: Boolean) {
    Icon(
        if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
    )
}

/** The colours offered for a widget of your own, as 0xRRGGBB. */
private val WIDGET_SWATCHES = listOf(
    0x000000, 0x17223F, 0x2F3B52, 0x1F3D2B, 0x4A1F2A, 0xFFFFFF, 0xFBF8F1,
    0xFFD27A, 0xF2994A, 0xE6B3C9, 0x8AB4F8, 0x7FD1C7, 0x9BD67D,
)

/** Picks the three colours of your own widget theme and how solid its background is. */
@Composable
private fun WidgetColorPicker(colors: WidgetColors, onChange: (WidgetColors) -> Unit) {
    SwatchRow(stringResource(R.string.widget_color_background), colors.background) { onChange(colors.copy(background = it)) }
    var opacity by remember(colors.backgroundOpacity) { mutableIntStateOf(colors.backgroundOpacity) }
    Text(stringResource(R.string.widget_color_opacity, opacity), style = MaterialTheme.typography.labelLarge)
    Slider(
        value = opacity.toFloat(),
        onValueChange = { opacity = (it / 5f).roundToInt() * 5 },
        onValueChangeFinished = { onChange(colors.copy(backgroundOpacity = opacity)) },
        valueRange = 0f..100f,
    )
    SwatchRow(stringResource(R.string.widget_color_text), colors.text) { onChange(colors.copy(text = it)) }
    SwatchRow(stringResource(R.string.widget_color_highlight), colors.highlight) { onChange(colors.copy(highlight = it)) }
}

@Composable
private fun SwatchRow(title: String, selected: Int, onSelect: (Int) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WIDGET_SWATCHES.forEach { rgb ->
            val name = "$title #%06X".format(Locale.US, rgb).leftToRight()
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF000000 or rgb.toLong()))
                    .border(
                        width = if (rgb == selected) 3.dp else 1.dp,
                        color = if (rgb == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .selectable(selected = rgb == selected, role = Role.RadioButton) { onSelect(rgb) }
                    .semantics { contentDescription = name },
            )
        }
    }
}

@Composable
internal fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
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
internal fun <T> ChoiceRow(
    title: String,
    current: T,
    options: List<T>,
    label: @Composable (T) -> String,
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
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun Stepper(title: String, value: Int, suffix: String, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Text("−") }
        // The phone's own digits, with a "+" on values above zero when the range also goes below it.
        val number = String.format(Locale.getDefault(), if (value > 0 && range.first < 0) "%+d" else "%d", value)
        Text(
            number.leftToRight() + if (suffix.isNotEmpty()) " $suffix" else "",
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
    val latVal = parseCoordinate(lat)
    val lngVal = parseCoordinate(lng)
    val valid = latVal != null && lngVal != null && latVal in -90.0..90.0 && lngVal in -180.0..180.0

    Text(stringResource(R.string.or_enter_coordinates), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = lat, onValueChange = { lat = it }, label = { Text(stringResource(R.string.latitude)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = lng, onValueChange = { lng = it }, label = { Text(stringResource(R.string.longitude)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
    TextButton(enabled = valid, onClick = { onSave(latVal!!, lngVal!!) }) { Text(stringResource(R.string.save_coordinates)) }
}

private fun soundTitle(context: Context, uri: String?): String =
    if (uri == null) {
        context.getString(R.string.sound_default)
    } else {
        runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull()
            ?: context.getString(R.string.sound_custom)
    }

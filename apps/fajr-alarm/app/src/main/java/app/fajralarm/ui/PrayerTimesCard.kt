package app.fajralarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fajralarm.R
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.AlertSound
import app.fajralarm.core.PrayerClock
import app.fajralarm.core.PrayerTime
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The day's times: which prayer it is and how long it has left, then every time with
 * when it starts and ends and a bell that turns its notification on. Shows nothing
 * until a location is set.
 */
@Composable
internal fun PrayerTimesCard(
    settings: AlarmSettings,
    tick: Int,
    onFullScreenChange: (Boolean) -> Unit,
    onAlertSoundChange: (AlertSound) -> Unit,
    onAlertChange: (PrayerTime, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val params = settings.calcParams() ?: return
    var now by remember(tick) { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = ZonedDateTime.now()
        }
    }
    val state = remember(params, now) { PrayerClock.at(now, params) } ?: return
    val current = state.current
    val ends = state.currentEnds
    val dayFmt = remember {
        val locale = Locale.getDefault()
        DateTimeFormatter.ofPattern("EEEE d MMM", locale).withDecimalStyle(DecimalStyle.of(locale))
    }

    Section(stringResource(R.string.section_prayers)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource((current ?: state.next).prayer.label),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (current != null && ends != null) {
                        stringResource(R.string.time_left, Duration.between(now, ends).describe(context))
                    } else {
                        stringResource(R.string.starts_in, Duration.between(now, state.next.at).describe(context))
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                stringResource(R.string.next_prayer, stringResource(state.next.prayer.label), state.next.at.clockTime(context)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            if (state.day.date == now.toLocalDate()) stringResource(R.string.today) else state.day.date.format(dayFmt),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column {
            state.day.moments().forEach { moment ->
                val isCurrent = moment == current
                val passed = !isCurrent && !moment.at.isAfter(now)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = when {
                        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                        moment.prayer.obligatory -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val weight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                    PrayerMark(moment.prayer, color, Modifier.alpha(if (passed) 0.6f else 1f))
                    Text(
                        stringResource(moment.prayer.label),
                        Modifier.weight(1f).padding(start = 10.dp, end = 8.dp).alpha(if (passed) 0.6f else 1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = weight,
                        color = color,
                    )
                    // When it starts, and under that when its time runs out. Imsak, sunrise and midnight are single moments.
                    Column(Modifier.alpha(if (passed) 0.6f else 1f), horizontalAlignment = Alignment.End) {
                        Text(
                            moment.at.clockTime(context),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = weight,
                            color = color,
                        )
                        state.dayEnds[moment.prayer]?.let { end ->
                            Text(
                                stringResource(R.string.until_time, end.clockTime(context)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrent) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (moment.prayer == PrayerTime.FAJR) {
                        // Fajr has the alarm, so no bell.
                        Spacer(Modifier.size(44.dp))
                    } else {
                        Bell(
                            on = moment.prayer in settings.alerts,
                            description = stringResource(R.string.alert_at, stringResource(moment.prayer.label)),
                        ) { onAlertChange(moment.prayer, it) }
                    }
                }
            }
        }
        SwitchRow(
            stringResource(R.string.full_screen_alerts),
            settings.fullScreenAlerts,
            subtitle = stringResource(R.string.full_screen_alerts_hint),
            onChange = onFullScreenChange,
        )
        ChoiceRow(
            title = stringResource(R.string.alert_sound),
            current = settings.alertSound,
            options = AlertSound.values().toList(),
            label = { stringResource(it.label) },
            onSelect = onAlertSoundChange,
        )
        // What the bells, the optional times and the icons mean: there when wanted, out of the way otherwise.
        Fold(stringResource(R.string.times_about)) {
            for (hint in listOf(R.string.alerts_hint, R.string.optional_times_hint, R.string.nafl_hint)) {
                Text(
                    stringResource(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The icon before a prayer's name, pink for a voluntary one. Imsak, sunrise and midnight are
 * not prayers and keep the gap.
 */
@Composable
private fun PrayerMark(prayer: PrayerTime, color: Color, modifier: Modifier = Modifier) {
    val icon = prayer.icon
    if (icon == null) {
        Spacer(modifier.size(20.dp))
    } else {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(if (prayer.nafl) R.string.mark_nafl else R.string.mark_obligatory),
            tint = if (prayer.nafl) MaterialTheme.colorScheme.tertiary else color,
            modifier = modifier.size(20.dp),
        )
    }
}

/** A bell that is gold when on and struck through when off. */
@Composable
private fun Bell(on: Boolean, description: String, onChange: (Boolean) -> Unit) {
    val tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Notifications,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .drawWithContent {
                    drawContent()
                    if (!on) {
                        drawLine(
                            tint,
                            Offset(size.width * 0.1f, size.height * 0.1f),
                            Offset(size.width * 0.9f, size.height * 0.9f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                },
        )
    }
}

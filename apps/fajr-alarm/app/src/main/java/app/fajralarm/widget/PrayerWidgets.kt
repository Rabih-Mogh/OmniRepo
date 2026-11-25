package app.fajralarm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import app.fajralarm.R
import app.fajralarm.core.PrayerNow
import app.fajralarm.prayer.CurrentPrayerText
import app.fajralarm.prayer.PrayerTicker
import app.fajralarm.prayer.setCountdown
import app.fajralarm.ui.MainActivity
import app.fajralarm.ui.shortLabel
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

/** The home screen widget with every time of the day. */
class PrayerTimesWidget : PrayerWidget()

/** The home screen widget with the current prayer and its countdown. */
class CurrentPrayerWidget : PrayerWidget()

/**
 * Both widgets are drawn by [PrayerWidgets] from [PrayerTicker.refresh], which also
 * keeps them on the right prayer as the day goes on.
 */
abstract class PrayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = PrayerTicker.refresh(context)

    // The last widget is gone: the wake-ups may no longer be needed.
    override fun onDisabled(context: Context) = PrayerTicker.refresh(context)
}

object PrayerWidgets {

    private const val REQ_OPEN = 23

    /** Whether either widget is on a home screen. */
    fun anyPlaced(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return providers(context).any { manager.getAppWidgetIds(it).isNotEmpty() }
    }

    /** Redraws every placed widget for [state]; null means there is no location to go by. */
    fun update(context: Context, state: PrayerNow?) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val (times, current) = providers(context)
        manager.getAppWidgetIds(times).takeIf { it.isNotEmpty() }?.let { manager.updateAppWidget(it, timesViews(context, state)) }
        manager.getAppWidgetIds(current).takeIf { it.isNotEmpty() }?.let { manager.updateAppWidget(it, currentViews(context, state)) }
    }

    private fun providers(context: Context) = listOf(
        ComponentName(context, PrayerTimesWidget::class.java),
        ComponentName(context, CurrentPrayerWidget::class.java),
    )

    internal fun timesViews(context: Context, state: PrayerNow?): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_times).apply {
            setOnClickPendingIntent(R.id.widget_root, openApp(context))
            removeAllViews(R.id.widget_prayers)
            removeAllViews(R.id.widget_optional)
            if (state == null) {
                setTextViewText(R.id.widget_headline, context.getString(R.string.widget_set_location))
                setViewVisibility(R.id.widget_countdown, View.INVISIBLE)
                setTextViewText(R.id.widget_date, "")
                return@apply
            }
            val text = CurrentPrayerText.of(context, state)
            setTextViewText(R.id.widget_headline, text.headline)
            setViewVisibility(R.id.widget_countdown, View.VISIBLE)
            setCountdown(R.id.widget_countdown, text.countsDownTo)
            setTextViewText(R.id.widget_date, state.day.date.format(dayFormat()))

            // The gold cell is the prayer whose time it is. With none, or late at night
            // when it is still last evening's Isha, it is the next prayer.
            val moments = state.day.moments()
            val marked = state.current?.takeIf { it in moments } ?: state.next
            for (moment in moments) {
                val isMarked = moment == marked
                val cell = RemoteViews(context.packageName, R.layout.widget_times_cell).apply {
                    setTextViewText(R.id.cell_name, context.getString(moment.prayer.shortLabel))
                    setTextViewText(R.id.cell_time, moment.at.shortClockTime(context))
                    if (isMarked) {
                        setInt(R.id.cell, "setBackgroundResource", R.drawable.widget_cell_current)
                        setTextColor(R.id.cell_name, context.getColor(R.color.widget_on_gold))
                        setTextColor(R.id.cell_time, context.getColor(R.color.widget_on_gold))
                    }
                }
                addView(if (moment.prayer.obligatory) R.id.widget_prayers else R.id.widget_optional, cell)
            }
        }

    internal fun currentViews(context: Context, state: PrayerNow?): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_current).apply {
            setOnClickPendingIntent(R.id.widget_root, openApp(context))
            if (state == null) {
                setTextViewText(R.id.widget_headline, context.getString(R.string.widget_set_location))
                setViewVisibility(R.id.widget_countdown, View.GONE)
                setViewVisibility(R.id.widget_next, View.GONE)
                return@apply
            }
            val text = CurrentPrayerText.of(context, state)
            setTextViewText(R.id.widget_headline, text.headline)
            setViewVisibility(R.id.widget_countdown, View.VISIBLE)
            setCountdown(R.id.widget_countdown, text.countsDownTo)
            setViewVisibility(R.id.widget_next, View.VISIBLE)
            setTextViewText(R.id.widget_next, text.next)
        }

    /** A clock time without AM or PM, e.g. "4:31": nine of them have to fit in the widget. */
    private fun ZonedDateTime.shortClockTime(context: Context): String {
        val locale = Locale.getDefault()
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        return format(DateTimeFormatter.ofPattern(pattern, locale).withDecimalStyle(DecimalStyle.of(locale)))
    }

    private fun dayFormat(): DateTimeFormatter {
        val locale = Locale.getDefault()
        return DateTimeFormatter.ofPattern("EEE d MMM", locale).withDecimalStyle(DecimalStyle.of(locale))
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, REQ_OPEN,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

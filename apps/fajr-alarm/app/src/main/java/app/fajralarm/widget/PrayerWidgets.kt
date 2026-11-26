package app.fajralarm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import app.fajralarm.R
import app.fajralarm.core.AlarmPlanner
import app.fajralarm.core.AlarmSettings
import app.fajralarm.core.PrayerNow
import app.fajralarm.prayer.CurrentPrayerText
import app.fajralarm.prayer.PrayerTicker
import app.fajralarm.prayer.setCountdown
import app.fajralarm.ui.MainActivity
import app.fajralarm.ui.clockTime
import app.fajralarm.ui.icon
import app.fajralarm.ui.label
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

    // Placed or resized. A home screen that doesn't choose between the sizes itself has to
    // be given the new one, and one that does may only now have said so.
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        PrayerTicker.refresh(context)

    // The last widget is gone: the wake-ups may no longer be needed.
    override fun onDisabled(context: Context) = PrayerTicker.refresh(context)
}

/**
 * Draws the widgets. Each one shows the table of the day's times, and more of it the
 * bigger it is made: [TIMES_SIZES] and [CURRENT_SIZES] are the sizes where something
 * is added, and what is added is decided from the size alone. The colours come from
 * the theme chosen in the settings.
 */
object PrayerWidgets {

    private const val REQ_OPEN = 23

    // "All times", by height: under 250dp the ten times sit in two columns, from
    // 250dp in one. 140dp adds a countdown inside the current prayer's row, 170dp a
    // countdown to the next prayer where the current one ends before it starts (Fajr, or
    // an end chosen in the settings), 330dp the alarm and the method.
    // In one column each prayer has its start and its end: as "4:52 – 6:20", or from
    // 290dp wide in two columns of clock times with AM and PM.
    private const val ROW_COUNTDOWN_HEIGHT = 140f
    private const val NEXT_LINE_HEIGHT = 170f
    private const val ONE_COLUMN_HEIGHT = 250f
    private const val FOOTER_HEIGHT = 330f
    private const val ENDS_WIDTH = 290f
    internal val TIMES_SIZES = listOf(
        SizeF(220f, 110f), SizeF(220f, ROW_COUNTDOWN_HEIGHT), SizeF(220f, NEXT_LINE_HEIGHT),
        SizeF(220f, ONE_COLUMN_HEIGHT), SizeF(ENDS_WIDTH, ONE_COLUMN_HEIGHT),
        SizeF(220f, FOOTER_HEIGHT), SizeF(ENDS_WIDTH, FOOTER_HEIGHT),
    )

    // "Current prayer": one cell holds the prayer's name and the countdown. From 110dp
    // wide the headline is a sentence, with the next prayer from 70dp tall and the date
    // from 110dp. Then the table: in two columns where it is 220dp wide, in one from
    // 290dp tall. In one column it has the prayers' ends too where it is 220dp wide.
    private const val CURRENT_SENTENCE_WIDTH = 110f
    private const val CURRENT_NEXT_HEIGHT = 70f
    private const val CURRENT_DATE_HEIGHT = 110f
    private const val CURRENT_TABLE_HEIGHT = 190f
    private const val CURRENT_TABLE_WIDTH = 220f
    private const val CURRENT_ONE_COLUMN_HEIGHT = 290f
    private const val CURRENT_ONE_COLUMN_WIDTH = 160f
    internal val CURRENT_SIZES = listOf(
        SizeF(40f, 40f),
        SizeF(CURRENT_SENTENCE_WIDTH, 40f), SizeF(CURRENT_SENTENCE_WIDTH, CURRENT_NEXT_HEIGHT),
        SizeF(CURRENT_SENTENCE_WIDTH, CURRENT_DATE_HEIGHT),
        SizeF(CURRENT_TABLE_WIDTH, CURRENT_TABLE_HEIGHT),
        SizeF(CURRENT_ONE_COLUMN_WIDTH, CURRENT_ONE_COLUMN_HEIGHT), SizeF(CURRENT_TABLE_WIDTH, CURRENT_ONE_COLUMN_HEIGHT),
    )

    /** Whether either widget is on a home screen. */
    fun anyPlaced(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return providers(context).any { manager.getAppWidgetIds(it).isNotEmpty() }
    }

    /** Redraws every placed widget for [state]; null means there is no location to go by. */
    fun update(context: Context, state: PrayerNow?, settings: AlarmSettings) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val (times, current) = providers(context)
        draw(context, manager, times, TIMES_SIZES) { timesViews(context, state, settings, it) }
        draw(context, manager, current, CURRENT_SIZES) { currentViews(context, state, settings, it) }
    }

    private fun providers(context: Context) = listOf(
        ComponentName(context, PrayerTimesWidget::class.java),
        ComponentName(context, CurrentPrayerWidget::class.java),
    )

    private fun draw(
        context: Context,
        manager: AppWidgetManager,
        provider: ComponentName,
        sizes: List<SizeF>,
        views: (SizeF) -> RemoteViews,
    ) {
        for (id in manager.getAppWidgetIds(provider)) {
            val options = manager.getAppWidgetOptions(id)
            val drawing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && choosesSize(options)) {
                // One drawing for each size: the home screen shows the biggest that fits,
                // and changes between them by itself while the widget is resized.
                RemoteViews(sizes.associateWith(views))
            } else {
                views(sizeOf(context, options) ?: sizes.first())
            }
            manager.updateAppWidget(id, drawing)
        }
    }

    /**
     * Whether the home screen picks among several drawings by the widget's size, as
     * those from Android 12 do: they say so by listing the sizes the widget can have.
     */
    internal fun choosesSize(options: Bundle): Boolean = options.containsKey(AppWidgetManager.OPTION_APPWIDGET_SIZES)

    /** The size, in dp, the home screen gave a widget; null where it doesn't say. */
    internal fun sizeOf(context: Context, options: Bundle): SizeF? {
        // The home screen reports the narrow and tall extremes of both orientations.
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val width = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
        )
        val height = options.getInt(
            if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
        )
        return if (width > 0 && height > 0) SizeF(width.toFloat(), height.toFloat()) else null
    }

    /** The "All times" widget as it is drawn at [size], in dp. */
    internal fun timesViews(context: Context, state: PrayerNow?, settings: AlarmSettings, size: SizeF): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_times).apply {
            val palette = WidgetPalette.of(settings)
            val oneColumn = size.height >= ONE_COLUMN_HEIGHT
            val footer = size.height >= FOOTER_HEIGHT
            setOnClickPendingIntent(R.id.widget_root, openApp(context))
            background(context, palette)
            paint(context, R.id.widget_headline, TEXT, palette.accent)
            paint(context, R.id.widget_countdown, TEXT, palette.text)
            for (id in listOf(R.id.widget_date, R.id.widget_next, R.id.widget_next_countdown, R.id.widget_footer)) {
                paint(context, id, TEXT, palette.muted)
            }
            show(R.id.widget_countdown, state != null, otherwise = View.INVISIBLE)
            // Only where it says something new: often the headline already counts down to the next prayer.
            show(
                R.id.widget_next_line,
                state?.currentEnds != null && state.currentEnds != state.next.at && size.height >= NEXT_LINE_HEIGHT,
            )
            show(R.id.widget_footer, state != null && footer)
            if (state == null) {
                setTextViewText(R.id.widget_headline, context.getString(R.string.widget_set_location))
                setTextViewText(R.id.widget_date, "")
                clearTable()
                return@apply
            }
            val text = CurrentPrayerText.of(context, state)
            setTextViewText(R.id.widget_headline, text.headline)
            setCountdown(R.id.widget_countdown, text.countsDownTo)
            setTextViewText(R.id.widget_date, state.day.date.format(dayFormat()))
            setTextViewText(R.id.widget_next, context.getString(R.string.next_prayer_in, context.getString(state.next.prayer.label)))
            setCountdown(R.id.widget_next_countdown, state.next.at)
            if (footer) setTextViewText(R.id.widget_footer, footerText(context, settings))
            fillTable(
                context, state, settings, palette,
                oneColumn = oneColumn,
                times = when {
                    !oneColumn -> Times.START_SHORT
                    size.width >= ENDS_WIDTH -> Times.COLUMNS
                    else -> Times.RANGE
                },
                countdown = text.takeIf { size.height >= ROW_COUNTDOWN_HEIGHT },
                textSp = when {
                    footer -> 15f
                    oneColumn || size.height >= NEXT_LINE_HEIGHT -> 13f
                    else -> 12f
                },
            )
        }

    /** The "Current prayer" widget as it is drawn at [size], in dp. */
    internal fun currentViews(context: Context, state: PrayerNow?, settings: AlarmSettings, size: SizeF): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_current).apply {
            val palette = WidgetPalette.of(settings)
            val sentence = size.width >= CURRENT_SENTENCE_WIDTH
            val oneColumn = size.height >= CURRENT_ONE_COLUMN_HEIGHT && size.width >= CURRENT_ONE_COLUMN_WIDTH
            val table = oneColumn || (size.height >= CURRENT_TABLE_HEIGHT && size.width >= CURRENT_TABLE_WIDTH)
            setOnClickPendingIntent(R.id.widget_root, openApp(context))
            background(context, palette)
            paint(context, R.id.widget_headline, TEXT, palette.accent)
            paint(context, R.id.widget_countdown, TEXT, palette.text)
            paint(context, R.id.widget_next, TEXT, palette.muted)
            paint(context, R.id.widget_date, TEXT, palette.muted)
            // One cell has no room to spare at its edges.
            val edge = context.resources.displayMetrics.density.let { ((if (sentence) 10 else 3) * it).toInt() to (6 * it).toInt() }
            setViewPadding(R.id.widget_content, edge.first, edge.second, edge.first, edge.second)
            show(R.id.widget_countdown, state != null)
            // With no prayer current the next one is in the headline; its clock time is then in the table.
            show(
                R.id.widget_next,
                state != null && sentence && size.height >= CURRENT_NEXT_HEIGHT && (state.current != null || !table),
            )
            show(R.id.widget_date, state != null && sentence && size.height >= CURRENT_DATE_HEIGHT)
            show(R.id.widget_table, state != null && table)
            setTextViewTextSize(R.id.widget_headline, TypedValue.COMPLEX_UNIT_SP, if (sentence) 12f else 11f)
            if (state == null) {
                setTextViewText(R.id.widget_headline, context.getString(R.string.widget_set_location))
                clearTable()
                return@apply
            }
            val text = CurrentPrayerText.of(context, state)
            // In one cell just the prayer's name: the countdown under it is to its end, or to its start.
            setTextViewText(
                R.id.widget_headline,
                if (sentence) text.headline else context.getString((state.current ?: state.next).prayer.shortLabel),
            )
            setCountdown(R.id.widget_countdown, text.countsDownTo)
            // One cell tall there is little room over and under the countdown, one cell wide beside it.
            setTextViewTextSize(
                R.id.widget_countdown, TypedValue.COMPLEX_UNIT_SP,
                when {
                    !sentence -> 13f
                    size.height >= CURRENT_NEXT_HEIGHT -> 22f
                    else -> 18f
                },
            )
            setTextViewText(R.id.widget_next, text.next)
            setTextViewText(R.id.widget_date, state.day.date.format(dayFormat()))
            if (table) {
                fillTable(
                    context, state, settings, palette, oneColumn,
                    times = when {
                        !oneColumn -> Times.START_SHORT
                        size.width >= CURRENT_TABLE_WIDTH -> Times.RANGE
                        else -> Times.START
                    },
                    // The big countdown is right above the table.
                    countdown = null,
                    textSp = if (oneColumn) 13f else 12f,
                )
            } else {
                clearTable()
            }
        }

    private const val TEXT = "setTextColor"
    private const val TINT = "setColorFilter"

    /**
     * Colours the view [viewId] through its setter [method]. A tone with a colour of
     * its own for dark mode is handed over as the pair from Android 12, so the home
     * screen switches by itself; before that the one for the phone's mode right now is used.
     */
    private fun RemoteViews.paint(context: Context, viewId: Int, method: String, tone: Tone) {
        when {
            tone.light == tone.dark -> setInt(viewId, method, tone.light)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> setColorInt(viewId, method, tone.light, tone.dark)
            else -> {
                val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
                setInt(viewId, method, if (night) tone.dark else tone.light)
            }
        }
    }

    private fun RemoteViews.background(context: Context, palette: WidgetPalette) {
        paint(context, R.id.widget_background, TINT, palette.background)
        setInt(R.id.widget_background, "setImageAlpha", palette.backgroundAlpha)
    }

    private fun RemoteViews.show(viewId: Int, shown: Boolean, otherwise: Int = View.GONE) =
        setViewVisibility(viewId, if (shown) View.VISIBLE else otherwise)

    private fun RemoteViews.clearTable() {
        removeAllViews(R.id.table_start)
        removeAllViews(R.id.table_end)
    }

    /** How much of a time's clock times a row of the table has room for. */
    private enum class Times {
        /** When it starts, cut to "4:31". */
        START_SHORT,

        /** When it starts, with AM or PM. */
        START,

        /** When a prayer starts and ends, as "4:52 – 6:20". */
        RANGE,

        /** The start and the end with AM or PM, each in a column under a heading. */
        COLUMNS,
    }

    /**
     * Fills the table with the times of the listed day, in the order they come. In two
     * columns they read down the first and then down the second. In one column there is
     * room for a bell beside the times that notify, and for when each prayer ends.
     * Each prayer has its icon before its name, pink for a voluntary one. With
     * [countdown] the gold row is twice as tall and counts down under the prayer's name.
     */
    private fun RemoteViews.fillTable(
        context: Context,
        state: PrayerNow,
        settings: AlarmSettings,
        palette: WidgetPalette,
        oneColumn: Boolean,
        times: Times,
        countdown: CurrentPrayerText?,
        textSp: Float,
    ) {
        clearTable()
        show(R.id.table_end, !oneColumn)
        val ends = times == Times.COLUMNS
        val plain = if (ends) R.layout.widget_times_row_wide else R.layout.widget_times_row
        val tall = if (ends) R.layout.widget_times_row_current_wide else R.layout.widget_times_row_current
        fun row(layout: Int, name: String, time: String, end: String, size: Float, nameTone: Tone, timeTone: Tone, endTone: Tone) =
            RemoteViews(context.packageName, layout).apply {
                setTextViewText(R.id.row_name, name)
                setTextViewText(R.id.row_time, time)
                setTextViewText(R.id.row_ends, end)
                for ((id, tone) in listOf(R.id.row_name to nameTone, R.id.row_time to timeTone, R.id.row_ends to endTone)) {
                    setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, size)
                    paint(context, id, TEXT, tone)
                }
            }

        if (ends) {
            val heading = palette.muted
            addView(
                R.id.table_start,
                row(plain, "", context.getString(R.string.widget_begins), context.getString(R.string.widget_ends), 11f, heading, heading, heading),
            )
        }

        // The gold row is the prayer whose time it is. With none, or late at night
        // when it is still last evening's Isha, it is the next prayer.
        val moments = state.day.moments()
        val marked = state.current?.takeIf { it in moments } ?: state.next
        val firstColumn = if (oneColumn) moments.size else (moments.size + 1) / 2
        // How many rows' height each column has used: the gold row with a countdown takes two.
        val used = intArrayOf(0, 0)
        moments.forEachIndexed { index, moment ->
            val isMarked = moment == marked
            // Everything before the next time of the day has been and gone.
            val passed = !isMarked && moment.at.isBefore(state.nextEvent.at)
            val faded = palette.passed.takeIf { passed }
            val onGold = palette.onGold.takeIf { isMarked }
            val end = state.dayEnds[moment.prayer]
            val counting = isMarked && countdown != null
            val cell = row(
                layout = if (counting) tall else plain,
                name = context.getString(moment.prayer.shortLabel),
                time = when (times) {
                    Times.START_SHORT -> moment.at.shortClockTime(context)
                    Times.RANGE -> listOfNotNull(moment.at, end).joinToString(" – ") { it.shortClockTime(context) }
                    Times.START, Times.COLUMNS -> moment.at.clockTime(context)
                },
                end = end?.clockTime(context).orEmpty(),
                size = textSp,
                nameTone = onGold ?: faded ?: if (moment.prayer.obligatory) palette.text else palette.muted,
                timeTone = onGold ?: faded ?: palette.text,
                endTone = onGold ?: faded ?: palette.muted,
            ).apply {
                if (isMarked) {
                    setViewVisibility(R.id.row_highlight, View.VISIBLE)
                    paint(context, R.id.row_highlight, TINT, palette.highlight)
                }
                moment.prayer.icon?.let { icon ->
                    setViewVisibility(R.id.row_mark, View.VISIBLE)
                    setImageViewResource(R.id.row_mark, icon)
                    val tone = when {
                        moment.prayer.nafl -> if (passed) palette.naflPassed else palette.nafl
                        else -> onGold ?: faded ?: palette.text
                    }
                    paint(context, R.id.row_mark, TINT, tone)
                }
                if (oneColumn && moment.prayer in settings.alerts) {
                    setViewVisibility(R.id.row_bell, View.VISIBLE)
                    paint(context, R.id.row_bell, TINT, onGold ?: faded ?: palette.muted)
                }
                if (counting && countdown != null) {
                    setTextViewText(
                        R.id.row_count_label,
                        context.getString(if (state.current == moment) R.string.row_ends_in else R.string.row_starts_in),
                    )
                    setCountdown(R.id.row_countdown, countdown.countsDownTo)
                    paint(context, R.id.row_count_label, TEXT, palette.onGold)
                    paint(context, R.id.row_countdown, TEXT, palette.onGold)
                }
            }
            val column = if (index < firstColumn) 0 else 1
            used[column] += if (counting) 2 else 1
            addView(if (column == 0) R.id.table_start else R.id.table_end, cell)
        }
        // Empty rows under the shorter column keep the two columns' rows level.
        if (!oneColumn) {
            val shorter = if (used[0] < used[1]) R.id.table_start else R.id.table_end
            repeat(kotlin.math.abs(used[0] - used[1])) {
                addView(shorter, RemoteViews(context.packageName, plain))
            }
        }
    }

    /** The bottom line of the tallest widget: the next alarm, then the method the times come from. */
    private fun footerText(context: Context, settings: AlarmSettings): String {
        val alarm = AlarmPlanner.nextAlarm(settings, ZonedDateTime.now())?.triggerAt
        val alarmText = if (alarm == null) {
            context.getString(R.string.widget_alarm_off)
        } else {
            val locale = Locale.getDefault()
            val day = alarm.format(DateTimeFormatter.ofPattern("EEE", locale))
            context.getString(R.string.widget_alarm, "$day ${alarm.clockTime(context)}")
        }
        return "$alarmText · ${context.getString(settings.method.label)}"
    }

    /** A clock time without AM or PM, e.g. "4:31", for where two of them have to fit side by side. */
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

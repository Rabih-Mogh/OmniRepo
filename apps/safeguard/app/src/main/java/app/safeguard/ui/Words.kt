package app.safeguard.ui

import android.content.Context
import app.safeguard.R
import app.safeguard.core.Change
import app.safeguard.core.Hours
import app.safeguard.core.Listed
import app.safeguard.core.Rules
import app.safeguard.core.Why
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Times, days, waits and reasons put into words, in the app's language. */
object Words {

    private fun locale(context: Context): Locale = context.resources.configuration.locales[0]

    /** A length of time given in hours: "6 hours", or "2 days" when it comes to whole days. */
    fun period(context: Context, hours: Int): String =
        if (hours % 24 == 0) {
            context.resources.getQuantityString(R.plurals.days, hours / 24, hours / 24)
        } else {
            context.resources.getQuantityString(R.plurals.hours, hours, hours)
        }

    /** What is left of a wait: "23 h 12 min". */
    fun left(context: Context, millis: Long): String {
        val minutes = (millis + 59_999) / 60_000
        return when {
            millis < 60_000 -> context.getString(R.string.left_soon)
            minutes < 60 -> context.getString(R.string.left_m, minutes)
            else -> context.getString(R.string.left_hm, minutes / 60, minutes % 60)
        }
    }

    /** A time of day given in minutes from midnight, as the phone writes times. */
    fun time(context: Context, minute: Int): String =
        LocalTime.of(minute / 60 % 24, minute % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale(context)))

    fun day(context: Context, day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale(context))

    /** "every day", "Mon–Fri" for a run of days, or the days one by one. */
    fun days(context: Context, days: Set<DayOfWeek>): String {
        val sorted = days.sorted()
        return when {
            sorted.size == 7 -> context.getString(R.string.every_day)
            sorted.size >= 3 && sorted.last().value - sorted.first().value == sorted.size - 1 ->
                day(context, sorted.first()) + "–" + day(context, sorted.last())
            else -> sorted.joinToString(context.getString(R.string.comma)) { day(context, it) }
        }
    }

    fun hours(context: Context, hours: Hours?): String = when {
        hours == null || hours.days.isEmpty() -> context.getString(R.string.limit_no_hours)
        hours.from == hours.to -> context.getString(R.string.limit_hours_all_day, days(context, hours.days))
        else -> context.getString(R.string.limit_hours, days(context, hours.days), time(context, hours.from), time(context, hours.to))
    }

    /** A waiting change, as a line in the list of them. */
    fun change(context: Context, change: Change, rules: Rules): String = when (change) {
        Change.Unlock -> context.getString(R.string.change_unlock)
        is Change.Drop -> context.getString(
            when (change.list) {
                Listed.SITE -> R.string.change_drop_site
                Listed.KEYWORD -> R.string.change_drop_keyword
                Listed.APP -> R.string.change_drop_app
            },
            change.value,
        )
        is Change.DropLimit ->
            context.getString(R.string.change_drop_limit, rules.limits.firstOrNull { it.id == change.id }?.name ?: change.id)
        is Change.PutLimit -> context.getString(R.string.change_put_limit, change.limit.name)
        is Change.Wait -> context.getString(R.string.change_wait, period(context, change.hours))
        // These never wait.
        is Change.Add, Change.Lock -> ""
    }

    /** Why something was closed, for the screen that comes up over it. */
    fun why(context: Context, why: Why): String = when (why) {
        is Why.Hours -> {
            val until = why.until
            if (until == null) {
                context.getString(R.string.why_hours_always, why.name)
            } else {
                val time = time(context, until.hour * 60 + until.minute)
                // Later than today, the day is said too.
                val at = if (until.toLocalDate() == LocalDate.now()) time else "${until.dayOfWeek.getDisplayName(TextStyle.FULL, locale(context))} $time"
                context.getString(R.string.why_hours, why.name, at)
            }
        }
        is Why.Spent -> context.getString(R.string.why_spent, why.name, why.minutes)
        is Why.Never -> context.getString(R.string.why_never, why.name)
        is Why.Site -> context.getString(R.string.why_site, why.site)
        is Why.Keyword -> context.getString(R.string.why_keyword, why.word)
        Why.Guarded -> context.getString(R.string.why_guarded)
    }
}

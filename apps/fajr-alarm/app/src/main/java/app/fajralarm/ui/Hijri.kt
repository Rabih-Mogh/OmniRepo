package app.fajralarm.ui

import android.content.Context
import app.fajralarm.R
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * The Hijri date of [day], e.g. "16 Jumada al-Akhirah 1448". It comes from the Umm
 * al-Qura calendar built into Android, which is calculated; [adjustDays] moves it to
 * follow a country that goes by sighting the moon.
 */
fun hijriDate(context: Context, day: LocalDate, adjustDays: Int = 0, withYear: Boolean = true): String {
    val hijri = HijrahDate.from(day.plusDays(adjustDays.toLong()))
    val month = context.resources.getStringArray(R.array.hijri_months)[hijri.get(ChronoField.MONTH_OF_YEAR) - 1]
    val dayOfMonth = hijri.get(ChronoField.DAY_OF_MONTH)
    return if (withYear) {
        context.getString(R.string.hijri_date, dayOfMonth, month, hijri.get(ChronoField.YEAR))
    } else {
        context.getString(R.string.hijri_date_short, dayOfMonth, month)
    }
}

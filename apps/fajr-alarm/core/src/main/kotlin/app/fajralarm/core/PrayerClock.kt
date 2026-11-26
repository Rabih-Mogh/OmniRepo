package app.fajralarm.core

import java.time.ZoneId
import java.time.ZonedDateTime

/** Where a moment falls among the prayers. */
data class PrayerNow(
    /** The prayer whose time it is and when it started, or null between sunrise and Dhuhr. */
    val current: PrayerMoment?,
    /**
     * What to count down to for [current]: the end chosen for it in the settings while
     * that is still ahead, then the end of its time. Null with no current prayer.
     */
    val currentEnds: ZonedDateTime?,
    /** The next of the five prayers. */
    val next: PrayerMoment,
    /** The next time of any kind, the optional ones included. */
    val nextEvent: PrayerMoment,
    /** The day [nextEvent] belongs to: the one to list. */
    val day: DailyTimes,
    /**
     * When the time of each of [day]'s prayers is shown as running out, the voluntary
     * ones included: for Asr and Isha that is the end chosen in the settings. Imsak,
     * sunrise and midnight have none.
     */
    val dayEnds: Map<PrayerTime, ZonedDateTime>,
    /** Every time still to come over the next two days or so, [nextEvent] first. */
    val upcoming: List<PrayerMoment>,
) {
    /** When what is shown next goes out of date: at [nextEvent], or sooner if the countdown runs out first. */
    val nextChange: ZonedDateTime
        get() = currentEnds?.takeIf { it.isBefore(nextEvent.at) } ?: nextEvent.at
}

object PrayerClock {

    /** Duha's time stops this long before Dhuhr, clear of the moment the sun is at its highest. */
    const val DUHA_BEFORE_DHUHR_MINUTES = 10L

    /** The sun is taken to turn yellow this long before it sets. */
    const val ASR_YELLOW_BEFORE_SUNSET_MINUTES = 20L

    /**
     * Fajr's time ends at sunrise and Isha's at the next Fajr; every other prayer's
     * ends when the next one starts. Asr and Isha can be shown as ending earlier, at a
     * point a school sets within their time ([PrayerCalcParams.asrEnd], [PrayerCalcParams.ishaEnd]):
     * the prayer stays the current one until its time really ends. Of the voluntary
     * ones, Duha's ends shortly before Dhuhr and the night prayer's at Fajr. Null with
     * no times to go by (polar day or night).
     */
    fun at(now: ZonedDateTime, params: PrayerCalcParams, zone: ZoneId = now.zone): PrayerNow? {
        val today = now.withZoneSameInstant(zone).toLocalDate()
        val timeline = (-1..2L)
            .map { PrayerTimeCalculator.compute(today.plusDays(it), zone, params) }
            .flatMap { day -> day.moments().map { it to day } }
            .sortedBy { it.first.at }

        val (nextEvent, day) = timeline.firstOrNull { it.first.at.isAfter(now) } ?: return null
        val next = timeline.firstOrNull { it.first.prayer.obligatory && it.first.at.isAfter(now) }?.first ?: return null

        fun firstAfter(started: PrayerMoment, ends: (PrayerTime) -> Boolean): ZonedDateTime? =
            timeline.firstOrNull { (m, _) -> m.at.isAfter(started.at) && ends(m.prayer) }?.first?.at

        /** When the time of a prayer that [started] is over. */
        fun endOf(started: PrayerMoment): ZonedDateTime? = when (started.prayer) {
            PrayerTime.FAJR -> firstAfter(started) { it == PrayerTime.SUNRISE }
            PrayerTime.DUHA -> firstAfter(started) { it == PrayerTime.DHUHR }
                ?.minusMinutes(DUHA_BEFORE_DHUHR_MINUTES)?.takeIf { it.isAfter(started.at) }
            PrayerTime.LAST_THIRD -> firstAfter(started) { it == PrayerTime.FAJR }
            PrayerTime.IMSAK, PrayerTime.SUNRISE, PrayerTime.MIDNIGHT -> null
            else -> firstAfter(started) { it.obligatory }
        }

        /** The end to show for it: the one chosen in the settings where it falls inside the prayer's time. */
        fun shownEndOf(started: PrayerMoment, itsDay: DailyTimes): ZonedDateTime? {
            val end = endOf(started) ?: return null
            val chosen = when (started.prayer) {
                PrayerTime.ASR -> when (params.asrEnd) {
                    AsrEnd.MAGHRIB -> null
                    AsrEnd.SHADOW_TWICE -> itsDay.asrShadowTwice
                    AsrEnd.SUN_YELLOWS -> itsDay.sunset?.minusMinutes(ASR_YELLOW_BEFORE_SUNSET_MINUTES)
                }
                PrayerTime.ISHA -> when (params.ishaEnd) {
                    IshaEnd.FAJR -> null
                    IshaEnd.MIDNIGHT -> itsDay.midnight
                    IshaEnd.FIRST_THIRD -> itsDay.firstThird
                }
                else -> null
            }
            return chosen?.takeIf { it.isAfter(started.at) && it.isBefore(end) } ?: end
        }

        val started = timeline.lastOrNull { it.first.prayer.obligatory && !it.first.at.isAfter(now) }
        val ends = started?.let { endOf(it.first) }
        val current = started?.first?.takeIf { ends != null && ends.isAfter(now) }
        val currentEnds = if (current == null) null else shownEndOf(current, started.second)?.takeIf { it.isAfter(now) } ?: ends
        val dayEnds = day.moments().mapNotNull { m -> shownEndOf(m, day)?.let { m.prayer to it } }.toMap()
        val upcoming = timeline.map { it.first }.filter { it.at.isAfter(now) }
        return PrayerNow(current, currentEnds, next, nextEvent, day, dayEnds, upcoming)
    }
}

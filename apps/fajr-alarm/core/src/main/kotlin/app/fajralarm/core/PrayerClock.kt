package app.fajralarm.core

import java.time.ZoneId
import java.time.ZonedDateTime

/** Where a moment falls among the prayers. */
data class PrayerNow(
    /** The prayer whose time it is and when it started, or null between sunrise and Dhuhr. */
    val current: PrayerMoment?,
    /** When [current]'s time runs out; null with no current prayer. */
    val currentEnds: ZonedDateTime?,
    /** The next of the five prayers. */
    val next: PrayerMoment,
    /** The next time of any kind, the optional ones included. */
    val nextEvent: PrayerMoment,
    /** The day [nextEvent] belongs to: the one to list. */
    val day: DailyTimes,
    /** When the time of each of [day]'s prayers runs out, the voluntary ones included. Sunrise and midnight have none. */
    val dayEnds: Map<PrayerTime, ZonedDateTime>,
    /** Every time still to come over the next two days or so, [nextEvent] first. */
    val upcoming: List<PrayerMoment>,
)

object PrayerClock {

    /** Duha's time stops this long before Dhuhr, clear of the moment the sun is at its highest. */
    const val DUHA_BEFORE_DHUHR_MINUTES = 10L

    /**
     * Fajr's time ends at sunrise and Isha's at the next Fajr; every other prayer's
     * ends when the next one starts. Of the voluntary ones, Duha's ends shortly before
     * Dhuhr and the night prayer's at Fajr. Null with no times to go by (polar day or night).
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

        fun endOf(started: PrayerMoment): ZonedDateTime? = when (started.prayer) {
            PrayerTime.FAJR -> firstAfter(started) { it == PrayerTime.SUNRISE }
            PrayerTime.DUHA -> firstAfter(started) { it == PrayerTime.DHUHR }
                ?.minusMinutes(DUHA_BEFORE_DHUHR_MINUTES)?.takeIf { it.isAfter(started.at) }
            PrayerTime.LAST_THIRD -> firstAfter(started) { it == PrayerTime.FAJR }
            PrayerTime.SUNRISE, PrayerTime.MIDNIGHT -> null
            else -> firstAfter(started) { it.obligatory }
        }

        val started = timeline.lastOrNull { it.first.prayer.obligatory && !it.first.at.isAfter(now) }?.first
        val ends = started?.let(::endOf)
        val current = started?.takeIf { ends != null && ends.isAfter(now) }
        val dayEnds = day.moments().mapNotNull { m -> endOf(m)?.let { m.prayer to it } }.toMap()
        val upcoming = timeline.map { it.first }.filter { it.at.isAfter(now) }
        return PrayerNow(current, ends.takeIf { current != null }, next, nextEvent, day, dayEnds, upcoming)
    }
}

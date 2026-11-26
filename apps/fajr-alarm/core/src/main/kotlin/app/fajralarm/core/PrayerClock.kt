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
    /** When the time of each of [day]'s five prayers runs out. */
    val dayEnds: Map<PrayerTime, ZonedDateTime>,
    /** Every time still to come over the next two days or so, [nextEvent] first. */
    val upcoming: List<PrayerMoment>,
)

object PrayerClock {

    /**
     * Fajr's time ends at sunrise and Isha's at the next Fajr; every other prayer's
     * ends when the next one starts. Null with no times to go by (polar day or night).
     */
    fun at(now: ZonedDateTime, params: PrayerCalcParams, zone: ZoneId = now.zone): PrayerNow? {
        val today = now.withZoneSameInstant(zone).toLocalDate()
        val timeline = (-1..2L)
            .map { PrayerTimeCalculator.compute(today.plusDays(it), zone, params) }
            .flatMap { day -> day.moments().map { it to day } }
            .sortedBy { it.first.at }

        val (nextEvent, day) = timeline.firstOrNull { it.first.at.isAfter(now) } ?: return null
        val next = timeline.firstOrNull { it.first.prayer.obligatory && it.first.at.isAfter(now) }?.first ?: return null

        fun endOf(started: PrayerMoment): ZonedDateTime? = timeline.firstOrNull { (m, _) ->
            m.at.isAfter(started.at) &&
                if (started.prayer == PrayerTime.FAJR) m.prayer == PrayerTime.SUNRISE else m.prayer.obligatory
        }?.first?.at

        val started = timeline.lastOrNull { it.first.prayer.obligatory && !it.first.at.isAfter(now) }?.first
        val ends = started?.let(::endOf)
        val current = started?.takeIf { ends != null && ends.isAfter(now) }
        val dayEnds = day.moments().filter { it.prayer.obligatory }
            .mapNotNull { m -> endOf(m)?.let { m.prayer to it } }.toMap()
        val upcoming = timeline.map { it.first }.filter { it.at.isAfter(now) }
        return PrayerNow(current, ends.takeIf { current != null }, next, nextEvent, day, dayEnds, upcoming)
    }
}

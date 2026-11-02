package app.fajralarm.core

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

data class PlannedAlarm(
    val triggerAt: ZonedDateTime,
    val times: DailyTimes,
)

object AlarmPlanner {

    /** Alarm time for the day of [times], or null if the anchor doesn't exist that day. */
    fun alarmTimeFor(settings: AlarmSettings, times: DailyTimes): ZonedDateTime? {
        val anchor = when (settings.anchor) {
            AlarmAnchor.FAJR -> times.fajr
            AlarmAnchor.SUNRISE -> times.sunrise
        } ?: return null
        return anchor.plusMinutes(settings.offsetMinutes.toLong())
    }

    /**
     * The next alarm strictly after [now] + [minLeadSeconds], looking up to two weeks
     * ahead. Each day is recalculated, so the alarm follows Fajr as it moves through
     * the year. Returns null if the alarm is off, no location is set, no days are
     * selected, or Fajr can't be determined (e.g. polar day).
     */
    fun nextAlarm(
        settings: AlarmSettings,
        now: ZonedDateTime,
        zone: ZoneId = now.zone,
        minLeadSeconds: Long = 60,
    ): PlannedAlarm? {
        if (!settings.enabled || settings.days.isEmpty()) return null
        val params = settings.calcParams() ?: return null
        val threshold = now.plusSeconds(minLeadSeconds)
        val startDate = now.withZoneSameInstant(zone).toLocalDate().minusDays(1)
        for (i in 0..14L) {
            val date = startDate.plusDays(i)
            if (date.dayOfWeek !in settings.days) continue
            val times = PrayerTimeCalculator.compute(date, zone, params)
            val at = alarmTimeFor(settings, times) ?: continue
            if (at.isAfter(threshold)) return PlannedAlarm(at, times)
        }
        return null
    }
}

/** A small arithmetic problem that must be solved to switch the alarm off. */
data class MathChallenge(val question: String, val answer: Int) {
    companion object {
        fun random(random: Random = Random.Default): MathChallenge {
            val a = random.nextInt(12, 60)
            val b = random.nextInt(12, 60)
            return if (random.nextBoolean()) {
                MathChallenge("$a + $b", a + b)
            } else {
                val c = random.nextInt(3, 10)
                MathChallenge("$a + $b × $c", a + b * c)
            }
        }
    }
}

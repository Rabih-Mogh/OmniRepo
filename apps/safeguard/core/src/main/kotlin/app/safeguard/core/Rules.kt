package app.safeguard.core

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.EnumSet

/**
 * Hours of the week during which something stays closed: from [from] to [to], counted in
 * minutes from midnight, starting on each of [days]. A [to] earlier than [from] runs past
 * midnight into the next day; the two being equal means the whole day.
 */
data class Hours(val from: Int, val to: Int, val days: Set<DayOfWeek>) {

    fun holds(day: DayOfWeek, minute: Int): Boolean = when {
        from == to -> day in days
        from < to -> day in days && minute in from until to
        else -> (day in days && minute >= from) || (day.minus(1) in days && minute < to)
    }

    fun holds(at: LocalDateTime): Boolean = holds(at.dayOfWeek, at.hour * 60 + at.minute)

    /** When the hours that hold at [at] end; null when they never do. */
    fun until(at: LocalDateTime): LocalDateTime? {
        var t = at.withSecond(0).withNano(0)
        repeat(8 * 24 * 60) {
            if (!holds(t)) return t
            t = t.plusMinutes(1)
        }
        return null
    }

    /** Whether these are closed at every minute of the week that [other] is. */
    fun covers(other: Hours): Boolean =
        DayOfWeek.values().all { day -> (0 until 24 * 60).all { !other.holds(day, it) || holds(day, it) } }
}

/**
 * A group of apps and websites that share one daily allowance and one set of closed hours:
 * YouTube the app and youtube.com in a browser, say.
 */
data class Limit(
    val id: String,
    val name: String,
    /** Package names. */
    val apps: Set<String> = emptySet(),
    val sites: Set<String> = emptySet(),
    /** Minutes it may be used each day; null for as long as one likes. */
    val minutesPerDay: Int? = null,
    val hours: Hours? = null,
) {
    /** Whether this closes everything [old] closes, at least as much of the time. */
    fun asStrictAs(old: Limit): Boolean =
        apps.containsAll(old.apps) && sites.containsAll(old.sites) &&
            (old.minutesPerDay == null || (minutesPerDay != null && minutesPerDay <= old.minutesPerDay)) &&
            (old.hours == null || (hours != null && hours.covers(old.hours)))
}

/** Everything the user has asked to be kept from. */
data class Rules(
    val limits: List<Limit> = emptyList(),
    /** Websites closed in any browser. */
    val sites: Set<String> = emptySet(),
    /** Words that close a browser page showing them. */
    val keywords: Set<String> = emptySet(),
    /** Names of blocked apps, which are neither opened nor installed; they need not be on the phone. */
    val apps: Set<String> = emptySet(),
    /** How long a change that loosens anything waits while [locked]. */
    val waitHours: Int = 24,
    /** Locked, the rules can only be made stricter at once, and the app guards itself. */
    val locked: Boolean = false,
)

/** What a new install starts with. */
object Presets {
    private val workingHours = Hours(9 * 60, 17 * 60, EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))

    val limits = listOf(
        Limit("youtube", "YouTube", setOf("com.google.android.youtube"), setOf("youtube.com", "youtu.be"), 30, workingHours),
        Limit(
            "facebook", "Facebook", setOf("com.facebook.katana", "com.facebook.lite"),
            setOf("facebook.com", "fb.com", "fb.watch"), 30, workingHours,
        ),
        Limit("instagram", "Instagram", setOf("com.instagram.android", "com.instagram.lite"), setOf("instagram.com"), 30, workingHours),
    )
}

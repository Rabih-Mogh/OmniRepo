package app.safeguard.core

/** The lists a single entry can be added to or taken off. */
enum class Listed { SITE, KEYWORD, APP }

/** Something the user asks to change in the [Rules]. */
sealed interface Change {
    data class Add(val list: Listed, val value: String) : Change
    data class Drop(val list: Listed, val value: String) : Change

    /** Adds the limit, or replaces the one with the same id. */
    data class PutLimit(val limit: Limit) : Change
    data class DropLimit(val id: String) : Change
    data class Wait(val hours: Int) : Change
    data object Lock : Change
    data object Unlock : Change

    fun on(rules: Rules): Rules = when (this) {
        is Add -> when (list) {
            Listed.SITE -> rules.copy(sites = rules.sites + value)
            Listed.KEYWORD -> rules.copy(keywords = rules.keywords + value)
            Listed.APP -> rules.copy(apps = rules.apps + value)
        }
        is Drop -> when (list) {
            Listed.SITE -> rules.copy(sites = rules.sites - value)
            Listed.KEYWORD -> rules.copy(keywords = rules.keywords - value)
            Listed.APP -> rules.copy(apps = rules.apps - value)
        }
        is PutLimit ->
            if (rules.limits.any { it.id == limit.id }) {
                rules.copy(limits = rules.limits.map { if (it.id == limit.id) limit else it })
            } else {
                rules.copy(limits = rules.limits + limit)
            }
        is DropLimit -> rules.copy(limits = rules.limits.filterNot { it.id == id })
        is Wait -> rules.copy(waitHours = hours)
        Lock -> rules.copy(locked = true)
        Unlock -> rules.copy(locked = false)
    }

    /** Whether this lets through anything [rules] holds back. Such a change waits while the rules are locked. */
    fun loosens(rules: Rules): Boolean = when (this) {
        is Add, Lock -> false
        is Drop, is DropLimit, Unlock -> true
        is PutLimit -> rules.limits.firstOrNull { it.id == limit.id }?.let { !limit.asStrictAs(it) } ?: false
        is Wait -> hours < rules.waitHours
    }
}

/**
 * A moment as the phone gives it: the clock ([wall]), the milliseconds since the phone
 * started ([elapsed]) and how many times it has started ([boot]). The clock can be set by
 * hand; the other two can't, which is what a wait is measured by.
 */
data class Stamp(val wall: Long, val elapsed: Long, val boot: Int)

/** A change that is waiting its turn: [left] milliseconds were still to go at [seen]. */
data class Pending(val change: Change, val left: Long, val seen: Stamp) {

    /**
     * The same wait at [now]. While the phone stays on, the time gone by is read off its
     * running time, so setting the clock forward gains nothing. Across a restart only the
     * clock is left to go by.
     */
    fun at(now: Stamp): Pending {
        val sameRun = now.boot == seen.boot && now.elapsed >= seen.elapsed
        val gone = if (sameRun) now.elapsed - seen.elapsed else now.wall - seen.wall
        return copy(left = left - gone.coerceAtLeast(0), seen = now)
    }
}

/** The rules in force, and the changes waiting to loosen them. */
data class Book(val rules: Rules = Rules(), val pending: List<Pending> = emptyList()) {

    /** Takes up [change]: at once if the rules aren't locked or it only tightens them, after the wait otherwise. */
    fun ask(change: Change, now: Stamp): Book {
        if (!rules.locked || !change.loosens(rules)) {
            // A limit made stricter drops a looser version of it that was waiting: that one
            // was drawn up without this change, and would undo it on arriving.
            val still = if (change is Change.PutLimit) pending.filterNot { it.change.limitId() == change.limit.id } else pending
            return Book(change.on(rules), still)
        }
        if (pending.any { it.change == change }) return this
        val others = pending.filterNot { change.replaces(it.change) }
        return copy(pending = others + Pending(change, rules.waitHours * 3_600_000L, now))
    }

    fun cancel(change: Change): Book = copy(pending = pending.filterNot { it.change == change })

    /** The book at [now]: waits that have run out are applied. Once the rules are unlocked nothing waits. */
    fun at(now: Stamp): Book {
        if (pending.isEmpty()) return this
        var rules = rules
        val still = ArrayList<Pending>()
        for (p in pending) {
            val q = p.at(now)
            if (q.left <= 0 || !rules.locked) rules = q.change.on(rules) else still += q
        }
        return Book(rules, still)
    }
}

private fun Change.limitId(): String? = (this as? Change.PutLimit)?.limit?.id

// A second request about the same thing takes the place of the first, and waits afresh.
private fun Change.replaces(old: Change): Boolean = when (this) {
    is Change.PutLimit -> old.limitId() == limit.id
    is Change.Wait -> old is Change.Wait
    else -> false
}

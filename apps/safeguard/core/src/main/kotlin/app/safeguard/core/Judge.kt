package app.safeguard.core

import java.time.LocalDateTime

/** The window in front of the user. */
data class Front(
    /** Package name. */
    val app: String,
    /** The app's name as the phone shows it. */
    val label: String,
    val browser: Boolean = false,
    /** An app store, or Android's own install prompt. */
    val store: Boolean = false,
    /** The phone's settings, or anything else an app can be switched off or removed from. */
    val settings: Boolean = false,
    /** The text on screen; only read for browsers, stores and settings. */
    val seen: List<Seen> = emptyList(),
    /** The website a browser is on, when the caller knows better than [seen] does. */
    val host: String? = null,
)

/** Why something was closed. */
sealed interface Why {
    data class Hours(val name: String, val until: LocalDateTime?) : Why
    data class Spent(val name: String, val minutes: Int) : Why
    data class Never(val name: String) : Why
    data class Site(val site: String) : Why
    data class Keyword(val word: String) : Why
    data object Guarded : Why
}

sealed interface Verdict {
    data object Free : Verdict

    /** Allowed, and the time spent counts against the limit with this id. */
    data class Counting(val limit: String) : Verdict

    /** [page] when what is closed is something inside the app (a website, a store page, a settings page) and not the app itself. */
    data class Closed(val why: Why, val page: Boolean) : Verdict
}

object Judge {

    /**
     * What to do about [front] at [now]. [own] are the names this app goes by, which a
     * locked app keeps off the settings pages; [usedSeconds] is today's use of a limit.
     */
    fun look(rules: Rules, front: Front, now: LocalDateTime, own: List<String>, usedSeconds: (String) -> Long): Verdict {
        if (rules.locked && front.settings && front.seen.any { s -> own.any { Page.mentions(s.text, it) } }) {
            return Verdict.Closed(Why.Guarded, page = true)
        }

        rules.apps.firstOrNull { front.app.equals(it, ignoreCase = true) || Page.mentions(front.label, it) }
            ?.let { return Verdict.Closed(Why.Never(it), page = false) }
        if (front.store) {
            rules.apps.firstOrNull { name -> front.seen.any { Page.mentions(it.text, name) } }
                ?.let { return Verdict.Closed(Why.Never(it), page = true) }
        }

        val host = if (front.browser) front.host ?: Page.host(front.seen) else null
        if (front.browser) {
            if (host != null) {
                rules.sites.firstOrNull { Page.on(host, it) }?.let { return Verdict.Closed(Why.Site(it), page = true) }
            }
            rules.keywords.firstOrNull { word -> front.seen.any { Page.mentions(it.text, word) } }
                ?.let { return Verdict.Closed(Why.Keyword(it), page = true) }
        }

        var counting: String? = null
        for (limit in rules.limits) {
            val app = front.app in limit.apps
            val site = host != null && limit.sites.any { Page.on(host, it) }
            if (!app && !site) continue
            if (limit.hours?.holds(now) == true) {
                return Verdict.Closed(Why.Hours(limit.name, limit.hours.until(now)), page = !app)
            }
            if (limit.minutesPerDay != null && usedSeconds(limit.id) >= limit.minutesPerDay * 60L) {
                return Verdict.Closed(Why.Spent(limit.name, limit.minutesPerDay), page = !app)
            }
            if (counting == null) counting = limit.id
        }
        return counting?.let { Verdict.Counting(it) } ?: Verdict.Free
    }
}

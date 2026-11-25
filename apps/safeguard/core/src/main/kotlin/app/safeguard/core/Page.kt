package app.safeguard.core

import java.util.Locale

/** One piece of text on the screen: what it says, the name the app gives its place, and whether it can be typed into. */
data class Seen(val text: String, val id: String? = null, val editable: Boolean = false)

/** Reading what a window shows: the website a browser is on, and whether a word is there. */
object Page {

    // What browsers call their address bar.
    private val bars = listOf("url", "location_bar", "omnibar", "omnibox", "address")

    /** The website a browser window is on, read from its address bar; null when the bar isn't showing one. */
    fun host(seen: List<Seen>): String? =
        seen.asSequence()
            .filter { s -> s.editable || s.id?.substringAfterLast('/')?.let { id -> bars.any { it in id } } == true }
            .mapNotNull { hostOf(it.text) }
            .firstOrNull()

    /** The host in an address as a browser shows it, with or without "https://"; null for anything else, a search say. */
    fun hostOf(address: String): String? {
        // Browsers put invisible direction marks around an address.
        var s = address.filterNot { it.category == CharCategory.FORMAT }.trim().lowercase(Locale.ROOT)
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        s = s.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
        s = s.substringAfterLast('@').substringBefore(':')
        if ('.' !in s || s.startsWith('.') || s.endsWith('.')) return null
        if (!s.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return null
        return s
    }

    /** A website as the user typed it, in the form the lists keep; null when it isn't one. */
    fun site(entry: String): String? = hostOf(entry)?.removePrefix("www.")

    /** Whether [host] is [site] or sits under it, as m.example.com does under example.com. */
    fun on(host: String, site: String): Boolean = host == site || host.endsWith(".$site")

    /**
     * Whether [text] says [word], whatever the capitals. An English word has to stand alone,
     * so "bet" isn't found in "better". Other scripts join words to their neighbours (Arabic
     * writes "the" onto the word), so there the word is looked for anywhere.
     */
    fun mentions(text: String, word: String): Boolean {
        val w = word.trim().lowercase(Locale.ROOT)
        if (w.isEmpty()) return false
        val t = text.lowercase(Locale.ROOT)
        if (!w.all { it.code < 128 }) return w in t
        var at = t.indexOf(w)
        while (at >= 0) {
            val before = at == 0 || !t[at - 1].isLetterOrDigit()
            val after = at + w.length == t.length || !t[at + w.length].isLetterOrDigit()
            if (before && after) return true
            at = t.indexOf(w, at + 1)
        }
        return false
    }
}

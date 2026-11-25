package app.safeguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PageTest {

    @Test
    fun `an address gives its host however the browser shows it`() {
        assertEquals("m.youtube.com", Page.hostOf("https://m.youtube.com/watch?v=abc"))
        assertEquals("m.youtube.com", Page.hostOf("m.youtube.com/shorts/abc"))
        assertEquals("example.com", Page.hostOf("  Example.com  "))
        assertEquals("example.com", Page.hostOf("http://user@example.com:8080/a#b"))
        // Chrome wraps what it shows in invisible direction marks.
        assertEquals("example.com", Page.hostOf("‎example.com/page‎"))
    }

    @Test
    fun `a search or a single word is not an address`() {
        assertNull(Page.hostOf("how to stop scrolling"))
        assertNull(Page.hostOf("youtube"))
        assertNull(Page.hostOf(""))
        assertNull(Page.hostOf("Search or type URL"))
    }

    @Test
    fun `a typed website is kept without its www`() {
        assertEquals("example.com", Page.site("https://www.Example.com/"))
        assertNull(Page.site("not a site"))
    }

    @Test
    fun `a website covers everything under it and nothing beside it`() {
        assertTrue(Page.on("facebook.com", "facebook.com"))
        assertTrue(Page.on("m.facebook.com", "facebook.com"))
        assertFalse(Page.on("notfacebook.com", "facebook.com"))
        assertFalse(Page.on("facebook.com.example.org", "facebook.com"))
    }

    @Test
    fun `the address bar is the box that can be typed into or is named as one`() {
        val chrome = listOf(
            Seen("Home"),
            Seen("youtube.com/feed", id = "com.android.chrome:id/url_bar", editable = true),
            Seen("Trending"),
        )
        assertEquals("youtube.com", Page.host(chrome))
        // Firefox shows the address in a label.
        val firefox = listOf(Seen("instagram.com", id = "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"))
        assertEquals("instagram.com", Page.host(firefox))
        // A link in the page is not where the browser is.
        assertNull(Page.host(listOf(Seen("example.com"), Seen("Search or type URL", editable = true))))
    }

    @Test
    fun `an English word has to stand alone`() {
        assertTrue(Page.mentions("Best BET of the day", "bet"))
        assertTrue(Page.mentions("example.com/bet?x=1", "bet"))
        assertFalse(Page.mentions("a better day", "bet"))
        assertTrue(Page.mentions("play online poker now", "online poker"))
        assertFalse(Page.mentions("anything", "  "))
    }

    @Test
    fun `an Arabic word is found with its article joined on`() {
        assertTrue(Page.mentions("مواقع القمار", "قمار"))
        assertFalse(Page.mentions("مواقع الأخبار", "قمار"))
    }
}

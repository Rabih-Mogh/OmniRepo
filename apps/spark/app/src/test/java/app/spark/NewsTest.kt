package app.spark

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.spark.data.News
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Locale

/** A news feed read: what a story is taken to be, and which items are passed over. */
@RunWith(AndroidJUnit4::class)
class NewsTest {
    private val now = SimpleDateFormat("yyyy-MM-dd HH:mm zzz", Locale.US).parse("2026-11-27 18:00 GMT")!!.time

    private fun item(title: String, link: String, date: String, description: String = "What happened.") =
        "<item><title>$title</title><description>$description</description><link>$link</link><pubDate>$date</pubDate><guid>x</guid></item>"

    private fun feed(vararg items: String) = """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>The feed's own title</title><link>https://example.com/</link>${items.joinToString("")}</channel></rss>"""

    @Test
    fun `a story is its title, its summary in plain words and its address`() {
        val stories = News.parse(
            feed(
                item("<![CDATA[A comet's 'tail' grows]]>", "https://example.com/comet?a=1&amp;b=2", "Fri, 27 Nov 2026 15:27:00 GMT", "<![CDATA[Seen   last night.<p>The post <a href=\"https://example.com\">Comet</a> appeared first.</p>]]>"),
                item("Fish &amp; chips", "https://example.com/fish", "Thu, 26 Nov 2026 05:01:39 EST"),
            ),
            "Example News",
            now,
        )
        assertEquals(2, stories.size)
        assertEquals("A comet's 'tail' grows", stories[0].title)
        assertEquals("Seen last night. The post Comet appeared first.", stories[0].summary)
        assertEquals("https://example.com/comet?a=1&b=2", stories[0].url)
        assertEquals("Example News", stories[0].source)
        assertEquals("Fish & chips", stories[1].title)
    }

    @Test
    fun `old stories, and ones without a secure address or a title, are passed over`() {
        val stories = News.parse(
            feed(
                item("Last month", "https://example.com/old", "Tue, 27 Oct 2026 10:00:00 +0000"),
                item("Not secure", "http://example.com/plain", "Fri, 27 Nov 2026 10:00:00 +0000"),
                item("", "https://example.com/untitled", "Fri, 27 Nov 2026 10:00:00 +0000"),
                item("Six days ago", "https://example.com/week", "Sat, 21 Nov 2026 19:00:00 +0000"),
                item("No date given", "https://example.com/undated", ""),
            ),
            "Example News",
            now,
        )
        assertEquals(listOf("Six days ago", "No date given"), stories.map { it.title })
    }

    @Test
    fun `only the first few of a feed are taken, and text that isn't a feed gives none`() {
        val many = (1..20).map { item("Story $it", "https://example.com/$it", "Fri, 27 Nov 2026 10:00:00 +0000") }
        assertEquals((1..6).map { "Story $it" }, News.parse(feed(*many.toTypedArray()), "Example News", now).map { it.title })
        assertEquals(0, News.parse("<html><body>Not found</body></html>", "Example News", now).size)
    }
}

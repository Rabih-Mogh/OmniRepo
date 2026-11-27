package app.spark.data

import android.util.Xml
import app.spark.core.Prompt
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The last few days' stories from a handful of public news feeds, for the news reels of a
 * pack written on the phone. Only what a feed itself says of a story is taken: its title,
 * its one-line summary and its address.
 */
object News {
    class Feed(val source: String, val url: String)

    /** Science twice over, and technology. Tests point this at a server of their own. */
    internal var feeds = listOf(
        Feed("ScienceDaily", "https://www.sciencedaily.com/rss/top/science.xml"),
        Feed("Phys.org", "https://phys.org/rss-feed/breaking/"),
        Feed("BBC News", "https://feeds.bbci.co.uk/news/technology/rss.xml"),
    )

    private const val LIMIT = 1_000_000
    private const val FROM_EACH = 6
    private const val FRESH_MS = 7 * 24 * 60 * 60 * 1000L

    /** The newest stories of each feed. A feed that can't be reached or read is left out. */
    fun fetch(now: Long = System.currentTimeMillis()): List<Prompt.Story> = feeds.flatMap { feed ->
        try {
            // Some feeds turn away a reader that doesn't say what it is.
            parse(Net.get(feed.url, LIMIT, mapOf("User-Agent" to "Spark (Android)")), feed.source, now)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** The stories in an RSS feed's text, newest first as the feed has them: those of the last week that have a secure address. */
    internal fun parse(xml: String, source: String, now: Long): List<Prompt.Story> {
        val stories = ArrayList<Prompt.Story>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var item: HashMap<String, String>? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && stories.size < FROM_EACH) {
            when (event) {
                XmlPullParser.START_TAG -> when {
                    parser.name == "item" -> item = HashMap()
                    // A feed's own title and link, not another vocabulary's of the same name (a picture's title, say).
                    item != null && parser.depth == 4 && parser.namespace.isNullOrEmpty() && parser.name in WANTED -> item[parser.name] = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    item?.let(::story)?.let { (story, at) -> if (at == null || now - at <= FRESH_MS) stories += story.copy(source = source) }
                    item = null
                }
            }
            event = parser.next()
        }
        return stories
    }

    private val WANTED = setOf("title", "description", "link", "pubDate")

    // A story and when it was published, when the feed says. An item without a title or a secure address isn't one.
    private fun story(item: Map<String, String>): Pair<Prompt.Story, Long?>? {
        val title = plain(item["title"].orEmpty())
        val url = item["link"].orEmpty().trim()
        if (title.isEmpty() || !url.startsWith("https://") || url.any { it.isWhitespace() } || url.length > 300) return null
        val at = try {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(item["pubDate"].orEmpty().trim())?.time
        } catch (_: Exception) {
            null
        }
        return Prompt.Story(title, plain(item["description"].orEmpty()), url, "") to at
    }

    // A feed's text as words alone: its tags taken out, the commonest character names put back, on one line.
    private fun plain(text: String): String = text
        .replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()
}

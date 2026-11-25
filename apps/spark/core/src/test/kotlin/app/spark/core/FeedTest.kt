package app.spark.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedTest {
    private val today = 20_000L

    private fun reel(id: String, topic: String = "maths", tags: List<String> = listOf(topic), kind: Kind = Kind.IDEA, day: Long = today, hook: String = id) =
        Reel(id, day, topic, tags, kind, hook, listOf("beat"), "takeaway", null, null, null)

    private fun next(pool: List<Reel>, taste: Taste = Taste(), seen: Set<String> = emptySet(), before: List<Reel> = emptyList(), wishes: List<String> = emptyList(), seed: Int = 1) =
        Feed.next(pool, taste, seen, before, wishes, today, Random(seed))

    /** The whole pool in the order the feed would show it, with nothing learned along the way. */
    private fun run(pool: List<Reel>, taste: Taste = Taste(), wishes: List<String> = emptyList(), seed: Int = 1): List<Reel> {
        val shown = ArrayList<Reel>()
        val random = Random(seed)
        while (true) shown += Feed.next(pool, taste, emptySet(), shown, wishes, today, random) ?: return shown
    }

    @Test
    fun `a reel seen or already in the feed doesn't come again`() {
        val pool = listOf(reel("a"), reel("b"), reel("c"))
        assertEquals("c", next(pool, seen = setOf("a"), before = listOf(pool[1]))?.id)
        assertNull(next(pool, seen = setOf("a", "b", "c")))
        assertEquals(setOf("a", "b", "c"), run(pool).map { it.id }.toSet())
    }

    @Test
    fun `what held the user comes before what they skipped`() {
        var taste = Taste()
        repeat(6) { taste = taste.after(reel("m$it", "maths"), Watch(1.0, liked = true).reward()) }
        repeat(6) { taste = taste.after(reel("i$it", "ideas"), Watch(0.05).reward()) }
        for (seed in 1..20) assertEquals("maths", next(listOf(reel("x", "ideas"), reel("y", "maths")), taste, seed = seed)?.topic)
    }

    @Test
    fun `a tag that held the user lifts a reel within its topic`() {
        var taste = Taste()
        repeat(6) { taste = taste.after(reel("p$it", tags = listOf("probability")), 1.0) }
        repeat(6) { taste = taste.after(reel("g$it", tags = listOf("geometry")), -0.6) }
        for (seed in 1..20) assertEquals("p", next(listOf(reel("g", tags = listOf("geometry")), reel("p", tags = listOf("probability"))), taste, seed = seed)?.id)
    }

    @Test
    fun `a reel that answers a wish goes first`() {
        val pool = listOf(reel("a", "science"), reel("b", "computing", tags = listOf("game theory")), reel("c", "ideas", hook = "Why habits stick"))
        for (seed in 1..20) {
            assertEquals("b", next(pool, wishes = listOf("I want to learn game theory"), seed = seed)?.id)
            assertEquals("c", next(pool, wishes = listOf("habits"), seed = seed)?.id)
        }
        assertTrue(Feed.answers(pool[1], listOf("Game Theory, please")))
        // Words everyone uses match nothing.
        assertTrue(!Feed.answers(reel("d", hook = "What they want from this"), listOf("what I want to learn about this")))
    }

    @Test
    fun `today's news comes before last week's, and old news last of all`() {
        val pool = listOf(reel("old", kind = Kind.NEWS, day = today - 20), reel("week", kind = Kind.NEWS, day = today - 6), reel("idea", day = today - 6), reel("new", kind = Kind.NEWS))
        for (seed in 1..20) {
            val order = run(pool.map { it.copy(topic = it.id, tags = listOf(it.id)) }, seed = seed).map { it.id }
            assertEquals("new", order.first())
            assertEquals("old", order.last())
        }
    }

    @Test
    fun `a topic never runs three in a row while another is waiting`() {
        var taste = Taste()
        repeat(10) { taste = taste.after(reel("m$it", "maths"), 0.6) }
        val pool = (1..8).map { reel("m$it", "maths") } + (1..4).map { reel("s$it", "science") }
        for (seed in 1..20) {
            val topics = run(pool, taste, seed = seed).map { it.topic }
            val firstNine = topics.take(9)
            assertTrue(firstNine.windowed(3).none { it.toSet().size == 1 }, "$topics")
        }
    }

    @Test
    fun `a topic shown least gets its turn even against one that is liked`() {
        var taste = Taste()
        repeat(40) { taste = taste.after(reel("m$it", "maths"), 0.3) }
        val pool = (1..6).map { reel("m$it", "maths") } + reel("h", "history")
        for (seed in 1..20) assertTrue(run(pool, taste, seed = seed).take(3).any { it.topic == "history" })
    }

    @Test
    fun `how a reel went is turned into a reward`() {
        assertEquals(-1.0, Watch(1.0, liked = true, less = true).reward())
        assertEquals(-0.6, Watch(0.05).reward())
        assertEquals(0.5, Watch(1.4).reward())
        assertEquals(0.25, Watch(0.5).reward())
        assertEquals(1.0, Watch(1.0, liked = true, lesson = true).reward())
        // A like on a reel flicked past still counts for it.
        assertTrue(Watch(0.05, liked = true).reward() > 0.5)
    }

    @Test
    fun `taste moves towards what just happened and counts the reels`() {
        val r = reel("a", "maths", tags = listOf("probability", "puzzles"))
        val once = Taste().after(r, 1.0)
        assertEquals(Leaning(0.2, 1), once.topics["maths"])
        assertEquals(Leaning(0.3, 1), once.tags["puzzles"])
        val twice = once.after(r, 1.0)
        assertEquals(0.36, twice.topics["maths"]!!.value, 1e-9)
        assertEquals(2, twice.tags["probability"]!!.count)
    }
}

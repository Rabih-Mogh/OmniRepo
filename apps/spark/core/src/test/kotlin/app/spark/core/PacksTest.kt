package app.spark.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacksTest {
    private val text = javaClass.getResource("/pack.json")!!.readText()

    /** The good pack with one piece of its text swapped for another. */
    private fun with(old: String, new: String): Packs.Reading {
        assertTrue(old in text, "the pack has no $old")
        return Packs.read(text.replace(old, new))
    }

    private fun assertProblem(reading: Packs.Reading, about: String) =
        assertTrue(reading.problems.any { about in it }, "expected a problem about \"$about\", got ${reading.problems}")

    @Test
    fun `a good pack is read whole`() {
        val reading = Packs.read(text)
        assertEquals(emptyList(), reading.problems)
        val pack = assertNotNull(reading.pack)
        assertEquals(LocalDate.of(2026, 11, 26).toEpochDay(), pack.day)
        assertEquals(listOf(Kind.IDEA, Kind.NEWS, Kind.PUZZLE), pack.reels.map { it.kind })

        val reel = pack.reels[0]
        assertEquals("2026-11-26-pairs", reel.lesson)
        assertEquals(listOf("probability", "intuition"), reel.tags)
        assertEquals(2, reel.beats.size)
        assertEquals(0.5, assertIs<Visual.Curve>(reel.visual).plot.at(23.0), 0.01)
        assertEquals(Source("Example News", "https://example.com/chip"), pack.reels[1].source)
        assertEquals(Visual.Dots(3, 2), pack.reels[2].visual)

        val steps = pack.lessons.single().steps
        // The right answer is the first option as written; the rest are the wrong ones, each with what is wrong with it.
        val choice = assertIs<Step.Choice>(steps[0])
        assertEquals("6", choice.right)
        assertEquals(listOf("4", "8"), choice.wrong)
        assertEquals("That counts every person twice.", choice.mistake("8"))
        assertNull(choice.mistake("6"))
        // A picture goes with the question, and another is kept for the explanation.
        assertEquals(Picture(Visual.Grid(1, 4, 4), "Four people"), choice.picture)
        assertEquals(Visual.Bars(listOf("2 people", "3 people", "4 people"), listOf(1.0, 3.0, 6.0), 2), choice.reveal?.visual)
        assertEquals(Picture(Visual.Number(253.0, 0, "", "pairs"), null), steps[1].picture)
        assertNull(steps[1].reveal)
        val order = assertIs<Step.Order>(steps[2])
        assertEquals(3, order.items.size)
        assertNull(order.picture)
        val line = assertIs<Visual.Line>(order.reveal?.visual)
        assertEquals(Mark(253.0, "23 people"), line.points[2])
        assertEquals(2, line.highlight)
        val estimate = assertIs<Step.Estimate>(steps[3])
        assertEquals(23.0, estimate.answer)
        val curve = assertIs<Visual.Curve>(estimate.reveal?.visual)
        assertEquals(listOf("Any two", "You and one"), curve.names)
        assertEquals(Mark(23.0, "23 people: 50%"), curve.mark)
        assertEquals(0.06, assertNotNull(curve.versus).at(23.0), 0.005)
        assertEquals("The chance of a shared birthday", estimate.reveal?.caption)
        val explore = assertIs<Step.Explore>(steps[4])
        assertTrue(explore.hits(1.0))
        assertTrue(!explore.hits(0.5))
        assertEquals(45.0, explore.shows.single().value.at(0.0, 1.0))
    }

    @Test
    fun `text that isn't a pack is refused outright`() {
        assertNull(Packs.read("nonsense").pack)
        assertNull(Packs.read("[]").pack)
        assertNull(with("\"format\": 1", "\"format\": 2").pack)
        assertNull(with("\"day\": \"2026-11-26\"", "\"day\": \"yesterday\"").pack)
    }

    @Test
    fun `a reel with something wrong is left out and the rest are kept`() {
        val reading = with("\"kind\": \"puzzle\"", "\"kind\": \"riddle\"")
        assertProblem(reading, "2026-11-26-doors: \"kind\" is \"riddle\"")
        assertEquals(listOf("2026-11-26-birthday", "2026-11-26-chip"), reading.pack!!.reels.map { it.id })
        assertEquals(1, reading.pack!!.lessons.size)
    }

    @Test
    fun `what a reel needs is checked`() {
        assertProblem(with("\"id\": \"2026-11-26-chip\"", "\"id\": \"chip\""), "must start with \"2026-11-26-\"")
        assertProblem(with("\"id\": \"2026-11-26-chip\"", "\"id\": \"2026-11-26-doors\""), "used twice")
        assertProblem(with("\"topic\": \"computing\"", "\"topic\": \"Computer Science\""), "one lowercase word")
        assertProblem(with("\"tags\": [\"chips\"]", "\"tags\": []"), "\"tags\" must be a list of 1 to 5")
        assertProblem(with("A new chip does one thing well.", "x".repeat(91)), "\"hook\" is 91 characters")
        assertProblem(with("\"source\": {\"title\": \"Example News\", \"url\": \"https://example.com/chip\"}", "\"x\": 1"), "news needs a \"source\"")
        assertProblem(with("https://example.com/chip", "http://example.com/chip"), "https://")
        assertProblem(with("\"lesson\": \"2026-11-26-pairs\"", "\"lesson\": \"2026-11-26-gone\""), "isn't in this pack")
        assertProblem(with("\"takeaway\": \"Count the pairs, not the people.\",", ""), "\"takeaway\" is missing")
    }

    @Test
    fun `a reel has to fit one screen`() {
        val beat = "x".repeat(150)
        val reading = with("\"beats\": [\"It was announced this week.\"]", "\"beats\": [\"$beat\", \"$beat\", \"$beat\", \"$beat\"]")
        assertProblem(reading, "2026-11-26-chip: its hook, beats and takeaway come to")
        assertEquals(2, reading.pack!!.reels.size)
    }

    @Test
    fun `a lesson no reel leads to is reported, and kept`() {
        val reading = with("\"lesson\": \"2026-11-26-pairs\"", "\"x\": 1")
        assertProblem(reading, "2026-11-26-pairs: no reel leads to this lesson")
        assertEquals(1, reading.pack!!.lessons.size)
    }

    @Test
    fun `a visual that can't be drawn is refused with its reel`() {
        assertProblem(with("1 - exp(-x*(x-1)/730)", "1 - exp(-x*(x-1)/730"), "bracket")
        assertProblem(with("1 - exp(-x*(x-1)/730)", "1/(x-30)"), "no value somewhere")
        assertProblem(with("\"values\": [1, 3.5]", "\"values\": [1, 3.5, 2]"), "as many labels as values")
        assertProblem(with("\"highlight\": 1", "\"highlight\": 2"), "\"highlight\"")
        assertProblem(with("\"total\": 3, \"lit\": 2", "\"total\": 3, \"lit\": 4"), "dots need")
        val reading = with("\"kind\": \"dots\"", "\"kind\": \"spiral\"")
        assertProblem(reading, "2026-11-26-doors, visual")
        assertEquals(2, reading.pack!!.reels.size)
    }

    @Test
    fun `the pictures a lesson can show are checked`() {
        val bars = "{\"kind\": \"bars\", \"labels\": [\"2 people\", \"3 people\", \"4 people\"], \"values\": [1, 3, 6], \"highlight\": 2, \"caption\": \"Pairs among them\"}"
        fun reveal(visual: String) = with(bars, visual)
        fun share(rest: String) = reveal("{\"kind\": \"share\", $rest}")

        val reading = share("\"labels\": [\"True\", \"False\"], \"values\": [99, 999]")
        assertEquals(emptyList(), reading.problems)
        val drawn = assertIs<Visual.Share>(reading.pack!!.lessons.single().steps[0].reveal?.visual)
        assertEquals(0, drawn.highlight)
        assertEquals(listOf("9%", "91%"), listOf(drawn.percent(0), drawn.percent(1)))
        assertEquals("<1%", Visual.Share(listOf("a", "b"), listOf(1.0, 999.0), 0).percent(0))
        assertProblem(share("\"labels\": [\"True\", \"False\"], \"values\": [99]"), "\"values\" must be a list of 2 to 5")
        assertProblem(share("\"labels\": [\"True\", \"False\"], \"values\": [0, 0]"), "one must be above zero")
        assertProblem(share("\"labels\": [\"True\", \"False\"], \"values\": [1, 2], \"highlight\": 2"), "the position of a part")

        assertProblem(with("\"rows\": 1, \"columns\": 4", "\"rows\": 1, \"columns\": 11"), "a grid needs")
        assertProblem(with("\"rows\": 1, \"columns\": 4", "\"rows\": 1, \"columns\": 4, \"lit\": 5"), "a grid needs")
        assertProblem(with("{\"at\": 253, \"label\": \"23 people\"}", "{\"at\": 353, \"label\": \"23 people\"}"), "a point is off the line")
        assertProblem(with("\"highlight\": 2, \"caption\": \"How many", "\"highlight\": 3, \"caption\": \"How many"), "the position of a point")
        assertProblem(with("\"versus\": \"1-(364/365)^x\", ", ""), "there is no \"versus\"")
        assertProblem(with("\"versus\": \"1-(364/365)^x\"", "\"versus\": \"1/(x-30)\""), "no value somewhere")
        assertProblem(with("\"mark\": {\"x\": 23,", "\"mark\": {\"x\": 61,"), "the mark is outside")
        assertProblem(with("\"caption\": \"Four people\"", "\"caption\": \"${"x".repeat(61)}\""), "\"caption\" is 61 characters")
        // A picture that can't be drawn takes its lesson with it, as a step that can't work does.
        assertTrue(reveal("{\"kind\": \"spiral\"}").pack!!.lessons.isEmpty())
    }

    @Test
    fun `a lesson has to show something`() {
        val ask = """{"kind": "order", "text": "Smallest first.", "items": ["1", "2", "3"], "why": "Counting."}"""
        val shown = """{"kind": "order", "text": "Smallest first.", "items": ["1", "2", "3"], "why": "Counting.", "reveal": {"kind": "dots", "total": 3, "lit": 1}}"""
        val seen = """{"kind": "read", "text": "Look.", "visual": {"kind": "number", "value": 3}}"""
        fun reading(steps: String) = Packs.read(
            """{"format": 1, "day": "2026-11-26", "reels": [], "lessons": [{"id": "2026-11-26-words", "topic": "maths", "tags": ["x1"], "title": "Words", "summary": "All words.", "steps": [$steps]}]}""",
        )
        assertProblem(reading("$ask, $ask, $seen"), "2026-11-26-words: a lesson shows as well as tells")
        assertTrue(reading("$ask, $ask, $seen").pack!!.lessons.isEmpty())
        assertEquals(1, reading("$shown, $ask, $seen").pack!!.lessons.size)
    }

    @Test
    fun `a lesson with a step that can't work is left out, and so is the reel that leads to it`() {
        val reading = with("\"answer\": 23, \"tolerance\": 3", "\"answer\": 230, \"tolerance\": 3")
        assertProblem(reading, "2026-11-26-pairs, steps 4: \"answer\" is outside the range")
        assertTrue(reading.pack!!.lessons.isEmpty())
        assertEquals(listOf("2026-11-26-chip", "2026-11-26-doors"), reading.pack!!.reels.map { it.id })
    }

    @Test
    fun `what a step needs is checked`() {
        assertProblem(with("\"options\": [\"6\", \"4\", \"8\"]", "\"options\": [\"6\", \"4\", \"6\"]"), "two options are the same")
        assertProblem(with("\"options\": [\"6\", \"4\", \"8\"]", "\"options\": [\"6\"]"), "\"options\" must be a list of 2 to 4")
        assertProblem(with("\"options\": [\"6\", \"4\", \"8\"]", "\"options\": [\"6\", \"4\"]"), "one line for each wrong option, in the options' order: 1, not 2")
        assertProblem(with("\"mistakes\": [\"That counts the people, not the pairs.\", \"That counts every person twice.\"], ", ""), "\"mistakes\" must be a list of 1 to 3")
        assertProblem(with("\"range\": [2, 100], \"step\": 1", "\"range\": [100, 2], \"step\": 1"), "the lower one first")
        assertProblem(with("\"range\": [2, 100], \"step\": 1, \"answer\": 23, \"tolerance\": 3", "\"range\": [2, 100], \"step\": 10, \"answer\": 27, \"tolerance\": 1"), "no stop of the slider")
        assertProblem(with("\"target\": {\"x\": 10, \"y\": 45}", "\"target\": {\"x\": 10, \"y\": 9}"), "already passes through the target")
        assertProblem(with("\"target\": {\"x\": 10, \"y\": 45}", "\"target\": {\"x\": 10, \"y\": 80}"), "outside the picture")
        assertProblem(with("\"param\": {\"range\": [0, 2]", "\"param\": {\"range\": [0, 0.5]"), "no stop of the slider takes the curve")
        assertProblem(with("\"plot\": \"a*x*(x-1)/2\"", "\"plot\": \"a*y\""), "\"y\" isn't something a formula knows")
    }

    @Test
    fun `the index names the packs on offer`() {
        assertEquals(listOf("2026-11-26", "2026-11-27"), Packs.index("""{"format": 1, "packs": ["2026-11-26", "2026-11-27"]}"""))
        assertEquals(emptyList(), Packs.index("""{"format": 1, "packs": []}"""))
        assertNull(Packs.index("""{"format": 2, "packs": ["2026-11-26"]}"""))
        assertNull(Packs.index("""{"format": 1}"""))
        assertNull(Packs.index("<html>Not found</html>"))
    }

    @Test
    fun `a lesson has to open with a guess`() {
        val read = """{"kind": "read", "text": "Just read.", "visual": {"kind": "number", "value": 3}}"""
        val ask = """{"kind": "order", "text": "Smallest first.", "items": ["1", "2", "3"], "why": "Counting."}"""
        fun reading(steps: String) = Packs.read(
            """{"format": 1, "day": "2026-11-26", "reels": [], "lessons": [{"id": "2026-11-26-told", "topic": "maths", "tags": ["x1"], "title": "Told", "summary": "Telling first.", "steps": [$steps]}]}""",
        )
        assertProblem(reading("$read, $ask, $read"), "2026-11-26-told: a lesson opens with a guess")
        assertTrue(reading("$read, $ask, $read").pack!!.lessons.isEmpty())
        assertEquals(1, reading("$ask, $read, $read").pack!!.lessons.size)
    }

    @Test
    fun `a lesson has to ask for something`() {
        val read = """{"kind": "read", "text": "Just read."}"""
        val lesson = """{"id": "2026-11-26-dull", "topic": "maths", "tags": ["x1"], "title": "Dull", "summary": "Nothing to do.", "steps": [$read, $read, $read]}"""
        val reading = Packs.read("""{"format": 1, "day": "2026-11-26", "reels": [], "lessons": [$lesson]}""")
        assertProblem(reading, "2026-11-26-dull: a lesson needs a step that asks for something")
        assertTrue(reading.pack!!.lessons.isEmpty())
    }
}

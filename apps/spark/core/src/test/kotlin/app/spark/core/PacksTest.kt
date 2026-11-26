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
        assertEquals(Visual.Number(253.0, 0, "", "pairs"), assertIs<Step.Read>(steps[1]).visual)
        assertEquals(3, assertIs<Step.Order>(steps[2]).items.size)
        assertEquals(23.0, assertIs<Step.Estimate>(steps[3]).answer)
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
        val read = """{"kind": "read", "text": "Just read."}"""
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

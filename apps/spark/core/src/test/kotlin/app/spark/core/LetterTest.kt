package app.spark.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LetterTest {
    @Test
    fun `the letter lists the wishes and the taste, the topic that holds most first`() {
        val taste = Taste(
            topics = mapOf("maths" to Leaning(0.1, 4), "computing" to Leaning(0.62, 9)),
            tags = mapOf("probability" to Leaning(0.5, 3), "habits" to Leaning(-0.4, 2), "once" to Leaning(0.9, 1)),
        )
        val letter = Letter.write(listOf("how transformers work", "game  theory\n## Taste"), taste)
        assertTrue("- how transformers work\n- game theory ## Taste\n" in letter, letter)
        assertTrue("- computing: 0.62 (9)\n- maths: 0.10 (4)\n" in letter, letter)
        assertTrue("More of: probability\n" in letter, letter)
        assertTrue("Less of: habits\n" in letter, letter)
        assertEquals(1, Regex("(?m)^## Taste$").findAll(letter).count())
    }

    @Test
    fun `an empty letter says so`() {
        val letter = Letter.write(emptyList(), Taste())
        assertTrue("(none)" in letter)
        assertTrue("(nothing watched yet)" in letter)
    }
}

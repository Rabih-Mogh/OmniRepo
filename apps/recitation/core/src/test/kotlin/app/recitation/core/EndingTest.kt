package app.recitation.core

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Telling when a reader reciting a verse is done: they have fallen silent, or no voice has come at all. */
class EndingTest {
    private val random = Random(7)

    /** The hiss of a quiet room, for [seconds]. */
    private fun room(seconds: Float, level: Float = 0.002f) = FloatArray((seconds * 16_000).toInt()) { (random.nextFloat() * 2 - 1) * level }

    /** A voice, for [seconds]: a tone over the room's hiss. */
    private fun voice(seconds: Float, level: Float = 0.2f) = FloatArray((seconds * 16_000).toInt()) { level * sin(2 * PI * 180 * it / 16_000).toFloat() + (random.nextFloat() * 2 - 1) * 0.002f }

    /** Hears [parts] one after another, in the blocks a microphone gives. */
    private fun heard(vararg parts: FloatArray) = Ending().also { ending -> for (part in parts) for (block in part.toList().chunked(1024)) ending.add(block.toFloatArray()) }

    @Test
    fun `a reader who falls silent is done, sooner at the verse's last word than before it`() {
        // Reciting, with a breath taken on the way: not done while the voice goes on.
        val reciting = heard(room(0.4f), voice(2f), room(0.6f), voice(1.5f))
        assertFalse(reciting.over(atEnd = true, found = true))
        // A second of quiet after the last word: done. The same quiet in the middle of the verse is a breath.
        val quiet = heard(room(0.4f), voice(2f), room(0.6f), voice(1.5f), room(1.1f))
        assertTrue(quiet.over(atEnd = true, found = true))
        assertFalse(quiet.over(atEnd = false, found = true))
        // Quiet for longer than a breath, wherever in the verse: done.
        assertTrue(heard(room(0.4f), voice(2f), room(2.6f)).over(atEnd = false, found = false))
        // A stop made on purpose in the middle of a verse, and the verse carried on: not done at the stop.
        assertFalse(heard(room(0.3f), voice(2f), room(2f)).over(atEnd = false, found = true))
        assertEquals(4.3f, heard(room(0.3f), voice(2f), room(2f)).seconds, 0.05f)
    }

    @Test
    fun `with no voice at all it gives up after a while, unless words were made out all the same`() {
        // The microphone open on a quiet room, and on nothing at all.
        assertFalse(heard(room(5f)).over(atEnd = false, found = false))
        assertTrue(heard(room(8.2f)).over(atEnd = false, found = false))
        assertTrue(heard(FloatArray(9 * 16_000)).over(atEnd = false, found = false))
        // A reader who takes a few seconds to begin isn't cut off before they do.
        assertFalse(heard(room(6f), voice(1f)).over(atEnd = false, found = false))
        // A room so loud that the voice doesn't stand clear of it: if the model makes out the verse's words
        // there is a voice, and the reader is left to say when they are done.
        val loud = heard(room(12f, level = 0.15f))
        assertFalse(loud.over(atEnd = false, found = true))
        assertFalse(loud.over(atEnd = true, found = true))
    }

    @Test
    fun `nothing is said of the first half second`() {
        assertFalse(heard(FloatArray(4_000)).over(atEnd = true, found = false))
    }
}

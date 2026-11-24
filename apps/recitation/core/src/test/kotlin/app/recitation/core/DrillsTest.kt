package app.recitation.core

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tajweed exercises: which verses are chosen for a topic, and how a recitation is judged for it alone. */
class DrillsTest {
    /** The real verses of the follow-along test, by the name of the case: «67:2» and so on. */
    private val verses: Map<String, List<WordForms>> = javaClass.getResourceAsStream("/follow.txt")!!.bufferedReader().readText().trim().split("\n\n").associate { block ->
        val lines = block.split('\n')
        lines.first { it.startsWith("C ") }.substringAfterLast(' ') to TajweedReference.verse(lines.first { it.startsWith("R ") }.drop(2))
    }
    private val surahs = verses.entries.groupBy({ it.key.substringBefore(':').toInt() }, { it.value })

    private fun word(index: Int, vararg faults: Correction, stopped: Boolean = false) = WordRecited(index, stopped, faults.toList(), emptyList())

    @Test
    fun `a verse is chosen for a topic only if the topic is listened for in it`() {
        for (topic in listOf(Topic.GHUNNAH, Topic.IKHFA, Topic.QALQALAH, Topic.MADD_NATURAL, Topic.MADD_MUTTASIL, Topic.MADD_ARID, Topic.IDGHAM)) {
            val picked = Drills.pick(surahs, topic, 50, Random(1))
            assertTrue(picked.isNotEmpty(), "$topic: none found")
            for (drill in picked) {
                val verse = surahs.getValue(drill.surah)[drill.verse - 1]
                assertTrue(verse.size in 3..10, "$topic: ${drill.surah}:${drill.verse} has ${verse.size} words")
                assertTrue(Drills.has(verse, topic), "$topic isn't in ${drill.surah}:${drill.verse}")
            }
            assertEquals(picked.size, picked.toSet().size)
        }
        // Not every verse has every rule: there are verses with no hidden nun, and they aren't chosen for it.
        val ikhfa = Drills.pick(surahs, Topic.IKHFA, 500, Random(1)).size
        val all = Drills.pick(surahs, Topic.MADD_NATURAL, 500, Random(1)).size
        assertTrue(ikhfa in 1 until all, "$ikhfa verses with ikhfa of $all")
        assertEquals(3, Drills.pick(surahs, Topic.MADD_NATURAL, 3, Random(1)).size)
    }

    @Test
    fun `a recitation is judged for the drill's topic alone`() {
        val drill = Drill(67, 2, Topic.GHUNNAH)
        val short = Correction(Fault.GHUNNAH_SHORT, clear = false, wanted = "ن")
        val madd = Correction(Fault.MADD_SHORT, clear = false, madd = Madd.MUTTASIL, wantedCounts = 4, heardCounts = 2)
        assertTrue(Drills.sound(drill, listOf(word(0), word(1, madd))))
        assertTrue(!Drills.sound(drill, listOf(word(0, short), word(1))))
        assertTrue(!Drills.sound(drill.copy(topic = Topic.MADD_MUTTASIL), listOf(word(0), word(1, madd))))
        assertEquals(Topic.MADD_MUTTASIL, Drills.topicOf(madd))
        assertEquals(Topic.MADD_ARID, Drills.topicOf(Correction(Fault.MADD_LONG, clear = false, madd = Madd.LIN)))
        assertEquals(null, Drills.topicOf(Correction(Fault.VOWEL, clear = true)))
    }

    @Test
    fun `letters are judged by the pair being practised`() {
        val drill = Drill(1, 6, Topic.LETTERS, letters = "صس")
        val said = Correction(Fault.LETTER, clear = true, wanted = "ص", heard = "س")
        assertTrue(!Drills.sound(drill, listOf(word(1, said))))
        assertTrue(Drills.sound(drill, listOf(word(1, Correction(Fault.LETTER, clear = true, wanted = "ق", heard = "ك")))))
        assertEquals("صس", Drills.pairOf(said))
        assertEquals("", Drills.pairOf(Correction(Fault.LETTER, clear = true, wanted = "ص", heard = "ك")))
        assertTrue(Drills.has(verses.getValue("1:6"), Topic.LETTERS, "صس"))
        assertTrue(!Drills.has(verses.getValue("1:2"), Topic.LETTERS, "صس"))
    }

    @Test
    fun `a stop is asked for on a word that sounds different at a stop, and has to be made there`() {
        // In 67:2 a word well inside the verse is asked for: not one of its first two or its last two.
        val verse = verses.getValue("67:2")
        val at = Drills.stopIn(verse)
        assertTrue(at in 2..verse.size - 3, "stop asked after word $at of ${verse.size}")
        assertTrue(verse[at].last.script != verse[at].inside.script)
        assertEquals(-1, Drills.stopIn(verses.getValue("1:2")))
        val drill = Drills.pick(mapOf(67 to listOf(verse)), Topic.STOP, 1, Random(1)).single()
        assertEquals(at, drill.stopAfter)
        // Stopped there and said as it sounds at a stop: sound. Recited on, or stopped elsewhere: not.
        assertTrue(Drills.sound(drill, listOf(word(at - 1), word(at, stopped = true), word(at + 1))))
        assertTrue(!Drills.sound(drill, listOf(word(at - 1), word(at), word(at + 1))))
        assertTrue(!Drills.sound(drill, listOf(word(at - 1, stopped = true), word(at), word(at + 1))))
        assertTrue(!Drills.sound(drill, listOf(word(at, Correction(Fault.VOWEL, clear = true), stopped = true))))
    }

    private fun tone(seconds: Double, loud: Double) = FloatArray((seconds * Mel.SAMPLE_RATE).toInt()) { (loud * sin(2 * PI * 180 * it / Mel.SAMPLE_RATE)).toFloat() }

    @Test
    fun `a hold is timed from the voice beginning to its end`() {
        val holding = Holding()
        holding.add(tone(0.5, 0.0))
        assertEquals(0f, holding.seconds)
        // Two seconds of voice, handed in as the microphone hands it: in small blocks.
        val voice = tone(2.0, 0.3)
        var mid = 0f
        for (at in voice.indices step 1600) {
            holding.add(voice.copyOfRange(at, minOf(at + 1600, voice.size)))
            if (at == 16_000) mid = holding.seconds
        }
        assertTrue(mid in 0.9f..1.2f, "a second in, it says $mid")
        holding.add(tone(0.5, 0.0))
        assertEquals(0f, holding.seconds)
        assertEquals(1, holding.holds.size)
        assertTrue(holding.holds[0] in 1.9f..2.1f, "held ${holding.holds[0]}")
        // At half a second a count, that is four counts.
        assertTrue(Holding.counts(holding.holds[0], 0.5f) in 3.8f..4.2f)
        // A click isn't a hold, and a second voice is a second hold.
        holding.add(tone(0.1, 0.3) + tone(0.5, 0.0) + tone(1.0, 0.25) + tone(0.5, 0.0))
        assertEquals(2, holding.holds.size)
        assertTrue(holding.holds[1] in 0.9f..1.1f, "held ${holding.holds[1]}")
    }
}

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

    /** A word that was said, in which a ghunnah and a madd were listened for; [wanted] is how it should have sounded. */
    private fun word(index: Int, vararg faults: Correction, stopped: Boolean = false, wanted: String = "") = WordRecited(
        index, stopped, faults.toList(), emptyList(), wanted,
        listOf(Awaited(0, 4, Listened.GHUNNAH, index, 4, symbol = 'ن'), Awaited(4, 8, Listened.MADD, index, 4, Madd.MUTTASIL, 'ا')),
    )

    /** A word that wasn't said. */
    private fun unsaid(index: Int) = WordRecited(index, false, listOf(Correction(Fault.WORD_MISSING, clear = true)), emptyList(), "صِرَااطَ")

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
    fun `the words a topic shows in are the ones to point at`() {
        // 1:7 ends on «ٱلضَّآلِّينَ», whose madd is the six-count one; 1:6 has a ص in its second word.
        val last = verses.getValue("1:7")
        assertEquals(listOf(last.size - 1), Drills.places(last, Topic.MADD_LAZIM))
        assertEquals(listOf(1), Drills.places(verses.getValue("1:6"), Topic.LETTERS, "صس").take(1))
        assertEquals(listOf(last.size - 1), Drills.places(last, Topic.STOP))
        assertEquals(emptyList(), Drills.places(verses.getValue("1:2"), Topic.IQLAB))
    }

    @Test
    fun `a recitation is judged for the drill's topic alone`() {
        val drill = Drill(67, 2, Topic.GHUNNAH)
        val short = Correction(Fault.GHUNNAH_SHORT, clear = false, wanted = "ن")
        val madd = Correction(Fault.MADD_SHORT, clear = false, madd = Madd.MUTTASIL, wantedCounts = 4, heardCounts = 2)
        assertEquals(Went.SOUND, Drills.went(drill, listOf(word(0), word(1, madd))))
        assertEquals(Went.MISSED, Drills.went(drill, listOf(word(0, short), word(1))))
        assertEquals(Went.MISSED, Drills.went(drill.copy(topic = Topic.MADD_MUTTASIL), listOf(word(0), word(1, madd))))
        assertEquals(Topic.MADD_MUTTASIL, Drills.topicOf(madd))
        assertEquals(Topic.MADD_ARID, Drills.topicOf(Correction(Fault.MADD_LONG, clear = false, madd = Madd.LIN)))
        assertEquals(null, Drills.topicOf(Correction(Fault.VOWEL, clear = true)))
    }

    @Test
    fun `an exercise sees of a recitation only what it is about`() {
        val short = Correction(Fault.GHUNNAH_SHORT, clear = false, wanted = "ن")
        val madd = Correction(Fault.MADD_SHORT, clear = false, madd = Madd.MUTTASIL, wantedCounts = 4, heardCounts = 2)
        val vowel = Correction(Fault.VOWEL, clear = true, wanted = "َ", heard = "ِ")
        val ghunnah = Awaited(0, 4, Listened.GHUNNAH, 1, 4, symbol = 'ن')
        val held = Awaited(4, 8, Listened.MADD, 1, 4, Madd.MUTTASIL, 'ا')
        val heard = listOf(
            word(0, vowel),
            WordRecited(1, false, listOf(short, madd), listOf(MaddHeard(Madd.MUTTASIL, 2)), "", listOf(ghunnah, held), listOf(Measure(ghunnah, 2, 4, Fault.GHUNNAH_SHORT), Measure(held, 2, 4, Fault.MADD_SHORT))),
            unsaid(2),
        )
        // For the ghunnah: the other vowel and the madd are gone, and so is the word that wasn't said.
        val seen = Drills.only(Drill(67, 2, Topic.GHUNNAH), heard)
        assertEquals(listOf(0, 1), seen.map { it.index })
        assertTrue(seen[0].sound)
        assertEquals(listOf(Fault.GHUNNAH_SHORT), seen[1].corrections.map { it.fault })
        assertEquals(listOf(Listened.GHUNNAH), seen[1].measures.map { it.awaited.kind })
        assertTrue(seen[1].madds.isEmpty())
        // For the madd, the ghunnah is gone.
        val forMadd = Drills.only(Drill(67, 2, Topic.MADD_MUTTASIL), heard)
        assertEquals(listOf(Fault.MADD_SHORT), forMadd[1].corrections.map { it.fault })
        assertEquals(2, forMadd[1].measures.single().heard)
        // For a rule that was sound, nothing is left to go over at all.
        assertTrue(Drills.only(Drill(67, 2, Topic.QALQALAH), heard).all { it.sound && it.measures.isEmpty() })
        // A doubled letter said once where a nun is merged is not the merging's to tell: its place reads as sound.
        val merged = Awaited(0, 2, Listened.IDGHAM, 0, 2, symbol = 'ر')
        val once = WordRecited(0, false, listOf(Correction(Fault.SHADDA_MISSING, clear = true, wanted = "ر")), emptyList(), "", listOf(merged), listOf(Measure(merged, 0, 2, Fault.SHADDA_MISSING)))
        val idgham = Drills.only(Drill(2, 5, Topic.IDGHAM), listOf(once)).single()
        assertTrue(idgham.sound && idgham.measures.single().fault == null)
        // A stop keeps all that was corrected in the word to stop on, and nothing of the others.
        val stop = Drills.only(Drill(2, 8, Topic.STOP, stopAfter = 1), heard)
        assertTrue(stop[0].sound)
        assertEquals(2, stop[1].corrections.size)
    }

    @Test
    fun `the letters a topic falls on are the ones to colour`() {
        fun coloured(words: String, topic: Topic, letters: String = "", stopped: Boolean = true) = words.split(' ').let { each ->
            Drills.letters(each, topic, letters, stopped = stopped).mapIndexed { w, ranges -> ranges.joinToString("+") { each[w].substring(it.first, it.last + 1) } }
        }
        // The nun hidden before a ق, turned before a ب, merged into a ي (which is coloured with it) and into a ر.
        assertEquals(listOf("ن", ""), coloured("مِن قَبْلِ", Topic.IKHFA))
        assertEquals(listOf("ن"), coloured("أَنتُمْ", Topic.IKHFA))
        assertEquals(listOf("ً", ""), coloured("عَلِيمًا قَدِيرًا", Topic.IKHFA))
        assertEquals(listOf("نۢ", ""), coloured("مِنۢ بَعْدِ", Topic.IQLAB))
        assertEquals(listOf("ن", "يَ"), coloured("مَن يَقُولُ", Topic.IDGHAM))
        assertEquals(listOf("ن", ""), coloured("مِن رَّبِّهِمْ", Topic.IDGHAM))
        assertEquals(listOf("نَّ"), coloured("إِنَّ", Topic.GHUNNAH))
        // The echo, in the middle of a word and on the word stopped on.
        assertEquals(listOf("", "دْ"), coloured("لَمْ يَلِدْ", Topic.QALQALAH))
        assertEquals(listOf("قِ"), coloured("ٱلْفَلَقِ", Topic.QALQALAH))
        assertEquals(listOf(""), coloured("ٱلْفَلَقِ", Topic.QALQALAH, stopped = false))
        // An ordinary long vowel is under no sign, and is told by its letters.
        assertEquals(listOf("ا"), coloured("قَالَ", Topic.MADD_NATURAL, stopped = false))
        assertEquals(listOf("و", "ا"), coloured("يَقُولُ يَا", Topic.MADD_NATURAL, stopped = false))
        // Before the last letter of the word stopped on it is the madd of the stop, not the natural one.
        assertEquals(listOf("ي"), coloured("نَسْتَعِينُ", Topic.MADD_ARID))
        assertEquals(listOf(""), coloured("نَسْتَعِينُ", Topic.MADD_NATURAL))
        // Recited on, the same vowel is a natural madd again.
        assertEquals(listOf("ي"), coloured("نَسْتَعِينُ", Topic.MADD_NATURAL, stopped = false))
        assertEquals(listOf("آ", ""), coloured("بِمَآ أُنزِلَ", Topic.MADD_MUNFASIL))
        assertEquals(listOf("آ"), coloured("جَآءَ", Topic.MADD_MUTTASIL))
        // A letter of a pair with its signs; the whole word for a stop.
        assertEquals(listOf("صِّ"), coloured("ٱلصِّرَٰطَ", Topic.LETTERS, "صس"))
        assertEquals(listOf(listOf("قَالَ".indices), emptyList()), Drills.letters(listOf("قَالَ", "لَا"), Topic.STOP, stopAfter = 0))
        // Every topic of a rule names the rules it is made of, and no rule is under two topics.
        val all = Topic.entries.flatMap { Drills.rulesOf(it) }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `three goes in four make a lesson learned`() {
        assertEquals(6, Drills.needed(Drills.GOES))
        assertEquals(3, Drills.needed(4))
        assertEquals(3, Drills.needed(3))
        assertEquals(1, Drills.needed(1))
    }

    @Test
    fun `letters are judged by the pair being practised`() {
        val drill = Drill(1, 6, Topic.LETTERS, letters = "صس")
        val said = Correction(Fault.LETTER, clear = true, wanted = "ص", heard = "س")
        assertEquals(Went.MISSED, Drills.went(drill, listOf(word(1, said, wanted = "صِرَااطَ"))))
        assertEquals(Went.SOUND, Drills.went(drill, listOf(word(1, Correction(Fault.LETTER, clear = true, wanted = "ق", heard = "ك"), wanted = "صِرَااطَ"))))
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
        assertEquals(Went.SOUND, Drills.went(drill, listOf(word(at - 1), word(at, stopped = true), word(at + 1))))
        assertEquals(Went.MISSED, Drills.went(drill, listOf(word(at - 1), word(at), word(at + 1))))
        assertEquals(Went.MISSED, Drills.went(drill, listOf(word(at - 1, stopped = true), word(at), word(at + 1))))
        assertEquals(Went.MISSED, Drills.went(drill, listOf(word(at, Correction(Fault.VOWEL, clear = true), stopped = true))))
        // Stopped on, with the rest of the verse never said: still a stop made there.
        assertEquals(Went.SOUND, Drills.went(drill, listOf(word(at - 1), word(at, stopped = true), unsaid(at + 1))))
    }

    @Test
    fun `a rule in a word that was not said is not judged`() {
        // The ghunnah is listened for in the second word alone, and that word wasn't said: nothing to judge.
        val plain = WordRecited(0, false, emptyList(), emptyList(), "قُل")
        assertEquals(Went.UNHEARD, Drills.went(Drill(67, 2, Topic.GHUNNAH), listOf(plain, unsaid(1))))
        // Said, and sound: judged. And one of two places said is judged by that one.
        assertEquals(Went.SOUND, Drills.went(Drill(67, 2, Topic.GHUNNAH), listOf(plain, word(1))))
        assertEquals(Went.SOUND, Drills.went(Drill(67, 2, Topic.GHUNNAH), listOf(word(0), unsaid(1))))
        // The letters of a pair, when no word that was said has either; and the word to stop on, when it wasn't said.
        assertEquals(Went.UNHEARD, Drills.went(Drill(1, 6, Topic.LETTERS, letters = "صس"), listOf(plain, unsaid(1))))
        assertEquals(Went.UNHEARD, Drills.went(Drill(67, 2, Topic.STOP, stopAfter = 1), listOf(plain, unsaid(1))))
        // On real sound: 67:5 recited up to its eighth word. Its qalqalah is in the first word, which was said; its
        // last words, with the madd a verse ends on, weren't.
        val lines = javaClass.getResourceAsStream("/follow.txt")!!.bufferedReader().readText().trim().split("\n\n").first { it.startsWith("C r3_s67 67:5") }.split('\n')
        fun of(key: Char) = lines.first { it.startsWith("$key ") }.drop(2)
        fun numbers(key: Char) = of(key).split(' ').map { it.toFloat() }.toFloatArray()
        val verse = verses.getValue("67:5")
        val until = of('H').indexOf("وَءَعتَد")
        val heard = HeardScript(of('H').take(until), numbers('P').copyOf(until), numbers('T').copyOf(until + 1))
        val words = Corrections.of(Expected.recited(verse, heard.symbols), heard)
        assertEquals(listOf(8, 9, 10, 11), words.filter { it.unsaid }.map { it.index })
        assertEquals(Went.SOUND, Drills.went(Drill(67, 5, Topic.QALQALAH), words))
        assertEquals(Went.UNHEARD, Drills.went(Drill(67, 5, Topic.MADD_ARID), words.filter { it.index != 7 }))
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

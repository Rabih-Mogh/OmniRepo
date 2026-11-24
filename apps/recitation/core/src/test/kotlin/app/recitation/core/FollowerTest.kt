package app.recitation.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Following a surah as it is recited. Each case in follow.txt is a verse as the tajweed
 * model heard an expert recite it: its reference line (R), what was heard (H), when (T)
 * and how surely (P), the silences in its sound (Q, in seconds) and how long it is (D).
 * The verses of a recording follow one another, so a recitation of the surah is made of
 * them here: every verse a stretch of sound, as the app hears it between two silences.
 */
class FollowerTest {
    private class Said(val name: String, val forms: List<WordForms>, val heard: HeardScript, val silences: FloatArray, val seconds: Float) {
        val alone: List<WordRecited> get() = Corrections.of(Expected.recited(forms, heard.symbols, silences = Silences.among(heard.times, silences)), heard)
    }

    private fun recording(tag: String): List<Said> = javaClass.getResourceAsStream("/follow.txt")!!.bufferedReader().readText().trim().split("\n\n").mapNotNull { block ->
        val lines = block.split('\n')
        fun of(key: Char) = lines.first { it.startsWith("$key ") || it == "$key" }.drop(2)
        fun numbers(key: Char) = of(key).split(' ').filter { it.isNotEmpty() }.map { it.toFloat() }.toFloatArray()
        if (!of('C').startsWith("$tag ")) null else Said(of('C'), TajweedReference.verse(of('R')), HeardScript(of('H'), numbers('P'), numbers('T')), numbers('Q'), of('D').toFloat())
    }

    /** What each word's corrections were, for telling two ways of going over a verse apart. */
    private fun told(words: List<WordRecited>) = words.map { word -> word.corrections.map { it.fault } }

    /** Recites [said] to [follower] from [at] seconds on, a verse a stretch, looking after each; gives the verses finished and the time reached. */
    private fun recite(follower: Follower, said: List<Said>, from: Float = 0f, last: Boolean = true): Pair<List<Follower.Finished>, Float> {
        val finished = ArrayList<Follower.Finished>()
        var at = from
        for ((k, verse) in said.withIndex()) {
            follower.add(verse.heard, at, verse.silences)
            at += verse.seconds
            finished += follower.look(last = last && k == said.lastIndex)
        }
        return finished to at
    }

    @Test
    fun `a surah recited from its first verse to its last is gone over verse by verse`() {
        for (tag in listOf("r1_s1", "r2_s78", "r3_s67")) {
            val said = recording(tag)
            val (finished, _) = recite(Follower(said.map { it.forms }), said)
            assertEquals(said.indices.toList(), finished.map { it.verse }, tag)
            // Each verse comes out as it does recited by itself, but where the place it ends at is a sound out.
            val same = said.indices.count { told(finished[it].words) == told(said[it].alone) }
            assertTrue(same >= said.size - 2, "$tag: only $same of ${said.size} verses went as they do alone")
            for ((k, verse) in finished.withIndex()) assertEquals(said[k].forms.size, verse.words.maxOf { it.index } + 1, "$tag verse ${k + 1}")
        }
    }

    @Test
    fun `the verse and the word being recited are known while they are being said`() {
        val said = recording("r3_s67")
        val follower = Follower(said.map { it.forms })
        var at = 0f
        for ((k, verse) in said.withIndex()) {
            // A third of the way into the verse, and two thirds: the place moves on with the reader, and is in this verse.
            var before = -1
            for (part in listOf(1, 2)) {
                val n = verse.heard.symbols.length * part / 3
                follower.look(HeardScript(verse.heard.symbols.take(n), verse.heard.sureness.copyOf(n), verse.heard.times.copyOf(n + 1)))
                assertEquals(k, follower.verse, "${verse.name}, $part thirds in")
                assertTrue(follower.word >= before && follower.word < verse.forms.size - 1, "${verse.name}: at word ${follower.word} after $before")
                before = follower.word
            }
            follower.add(verse.heard, at, verse.silences)
            at += verse.seconds
            follower.look()
            assertEquals(k to verse.forms.size - 1, follower.verse to follower.word, verse.name)
        }
    }

    @Test
    fun `a reader may begin at any verse`() {
        val said = recording("r2_s78")
        val (finished, _) = recite(Follower(said.map { it.forms }), said.drop(9))
        assertEquals((9 until said.size).toList(), finished.map { it.verse })
    }

    @Test
    fun `a reader who goes back some verses is followed there, and those verses are gone over again`() {
        val said = recording("r3_s67")
        val follower = Follower(said.map { it.forms })
        val (first, at) = recite(follower, said.take(6), last = false)
        assertEquals(listOf(0, 1, 2, 3, 4), first.map { it.verse })
        val (second, _) = recite(follower, said.subList(2, 8), from = at)
        assertEquals(listOf(2, 3, 4, 5, 6, 7), second.map { it.verse })
    }

    @Test
    fun `a verse taken up in its middle is followed but not gone over`() {
        val said = recording("r3_s67")
        val follower = Follower(said.map { it.forms })
        val third = said[2].heard
        val half = third.symbols.length / 2
        // Only the second half of the third verse is heard, then the fourth and the fifth.
        follower.add(HeardScript(third.symbols.drop(half), third.sureness.copyOfRange(half, third.sureness.size), third.times.copyOfRange(half, third.times.size)), 0f, FloatArray(0))
        assertEquals(emptyList(), follower.look().map { it.verse })
        assertEquals(2, follower.verse)
        val (finished, _) = recite(follower, said.subList(3, 5), from = said[2].seconds)
        assertEquals(listOf(3, 4), finished.map { it.verse })
    }

    @Test
    fun `the basmala said before the first verse is followed and not taken for the verse's words`() {
        val basmala = recording("r1_s1").first()
        val said = recording("r2_s78")
        val follower = Follower(said.map { it.forms }, opening = basmala.forms)
        follower.add(basmala.heard, 0f, basmala.silences)
        assertEquals(emptyList(), follower.look().map { it.verse })
        assertEquals(-1, follower.verse)
        val (finished, _) = recite(follower, said.take(3), from = basmala.seconds)
        assertEquals(listOf(0, 1, 2), finished.map { it.verse })
        assertEquals(told(said[0].alone), told(finished[0].words))
    }

    @Test
    fun `a verse heard in two stretches, cut where the reader breathed, is gone over as one`() {
        val said = recording("r3_s67")
        val follower = Follower(said.map { it.forms })
        val finished = ArrayList<Follower.Finished>()
        var at = 0f
        var cut = 0
        for (verse in said.take(6)) {
            // The first silence inside the verse, if it has one: the sounds before its middle, and the rest.
            val inside = Silences.among(verse.heard.times, verse.silences).firstOrNull()
            if (inside == null) follower.add(verse.heard, at, verse.silences) else {
                cut++
                val heard = verse.heard
                follower.add(HeardScript(heard.symbols.take(inside), heard.sureness.copyOf(inside), heard.times.copyOf(inside + 1)), at, verse.silences)
                finished += follower.look()
                follower.add(HeardScript(heard.symbols.drop(inside), heard.sureness.copyOfRange(inside, heard.sureness.size), heard.times.copyOfRange(inside, heard.times.size)), at, FloatArray(0))
            }
            at += verse.seconds
            finished += follower.look()
        }
        finished += follower.look(last = true)
        assertTrue(cut >= 2, "only $cut verses with a breath inside")
        assertEquals(listOf(0, 1, 2, 3, 4, 5), finished.map { it.verse })
        val same = (0..5).count { told(finished[it].words) == told(said[it].alone) }
        assertTrue(same >= 5, "only $same of 6 verses went as they do alone")
    }

    @Test
    fun `a few words are found where they fit best in a longer run`() {
        assertEquals(7, Edits.within("abcdefghij", "efg")!!.until)
        assertEquals(0, Edits.within("abcdefghij", "efg")!!.cost)
        assertEquals(1, Edits.within("abcdefghij", "exg")!!.cost)
        // Said twice in the run: the first, unless the reader is already past it.
        assertEquals(3, Edits.within("abcxxabc", "abc")!!.until)
        assertEquals(8, Edits.within("abcxxabc", "abc", after = 4)!!.until)
        assertEquals(8, Edits.within("abcxxabc", "abc", from = 2)!!.until)
    }
}

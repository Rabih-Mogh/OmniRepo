package app.recitation.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sharing a word's time among its letters and vowel signs, and telling where the reciter stopped. */
class PointerTest {

    /** Each word's sounds one after another, [gap] ms apart, with [waits] extra ms before the words named. */
    private fun timings(verses: List<List<String>>, gap: Int = 120, waits: Map<Int, Int> = emptyMap()): Timings {
        var ms = 1000
        var w = 0
        return Timings(
            verses.map { verse ->
                verse.mapIndexed { i, word ->
                    ms += waits[w] ?: 0
                    w++
                    val start = ms
                    val reached = ArrayList<Int>()
                    for (phone in Phonemizer.word(word, verse.getOrNull(i + 1))) {
                        if (phone.optional) continue
                        if (reached.isEmpty() || phone.upTo > reached[reached.size - 2]) {
                            reached += phone.upTo
                            reached += ms
                        }
                        ms += gap
                    }
                    WordTiming(start, reached.toIntArray())
                }
            },
            0.9f,
        )
    }

    /** The stretches of a word as "letters@ms". */
    private fun stretches(word: String, timing: WordTiming): List<String> {
        var from = 0
        return (0 until timing.letters.size step 2).map { k ->
            val to = timing.letters[k]
            "${word.substring(from, to)}@${timing.letters[k + 1]}".also { from = to }
        }
    }

    @Test
    fun `a letter merged into the next word is lit while that word's doubled letter begins`() {
        // "min rabbihim": the n has no sound of its own; the doubled r starts the next word.
        val verse = listOf("مِن", "رَّبِّهِمْ")
        val raw = timings(listOf(verse))
        val refined = Pointer.refine(listOf(verse), raw).timings.verses[0]
        // Raw, the light never reaches the n.
        assertEquals(2, raw.verses[0][0].letters.let { it[it.size - 2] })
        // Refined, the n is lit from the moment the r begins...
        val min = stretches(verse[0], refined[0])
        assertEquals("ن@${raw.verses[0][1].startMs}", min.last())
        // ...and the next word starts a little later, when the n has had its share.
        assertTrue(refined[1].startMs > raw.verses[0][1].startMs)
        assertTrue(refined[1].letters[1] >= refined[1].startMs)
    }

    @Test
    fun `a stop keeps a merged letter where it was`() {
        val verse = listOf("مِن", "رَّبِّهِمْ")
        // Two seconds pass before the second word: the reciter stopped on the first.
        val raw = timings(listOf(verse), waits = mapOf(1 to 2000))
        val refined = Pointer.refine(listOf(verse), raw)
        assertTrue(refined.stops[0])
        assertEquals(raw.verses[0][1].startMs, refined.timings.verses[0][1].startMs)
    }

    @Test
    fun `a letter and its vowel sign each get their time`() {
        val verse = listOf("بِسْمِ", "ٱللَّهِ")
        val refined = Pointer.refine(listOf(verse), timings(listOf(verse))).timings.verses[0]
        val bismi = stretches(verse[0], refined[0]).map { it.substringBefore('@') }
        assertEquals(listOf("ب", "ِ", "سْ", "م", "ِ"), bismi)
        // Times never go backwards.
        val times = (1 until refined[0].letters.size step 2).map { refined[0].letters[it] }
        assertEquals(times.sorted(), times)
    }

    @Test
    fun `timings kept by an earlier version, a letter and its vowel together, are shared out`() {
        // As 1.43 and earlier saved them: one stretch for "bi", one for "s", one for "mi".
        val old = Timings(listOf(listOf(WordTiming(1000, intArrayOf(2, 1000, 4, 1300, 6, 1500)), WordTiming(2000, intArrayOf(1, 2000)))), 0.9f)
        val verse = listOf("بِسْمِ", "ٱللَّهِ")
        val refined = Pointer.refine(listOf(verse), old).timings.verses[0][0]
        val parts = stretches(verse[0], refined)
        assertEquals(listOf("ب", "ِ", "سْ", "م", "ِ"), parts.map { it.substringBefore('@') })
        // The letter keeps the start; its sign follows shortly after, before the next letter.
        assertEquals("ب@1000", parts[0])
        val kasra = parts[1].substringAfter('@').toInt()
        assertTrue(kasra in 1001 until 1300)
    }

    @Test
    fun `silent letters before a sounded one are passed over quickly, not skipped`() {
        // "ar-rahman" run into: the joining alif and the l are not sounded.
        val verse = listOf("ٱللَّهِ", "ٱلرَّحْمَـٰنِ")
        val refined = Pointer.refine(listOf(verse), timings(listOf(verse))).timings.verses[0][1]
        val parts = stretches(verse[1], refined).map { it.substringBefore('@') }
        assertEquals(listOf("ٱ", "ل"), parts.take(2))
        assertTrue(parts[2].startsWith("ر"))
    }

    @Test
    fun `the reciter is taken to have stopped where he waits long`() {
        val verses = listOf(listOf("بِسْمِ", "ٱللَّهِ", "ٱلرَّحْمَـٰنِ", "ٱلرَّحِيمِ"), listOf("ٱلْحَمْدُ", "لِلَّهِ"))
        // A breath after the first verse, none inside it.
        val stops = Pointer.refine(verses, timings(verses, waits = mapOf(4 to 1500))).stops
        assertEquals(listOf(false, false, false, true, false, true), stops.toList())
        // Running straight on into the next verse: no stop there.
        assertEquals(false, Pointer.refine(verses, timings(verses)).stops[3])
    }

    @Test
    fun `words not timed yet are left alone`() {
        val verses = listOf(listOf("بِسْمِ", "ٱللَّهِ", "ٱلرَّحْمَـٰنِ", "ٱلرَّحِيمِ"))
        val all = timings(verses)
        val soFar = Timings(listOf(all.verses[0].take(2)), 1f, heardMs = 3000)
        val refined = Pointer.refine(verses, soFar)
        assertEquals(2, refined.timings.verses[0].size)
        assertEquals(3000, refined.timings.heardMs)
        // Past what has been timed, a stop is only assumed at the verse's end.
        assertEquals(listOf(false, false, false, true), refined.stops.toList())
    }
}

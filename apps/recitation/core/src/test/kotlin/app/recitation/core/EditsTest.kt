package app.recitation.core

import app.recitation.core.Edits.Kind.ADDED
import app.recitation.core.Edits.Kind.LEFT_OUT
import app.recitation.core.Edits.Kind.REPLACED
import app.recitation.core.Edits.Kind.SAME
import kotlin.test.Test
import kotlin.test.assertEquals

class EditsTest {
    @Test
    fun `counts the fewest changes`() {
        assertEquals(0, Edits.distance("بِسمِ", "بِسمِ"))
        assertEquals(1, Edits.distance("بِسمِ", "بِصمِ"))
        assertEquals(2, Edits.distance("مَاالِكِ", "مَالِكِ") + Edits.distance("ab", "abc"))
        assertEquals(3, Edits.distance("", "abc"))
    }

    @Test
    fun `names each change and where it is in both`() {
        // A letter replaced, a count of a long vowel left out (the later of the two, as the library does), a sound added at the end.
        val changes = Edits.between("صِرَااطَ", "سِرَاطَن")
        assertEquals(
            listOf(
                Edits.Change(REPLACED, 0, 1, 0, 1), Edits.Change(SAME, 1, 5, 1, 5), Edits.Change(LEFT_OUT, 5, 6, 5, 5),
                Edits.Change(SAME, 6, 8, 5, 7), Edits.Change(ADDED, 8, 8, 7, 8),
            ),
            changes,
        )
    }

    @Test
    fun `the changes cover both runs whole, in order`() {
        for ((a, b) in listOf("ءَلحَمدُ" to "ءَلهَمدُو", "" to "اا", "ننن" to "", "abcdef" to "azcdxxf")) {
            val changes = Edits.between(a, b)
            var x = 0
            var y = 0
            for (c in changes) {
                assertEquals(x, c.from); assertEquals(y, c.toFrom)
                x = c.until; y = c.toUntil
            }
            assertEquals(a.length, x); assertEquals(b.length, y)
            assertEquals(Edits.distance(a, b), changes.filter { it.kind != SAME }.sumOf { maxOf(it.until - it.from, it.toUntil - it.toFrom) })
        }
    }
}

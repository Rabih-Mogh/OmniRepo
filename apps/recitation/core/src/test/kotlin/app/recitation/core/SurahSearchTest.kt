package app.recitation.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SurahSearchTest {
    private fun names(query: String) = SurahSearch.find(query).surahs.map { it.latin }

    @Test
    fun `a surah is found by its number`() {
        assertEquals(listOf("Al-Kahf"), names("18"))
        assertEquals(listOf("Al-Kahf"), names("١٨"))
        assertEquals(emptyList(), names("115"))
    }

    @Test
    fun `a surah is found by part of its name however it is typed`() {
        assertEquals(listOf("Al-Baqarah"), names("baqara"))
        assertEquals(listOf("Al-Baqarah"), names("Al Baqarah"))
        assertEquals(listOf("Al-Baqarah"), names("البقرة"))
        assertEquals(listOf("Al-Baqarah"), names("بقره"))
        // What the name means finds it too.
        assertEquals(listOf("Al-Baqarah"), names("the cow"))
    }

    @Test
    fun `a verse is found by its numbers`() {
        assertEquals(2 to 255, SurahSearch.find("2:255").verse)
        assertEquals(2 to 255, SurahSearch.find("2 255").verse)
        assertEquals(2 to 255, SurahSearch.find("٢:٢٥٥").verse)
        assertEquals(18 to 10, SurahSearch.find("kahf 10").verse)
        assertEquals(2 to 255, SurahSearch.find("البقرة ٢٥٥").verse)
        // Al-Fatihah has seven verses.
        assertNull(SurahSearch.find("1:8").verse)
        assertEquals(listOf("Al-Fatihah"), names("1:8"))
    }

    @Test
    fun `text is compared without its marks`() {
        assertEquals("الرحمن", SurahSearch.plain("ٱلرَّحْمَـٰنِ"))
        assertEquals("العالمين", SurahSearch.plain("الْعَالَمِينَ"))
        assertEquals("lordoftheworlds", SurahSearch.plain("Lord of the worlds -"))
    }

    @Test
    fun `nothing typed finds nothing`() {
        assertEquals(emptyList(), names("  "))
        assertNull(SurahSearch.find("").verse)
    }
}

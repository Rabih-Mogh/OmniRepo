package app.safeguard.core

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDateTime
import java.util.EnumSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HoursTest {
    private val weekdays = EnumSet.range(MONDAY, FRIDAY)
    private val working = Hours(9 * 60, 17 * 60, weekdays)

    // 23 November 2026 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute)

    @Test
    fun `working hours hold from nine to five on working days`() {
        assertFalse(working.holds(at(23, 8, 59)))
        assertTrue(working.holds(at(23, 9)))
        assertTrue(working.holds(at(27, 16, 59)))
        assertFalse(working.holds(at(27, 17)))
        assertFalse(working.holds(at(28, 12)))
    }

    @Test
    fun `hours that run past midnight belong to the day they start on`() {
        val night = Hours(22 * 60, 6 * 60, setOf(FRIDAY))
        assertTrue(night.holds(FRIDAY, 23 * 60))
        assertTrue(night.holds(SATURDAY, 5 * 60))
        assertFalse(night.holds(SATURDAY, 23 * 60))
        assertFalse(night.holds(FRIDAY, 5 * 60))
    }

    @Test
    fun `the same start and end is the whole day`() {
        val weekend = Hours(0, 0, setOf(SATURDAY, SUNDAY))
        assertTrue(weekend.holds(SATURDAY, 0))
        assertTrue(weekend.holds(SUNDAY, 23 * 60 + 59))
        assertFalse(weekend.holds(MONDAY, 12 * 60))
    }

    @Test
    fun `closed hours say when they end`() {
        assertEquals(at(23, 17), working.until(at(23, 10, 30)))
        assertEquals(at(28, 6), Hours(22 * 60, 6 * 60, setOf(FRIDAY)).until(at(27, 23)))
        // Two whole days in a row end when the second does.
        assertEquals(at(30, 0), Hours(0, 0, setOf(SATURDAY, SUNDAY)).until(at(28, 15)))
        assertNull(Hours(0, 0, DayOfWeek.values().toSet()).until(at(23, 12)))
    }

    @Test
    fun `wider hours cover narrower ones and not the other way round`() {
        val longer = Hours(8 * 60, 18 * 60, DayOfWeek.values().toSet())
        assertTrue(longer.covers(working))
        assertFalse(working.covers(longer))
        assertTrue(working.covers(working))
        // Starting an hour later lets the first hour through.
        assertFalse(Hours(10 * 60, 17 * 60, weekdays).covers(working))
        assertFalse(Hours(9 * 60, 17 * 60, setOf(MONDAY)).covers(working))
    }
}

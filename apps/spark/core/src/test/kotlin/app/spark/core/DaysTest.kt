package app.spark.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DaysTest {
    private fun day(days: Days, today: Long, reels: Int, lesson: Boolean = true, goal: Int = 3, lessonsLeft: Boolean = true): Days {
        var d = days
        repeat(reels) { d = d.watched(today, goal, lessonsLeft) }
        if (lesson) d = d.learned(today, goal, lessonsLeft)
        return d
    }

    @Test
    fun `the goal is met by the reels and a lesson together`() {
        val reelsOnly = day(Days(), 100, reels = 3, lesson = false)
        assertFalse(reelsOnly.metOn(100))
        val both = reelsOnly.learned(100, 3, true)
        assertTrue(both.metOn(100))
        assertEquals(1, both.streakOn(100))
        // The lesson first, then the reels, comes to the same.
        assertTrue(day(Days().learned(100, 3, true), 100, reels = 3, lesson = false).metOn(100))
    }

    @Test
    fun `no lesson is asked for when none is left to take`() {
        assertTrue(day(Days(), 100, reels = 3, lesson = false, lessonsLeft = false).metOn(100))
    }

    @Test
    fun `the streak grows a day at a time and counts each day once`() {
        var d = day(Days(), 100, reels = 5)
        assertEquals(1, d.streakOn(100))
        d = day(d, 101, reels = 3)
        d = day(d, 102, reels = 3)
        assertEquals(3, d.streakOn(102))
    }

    @Test
    fun `a new day starts from nothing but keeps the streak until it is missed`() {
        val d = day(Days(), 100, reels = 3)
        assertEquals(0, d.on(101).reels)
        assertFalse(d.on(101).lesson)
        assertEquals(1, d.streakOn(101))
        assertEquals(0, d.streakOn(102))
        assertEquals(1, day(d, 103, reels = 3).streakOn(103))
    }
}

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
    fun `a day counts towards the streak well short of the goal`() {
        // Two reels are not enough; the third is, though the goal is ten.
        val two = day(Days(), 100, reels = 2, lesson = false, goal = 10)
        assertEquals(0, two.streakOn(100))
        val three = two.watched(100, 10, true)
        assertEquals(1, three.streakOn(100))
        assertFalse(three.metOn(100))
        // A lesson alone does it too.
        assertEquals(1, Days().learned(100, 10, true).streakOn(100))
        // And reaching the goal later the same day doesn't count the day twice.
        assertEquals(1, day(three, 100, reels = 7, goal = 10).streakOn(100))
        assertTrue(day(three, 100, reels = 7, goal = 10).metOn(100))
    }

    @Test
    fun `a new day starts from nothing and keeps the streak`() {
        val d = day(Days(), 100, reels = 3)
        assertEquals(0, d.on(101).reels)
        assertFalse(d.on(101).lesson)
        assertEquals(1, d.streakOn(101))
    }

    @Test
    fun `one missed day is let go and two are not`() {
        val d = day(Days(), 100, reels = 3)
        // 101 is missed: the streak still stands on 102, and carries on from there.
        assertEquals(1, d.streakOn(102))
        assertEquals(2, day(d, 102, reels = 3).streakOn(102))
        // 101 and 102 both missed: it is gone, and the next day starts a new one.
        assertEquals(0, d.streakOn(103))
        assertEquals(1, day(d, 103, reels = 3).streakOn(103))
    }

    @Test
    fun `only one missed day in a week is let go`() {
        var d = day(Days(), 100, reels = 3)
        d = day(d, 102, reels = 3) // 101 let go
        d = day(d, 103, reels = 3)
        assertEquals(3, d.streakOn(103))
        // 104 missed too, three days after the last one: that ends it.
        assertEquals(0, d.streakOn(105))
        assertEquals(1, day(d, 105, reels = 3).streakOn(105))
    }

    @Test
    fun `a week after a missed day, another can be let go`() {
        var d = day(Days(), 100, reels = 3)
        d = day(d, 102, reels = 3) // 101 let go
        for (today in 103L..107L) d = day(d, today, reels = 3)
        assertEquals(7, d.streakOn(107))
        // 108 is missed, seven days after 101.
        assertEquals(7, d.streakOn(109))
        assertEquals(8, day(d, 109, reels = 3).streakOn(109))
    }
}

package app.accentcoach.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticeTest {

    private val today = LocalDate.of(2026, 11, 25)
    private fun ago(days: Long) = today.minusDays(days)
    private fun practice(vararg days: Pair<LocalDate, Int>) = Practice(days.toMap())

    @Test
    fun nothingPractisedIsNoStreak() {
        val streak = Practice().streak(today)
        assertEquals(0, streak.today)
        assertEquals(0, streak.days)
        assertEquals(0, streak.best)
        assertFalse(streak.todayDone)
    }

    @Test
    fun aDayCountsOnceItsGoalIsMet() {
        var practice = Practice()
        repeat(Streak.GOAL - 1) { practice = practice.recorded(today) }
        assertEquals(0, practice.streak(today).days)
        assertFalse(practice.streak(today).todayDone)
        practice = practice.recorded(today)
        assertEquals(1, practice.streak(today).days)
        assertTrue(practice.streak(today).todayDone)
        assertEquals(Streak.GOAL, practice.streak(today).today)
    }

    @Test
    fun aDayNotYetFinishedDoesNotBreakTheRun() {
        val streak = practice(ago(2) to 5, ago(1) to 8, today to 2).streak(today)
        assertEquals(2, streak.days)
        assertEquals(2, streak.today)
        assertFalse(streak.todayDone)
    }

    @Test
    fun todayAddsToTheRunOnceDone() {
        assertEquals(3, practice(ago(2) to 5, ago(1) to 8, today to 5).streak(today).days)
    }

    @Test
    fun aMissedDayEndsTheRunButNotTheBest() {
        val streak = practice(ago(5) to 5, ago(4) to 5, ago(3) to 5, ago(2) to 4, ago(1) to 6).streak(today)
        assertEquals(1, streak.days)
        assertEquals(3, streak.best)
    }

    @Test
    fun aRunThatEndedBeforeYesterdayIsOver() {
        val streak = practice(ago(3) to 5, ago(2) to 5).streak(today)
        assertEquals(0, streak.days)
        assertEquals(2, streak.best)
    }

    @Test
    fun theWeekIsTheLastSevenDaysOldestFirst() {
        val week = practice(ago(6) to 1, ago(7) to 9, today to 3).streak(today).week
        assertEquals(List(7) { ago(6L - it) }, week.map { it.first })
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 3), week.map { it.second })
    }

    @Test
    fun theBestGoOfADrillIsKept() {
        val lesson = Lesson("th", "TH", "θ", "", "", List(4) { Word("think", listOf("θ", "ɪ", "ŋ", "k")) }, emptyList())
        var practice = Practice().drilled("th", 2)
        assertFalse(practice.learned(lesson))
        practice = practice.drilled("th", 3).drilled("th", 1)
        assertEquals(3, practice.drills["th"])
        assertTrue(practice.learned(lesson))
    }
}

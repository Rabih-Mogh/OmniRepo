package app.spark.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplyTest {
    @Test
    fun `the feed is low when it can't fill the day's goal`() {
        assertFalse(Supply.low(unseen = 10, goal = 10, newest = 100, today = 100))
        assertTrue(Supply.low(unseen = 9, goal = 10, newest = 100, today = 100))
        assertTrue(Supply.low(unseen = 0, goal = 5, newest = 100, today = 100))
    }

    @Test
    fun `the feed is low when its newest pack is more than a week old`() {
        assertFalse(Supply.low(unseen = 40, goal = 10, newest = 100, today = 107))
        assertTrue(Supply.low(unseen = 40, goal = 10, newest = 100, today = 108))
    }
}

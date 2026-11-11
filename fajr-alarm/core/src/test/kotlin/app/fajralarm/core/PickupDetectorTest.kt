package app.fajralarm.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PickupDetectorTest {

    @Test
    fun lyingFlatAndVibratingIsNotPickedUp() {
        val d = PickupDetector()
        // Face up on a table, with the vibration motor shaking it hard on every axis.
        repeat(2_000) { i ->
            val shake = if (i % 2 == 0) 3f else -3f
            d.offer(shake, -shake, 9.81f + shake)
        }
        assertFalse(d.pickedUp)
    }

    @Test
    fun tiltingThePhoneUpCountsAsPickedUp() {
        val d = PickupDetector()
        repeat(50) { d.offer(0f, 0f, 9.81f) }
        // Lift it to about 60° from flat, as when holding it to look at a sink.
        val a = Math.toRadians(60.0)
        repeat(100) { d.offer(0f, (9.81 * sin(a)).toFloat(), (9.81 * cos(a)).toFloat()) }
        assertTrue(d.pickedUp)
    }

    @Test
    fun aBriefJoltIsFilteredOut() {
        val d = PickupDetector()
        repeat(50) { d.offer(0f, 0f, 9.81f) }
        // A single knock on the nightstand.
        d.offer(0f, 9.81f, 0f)
        repeat(50) { d.offer(0f, 0f, 9.81f) }
        assertFalse(d.pickedUp)
    }

    @Test
    fun staysPickedUpOnceLifted() {
        val d = PickupDetector()
        repeat(50) { d.offer(0f, 0f, 9.81f) }
        repeat(100) { d.offer(0f, 9.81f, 0f) }
        repeat(100) { d.offer(0f, 0f, 9.81f) }
        assertTrue(d.pickedUp)
    }
}

package app.fajralarm.core

import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

enum class MoonPhaseName {
    NEW,
    WAXING_CRESCENT,
    FIRST_QUARTER,
    WAXING_GIBBOUS,
    FULL,
    WANING_GIBBOUS,
    LAST_QUARTER,
    WANING_CRESCENT,
}

/** How the moon looks at one moment. */
data class MoonPhase(
    /** How far the moon is ahead of the sun around the sky, 0 to 360 degrees: 0 new, 180 full. */
    val elongation: Double,
    /** The lit part of its disc, 0 to 1. */
    val illumination: Double,
    /** Days since the new moon. */
    val ageDays: Double,
    val name: MoonPhaseName,
) {
    /** Growing towards full. */
    val waxing: Boolean get() = elongation < 180.0
}

/**
 * The moon's phase, worked out on the phone from the low-precision positions of the
 * sun and the moon (good to about half a degree, which is an hour of the phase).
 */
object Moon {

    private const val DEGREES_PER_DAY = 360.0 / 29.530588853

    /** A phase counts as new, quarter or full within half a day of the exact moment. */
    private const val NAMED_WITHIN_DEGREES = DEGREES_PER_DAY / 2

    private val PRINCIPAL = listOf(
        0.0 to MoonPhaseName.NEW,
        90.0 to MoonPhaseName.FIRST_QUARTER,
        180.0 to MoonPhaseName.FULL,
        270.0 to MoonPhaseName.LAST_QUARTER,
    )

    fun phaseAt(instant: Instant): MoonPhase {
        val elongation = elongation(instant)
        val principal = PRINCIPAL.firstOrNull { (angle, _) -> distance(elongation, angle) < NAMED_WITHIN_DEGREES }?.second
        val name = principal ?: when {
            elongation < 90 -> MoonPhaseName.WAXING_CRESCENT
            elongation < 180 -> MoonPhaseName.WAXING_GIBBOUS
            elongation < 270 -> MoonPhaseName.WANING_GIBBOUS
            else -> MoonPhaseName.WANING_CRESCENT
        }
        return MoonPhase(
            elongation = elongation,
            illumination = (1 - cos(elongation * PI / 180)) / 2,
            ageDays = elongation / DEGREES_PER_DAY,
            name = name,
        )
    }

    /** The next new moon, first quarter, full moon and last quarter after [from], soonest first. */
    fun nextPrincipal(from: Instant): List<Pair<MoonPhaseName, Instant>> {
        val now = elongation(from)
        return PRINCIPAL.map { (angle, name) ->
            // Start from the mean rate, then close in: the moon's speed varies over the month.
            val ahead = ((angle - now) % 360 + 360) % 360
            var at = from.plus(days(ahead / DEGREES_PER_DAY))
            repeat(4) { at = at.plus(days(signed(angle - elongation(at)) / DEGREES_PER_DAY)) }
            name to at
        }.filter { it.second.isAfter(from) }.sortedBy { it.second }
    }

    private fun days(days: Double): Duration = Duration.ofSeconds((days * 86_400).roundToLong())

    /** The difference [degrees] folded into -180 to 180. */
    private fun signed(degrees: Double): Double = degrees - 360 * floor((degrees + 180) / 360)

    private fun distance(a: Double, b: Double): Double = kotlin.math.abs(signed(a - b))

    private fun elongation(instant: Instant): Double {
        // Days and centuries from the start of 2000 (J2000.0).
        val d = (instant.epochSecond - 946_728_000L) / 86_400.0
        val t = d / 36_525.0
        fun s(degrees: Double) = sin(degrees * PI / 180)
        val g = 357.529 + 0.98560028 * d
        val sun = 280.459 + 0.98564736 * d + 1.915 * s(g) + 0.020 * s(2 * g)
        val moon = 218.32 + 481_267.881 * t +
            6.29 * s(135.0 + 477_198.87 * t) -
            1.27 * s(259.3 - 413_335.36 * t) +
            0.66 * s(235.7 + 890_534.22 * t) +
            0.21 * s(269.9 + 954_397.74 * t) -
            0.19 * s(357.5 + 35_999.05 * t) -
            0.11 * s(186.5 + 966_404.03 * t)
        return ((moon - sun) % 360 + 360) % 360
    }
}

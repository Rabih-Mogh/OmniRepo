package app.spark.core

import kotlin.math.E
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExprTest {
    private fun value(text: String, x: Double = 0.0, a: Double = 0.0) = Expr.parse(text).at(x, a)

    @Test
    fun `multiplying comes before adding, and brackets before both`() {
        assertEquals(14.0, value("2 + 3 * 4"))
        assertEquals(20.0, value("(2 + 3) * 4"))
        assertEquals(1.0, value("10 - 4 - 5"))
        assertEquals(2.0, value("12 / 3 / 2"))
    }

    @Test
    fun `powers group from the right and bind tighter than a minus sign`() {
        assertEquals(512.0, value("2^3^2"))
        assertEquals(-9.0, value("-x^2", x = 3.0))
        assertEquals(9.0, value("(-x)^2", x = 3.0))
        assertEquals(0.25, value("2^-2"))
    }

    @Test
    fun `x and a take the values given`() {
        assertEquals(19.0, value("a*x^2 + 1", x = 3.0, a = 2.0))
    }

    @Test
    fun `the functions and the constants are known`() {
        assertEquals(1.0, value("sin(pi/2)"), 1e-12)
        assertEquals(1.0, value("ln(e)"), 1e-12)
        assertEquals(3.0, value("log(1000)"), 1e-12)
        assertEquals(5.0, value("sqrt(abs(-25))"))
        assertEquals(E, value("exp(1)"), 1e-12)
        assertEquals(2.0, value("min(2, max(1, 3))"))
        assertEquals(PI, value("pi"))
    }

    @Test
    fun `what isn't a formula is refused`() {
        for (text in listOf("", "2 +", "2x", "ax", "(1 + 2", "sin 2", "min(1)", "y", "1 2", "2 $ 3", "1e6")) {
            assertFailsWith<Expr.Malformed>(text) { Expr.parse(text) }
        }
    }
}

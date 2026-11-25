package app.spark.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A formula a pack can carry as text, like `a*x^2 + 1`, for the curves the app draws and
 * the ones a lesson lets you bend. It knows `x`, the parameter `a`, numbers, `pi` and `e`,
 * the operators `+ - * / ^`, brackets, and the functions sin, cos, tan, sqrt, abs, exp,
 * ln, log (base 10), min and max.
 */
class Expr private constructor(val text: String, private val root: Node) {

    class Malformed(message: String) : Exception(message)

    /** The formula's value at [x], with the parameter at [a]. */
    fun at(x: Double, a: Double = 0.0): Double = root.at(x, a)

    override fun equals(other: Any?) = other is Expr && other.text == text
    override fun hashCode() = text.hashCode()
    override fun toString() = text

    private fun interface Node {
        fun at(x: Double, a: Double): Double
    }

    companion object {
        fun parse(text: String): Expr {
            if (text.length > 200) throw Malformed("the formula is too long")
            val parser = Parser(text)
            val root = parser.sum()
            parser.blank()
            if (parser.at < text.length) throw Malformed("unexpected '${text[parser.at]}' in \"$text\"")
            return Expr(text, root)
        }
    }

    private class Parser(private val text: String) {
        var at = 0
        private var depth = 0

        fun blank() {
            while (at < text.length && text[at] == ' ') at++
        }

        private fun take(c: Char): Boolean {
            blank()
            if (at < text.length && text[at] == c) {
                at++
                return true
            }
            return false
        }

        fun sum(): Node {
            if (++depth > 40) throw Malformed("the formula is nested too deeply")
            var left = product()
            while (true) {
                val l = left
                left = when {
                    take('+') -> product().let { r -> Node { x, a -> l.at(x, a) + r.at(x, a) } }
                    take('-') -> product().let { r -> Node { x, a -> l.at(x, a) - r.at(x, a) } }
                    else -> {
                        depth--
                        return left
                    }
                }
            }
        }

        private fun product(): Node {
            var left = signed()
            while (true) {
                val l = left
                left = when {
                    take('*') -> signed().let { r -> Node { x, a -> l.at(x, a) * r.at(x, a) } }
                    take('/') -> signed().let { r -> Node { x, a -> l.at(x, a) / r.at(x, a) } }
                    else -> return left
                }
            }
        }

        // A minus sign binds looser than a power, so -x^2 is -(x^2).
        private fun signed(): Node {
            if (take('-')) {
                val inner = signed()
                return Node { x, a -> -inner.at(x, a) }
            }
            return power()
        }

        // Powers group from the right: 2^3^2 is 2^(3^2). The exponent may carry its own sign.
        private fun power(): Node {
            val base = atom()
            if (take('^')) {
                val exponent = signed()
                return Node { x, a -> base.at(x, a).pow(exponent.at(x, a)) }
            }
            return base
        }

        private fun atom(): Node {
            blank()
            if (at >= text.length) throw Malformed("\"$text\" ends early")
            val c = text[at]
            if (c == '(') {
                at++
                val inner = sum()
                if (!take(')')) throw Malformed("a bracket isn't closed in \"$text\"")
                return inner
            }
            if (c in '0'..'9' || c == '.') {
                val start = at
                while (at < text.length && (text[at] in '0'..'9' || text[at] == '.')) at++
                val value = text.substring(start, at).toDoubleOrNull() ?: throw Malformed("\"${text.substring(start, at)}\" isn't a number")
                return Node { _, _ -> value }
            }
            if (c in 'a'..'z') {
                val start = at
                while (at < text.length && text[at] in 'a'..'z') at++
                return named(text.substring(start, at))
            }
            throw Malformed("unexpected '$c' in \"$text\"")
        }

        private fun named(name: String): Node = when (name) {
            "x" -> Node { x, _ -> x }
            "a" -> Node { _, a -> a }
            "pi" -> Node { _, _ -> Math.PI }
            "e" -> Node { _, _ -> Math.E }
            "sin" -> one(::sin)
            "cos" -> one(::cos)
            "tan" -> one(::tan)
            "sqrt" -> one(::sqrt)
            "abs" -> one(::abs)
            "exp" -> one(::exp)
            "ln" -> one(::ln)
            "log" -> one(::log10)
            "min" -> two(::min)
            "max" -> two(::max)
            else -> throw Malformed("\"$name\" isn't something a formula knows")
        }

        private fun one(f: (Double) -> Double): Node {
            if (!take('(')) throw Malformed("a function needs brackets in \"$text\"")
            val inner = sum()
            if (!take(')')) throw Malformed("a bracket isn't closed in \"$text\"")
            return Node { x, a -> f(inner.at(x, a)) }
        }

        private fun two(f: (Double, Double) -> Double): Node {
            if (!take('(')) throw Malformed("a function needs brackets in \"$text\"")
            val first = sum()
            if (!take(',')) throw Malformed("min and max take two values in \"$text\"")
            val second = sum()
            if (!take(')')) throw Malformed("a bracket isn't closed in \"$text\"")
            return Node { x, a -> f(first.at(x, a), second.at(x, a)) }
        }
    }
}

package app.fajralarm.core

/**
 * Reads a typed latitude or longitude, or null if it isn't a number. Keyboards differ
 * by language: Arabic ones type ٣٣٫٨٩ where English ones type 33.89, and many use a
 * comma for the decimal mark.
 */
fun parseCoordinate(text: String): Double? =
    text.trim().map { c ->
        when {
            c == ',' || c == '٫' -> '.'
            c == '−' -> '-'
            c.isDigit() -> c.digitToInt().digitToChar()
            else -> c
        }
    }.joinToString("").toDoubleOrNull()

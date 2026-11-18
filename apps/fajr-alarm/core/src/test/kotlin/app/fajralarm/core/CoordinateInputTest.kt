package app.fajralarm.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoordinateInputTest {

    @Test
    fun readsEnglishDigitsWithAPointOrAComma() {
        assertEquals(33.8938, parseCoordinate("33.8938"))
        assertEquals(33.8938, parseCoordinate(" 33,8938 "))
        assertEquals(-73.9857, parseCoordinate("-73.9857"))
    }

    @Test
    fun readsDigitsFromAnArabicKeyboard() {
        assertEquals(33.8938, parseCoordinate("٣٣٫٨٩٣٨"))
        assertEquals(35.5018, parseCoordinate("٣٥.٥٠١٨"))
        assertEquals(-6.2, parseCoordinate("−٦٫٢"))
        // Persian digits, as some keyboards type them.
        assertEquals(51.42, parseCoordinate("۵۱٫۴۲"))
    }

    @Test
    fun rejectsTextThatIsNotANumber() {
        assertNull(parseCoordinate(""))
        assertNull(parseCoordinate("north"))
        assertNull(parseCoordinate("33.8.9"))
    }
}

package app.spark.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonTest {
    @Test
    fun `objects, lists and the plain values are read`() {
        val value = Json.read("""{"a": [1, -2.5, 1e3], "b": {"c": true, "d": null, "e": false}, "f": "x"}""")
        assertEquals(mapOf("a" to listOf(1.0, -2.5, 1000.0), "b" to mapOf("c" to true, "d" to null, "e" to false), "f" to "x"), value)
    }

    @Test
    fun `escapes in a string are undone`() {
        assertEquals("a\"b\\c\néش", Json.read(""""a\"b\\c\néش""""))
        assertEquals("ش", Json.read("\"ش\""))
    }

    @Test
    fun `empty objects and lists are read`() {
        assertEquals(mapOf("a" to emptyList<Any?>(), "b" to emptyMap<String, Any?>()), Json.read("""{ "a": [ ], "b": { } }"""))
    }

    @Test
    fun `text that isn't JSON is refused`() {
        for (text in listOf("", "{", """{"a" 1}""", """{"a": 1,}""", "[1 2]", "\"open", "tru", "{} x", "\"a\nb\"", """"\x"""", "-", "[" .repeat(100))) {
            assertFailsWith<Json.Malformed>(text) { Json.read(text) }
        }
    }
}

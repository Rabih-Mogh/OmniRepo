package app.spark.core

/**
 * Reads JSON into plain Kotlin values: a Map for an object, a List for an array, and
 * String, Double, Boolean or null for the rest. It is small on purpose. The packs are the
 * only JSON the app is sent, and one reader means the phone and the pack checker agree on
 * what a pack says.
 */
object Json {

    class Malformed(message: String) : Exception(message)

    fun read(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value(0)
        reader.blank()
        if (reader.at < text.length) reader.fail("something follows the value")
        return value
    }

    private class Reader(private val text: String) {
        var at = 0

        fun fail(what: String): Nothing = throw Malformed("$what at character $at")

        fun blank() {
            while (at < text.length && text[at] in " \t\r\n") at++
        }

        fun value(depth: Int): Any? {
            // A pack is a few levels deep. Anything far deeper isn't one, and would only use up the stack.
            if (depth > 32) fail("nested too deeply")
            blank()
            if (at >= text.length) fail("the text ends early")
            return when (val c = text[at]) {
                '{' -> obj(depth)
                '[' -> list(depth)
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c in '0'..'9') number() else fail("unexpected '$c'")
            }
        }

        private fun obj(depth: Int): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            at++
            blank()
            if (peek() == '}') {
                at++
                return map
            }
            while (true) {
                blank()
                if (peek() != '"') fail("a name was expected")
                val name = string()
                blank()
                if (peek() != ':') fail("':' was expected")
                at++
                map[name] = value(depth + 1)
                blank()
                when (peek()) {
                    ',' -> at++
                    '}' -> {
                        at++
                        return map
                    }
                    else -> fail("',' or '}' was expected")
                }
            }
        }

        private fun list(depth: Int): List<Any?> {
            val list = ArrayList<Any?>()
            at++
            blank()
            if (peek() == ']') {
                at++
                return list
            }
            while (true) {
                list += value(depth + 1)
                blank()
                when (peek()) {
                    ',' -> at++
                    ']' -> {
                        at++
                        return list
                    }
                    else -> fail("',' or ']' was expected")
                }
            }
        }

        private fun string(): String {
            val out = StringBuilder()
            at++
            while (true) {
                if (at >= text.length) fail("a string isn't closed")
                when (val c = text[at++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (at >= text.length) fail("a string isn't closed")
                        when (val e = text[at++]) {
                            '"', '\\', '/' -> out.append(e)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                val code = text.substring(at, minOf(at + 4, text.length)).takeIf { it.length == 4 }?.toIntOrNull(16)
                                    ?: fail("a \\u escape needs four hex digits")
                                out.append(code.toChar())
                                at += 4
                            }
                            else -> fail("unknown escape '\\$e'")
                        }
                    }
                    else -> if (c < ' ') fail("a line break or control character inside a string") else out.append(c)
                }
            }
        }

        private fun number(): Double {
            val start = at
            if (peek() == '-') at++
            while (at < text.length && (text[at] in '0'..'9' || text[at] in ".eE+-")) at++
            return text.substring(start, at).toDoubleOrNull() ?: fail("not a number")
        }

        private fun word(word: String, value: Any?): Any? {
            if (!text.startsWith(word, at)) fail("unexpected '${text[at]}'")
            at += word.length
            return value
        }

        private fun peek(): Char = if (at < text.length) text[at] else fail("the text ends early")
    }
}

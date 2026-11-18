// JSON as the composed tree and its data model hold it: null, Boolean, Long or Double, String, List and Map (in the
// order it was written). Small and dependency-free, so the port runs in a plain JVM test and in any app; it reads and
// writes numbers as JavaScript does, so a tree or a press reads the same on every platform.
package bot.ouro.ui.hx.composed

/** Reads and writes JSON values: null, [Boolean], [Long] or [Double], [String], [List] and [Map]. */
object ComposedJson {
    /** The value [text] holds. Throws [IllegalArgumentException] when it isn't JSON. */
    fun parse(text: String): Any? {
        val reader = Reader(text)
        reader.space()
        val value = reader.value()
        reader.space()
        require(reader.at == text.length) { "not JSON: more after the value at ${reader.at}" }
        return value
    }

    /** The value as JSON text, as JavaScript's `JSON.stringify` writes it: no spaces, keys in their order. */
    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    /** A number as JavaScript writes it: 4 for 4.0, 0.5, 1e-7. */
    fun number(n: Number): String = when (n) {
        is Double, is Float -> {
            val d = n.toDouble()
            when {
                d.isNaN() || d.isInfinite() -> "null"
                d == Math.rint(d) && kotlin.math.abs(d) < 1e21 -> d.toLong().toString()
                else -> d.toString().replace("E", "e").replace(".0e", "e").replace(Regex("e(\\d)"), "e+$1")
            }
        }
        else -> n.toLong().toString()
    }

    private fun write(out: StringBuilder, v: Any?) {
        when (v) {
            null -> out.append("null")
            is Boolean -> out.append(v)
            is Number -> out.append(number(v))
            is String -> quote(out, v)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) out.append(',')
                    first = false
                    quote(out, k.toString())
                    out.append(':')
                    write(out, x)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                v.forEachIndexed { i, x ->
                    if (i > 0) out.append(',')
                    write(out, x)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("not a JSON value: ${v::class.simpleName}")
        }
    }

    private fun quote(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
        }
        out.append('"')
    }

    private class Reader(val s: String) {
        var at = 0

        fun space() {
            while (at < s.length && s[at] in " \t\n\r") at++
        }

        fun fail(what: String): Nothing = throw IllegalArgumentException("not JSON: $what at $at")

        fun value(): Any? {
            if (at >= s.length) fail("the text ended")
            return when (val c = s[at]) {
                '{' -> obj()
                '[' -> list()
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c in '0'..'9') number() else fail("unexpected '$c'")
            }
        }

        private fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, at)) fail("expected $w")
            at += w.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            at++
            space()
            if (at < s.length && s[at] == '}') {
                at++
                return out
            }
            while (true) {
                space()
                if (at >= s.length || s[at] != '"') fail("expected a key")
                val k = string()
                space()
                if (at >= s.length || s[at] != ':') fail("expected ':'")
                at++
                space()
                out[k] = value()
                space()
                if (at >= s.length) fail("the object didn't end")
                when (s[at]) {
                    ',' -> at++
                    '}' -> {
                        at++
                        return out
                    }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun list(): List<Any?> {
            val out = ArrayList<Any?>()
            at++
            space()
            if (at < s.length && s[at] == ']') {
                at++
                return out
            }
            while (true) {
                space()
                out += value()
                space()
                if (at >= s.length) fail("the list didn't end")
                when (s[at]) {
                    ',' -> at++
                    ']' -> {
                        at++
                        return out
                    }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        private fun string(): String {
            val out = StringBuilder()
            at++
            while (true) {
                if (at >= s.length) fail("the string didn't end")
                val c = s[at++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (at >= s.length) fail("the string didn't end")
                        when (val e = s[at++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (at + 4 > s.length) fail("a short \\u escape")
                                val code = s.substring(at, at + 4).toIntOrNull(16) ?: fail("a bad \\u escape")
                                out.append(code.toChar())
                                at += 4
                            }
                            else -> fail("a bad escape \\$e")
                        }
                    }
                    else -> if (c < ' ') fail("a control character in a string") else out.append(c)
                }
            }
        }

        private fun number(): Any {
            val start = at
            if (s[at] == '-') at++
            while (at < s.length && s[at] in "0123456789.eE+-") at++
            val text = s.substring(start, at)
            val whole = text.none { it == '.' || it == 'e' || it == 'E' }
            return (if (whole) text.toLongOrNull() else null) ?: text.toDoubleOrNull() ?: fail("a bad number $text")
        }
    }
}

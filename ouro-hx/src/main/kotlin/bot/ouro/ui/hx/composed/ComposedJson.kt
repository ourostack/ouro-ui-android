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

    /**
     * A number as JavaScript writes it (ECMAScript's Number::toString): 4 for 4.0, 0.00015, 12345678.5,
     * 100000000000000000000, 1e+21 and 1e-7. JSON has no NaN or infinity, so they are null.
     */
    fun number(n: Number): String {
        if (n is Long || n is Int || n is Short || n is Byte) return n.toString()
        val d = n.toDouble()
        if (d.isNaN() || d.isInfinite()) return "null"
        if (d == 0.0) return "0"
        // The shortest digits that read back as d, and where the decimal point goes among them.
        val exact = java.math.BigDecimal(d.toString()).stripTrailingZeros()
        val digits = exact.unscaledValue().abs().toString()
        val k = digits.length
        val point = k - exact.scale()
        val sign = if (d < 0) "-" else ""
        val text = when {
            point in k..21 -> digits + "0".repeat(point - k)
            point in 1..21 -> digits.substring(0, point) + "." + digits.substring(point)
            point in -5..0 -> "0." + "0".repeat(-point) + digits
            else -> {
                val e = point - 1
                (if (k == 1) digits else digits[0] + "." + digits.substring(1)) + "e" + (if (e >= 0) "+" else "-") + kotlin.math.abs(e)
            }
        }
        return sign + text
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

    /** How deep JSON may nest before it is refused: past any tree or data model, short of the stack. */
    const val MAX_DEPTH = 512

    private class Reader(val s: String) {
        var at = 0
        private var depth = 0

        private fun deeper() {
            if (++depth > MAX_DEPTH) fail("nested more than $MAX_DEPTH deep")
        }

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
            deeper()
            at++
            space()
            if (at < s.length && s[at] == '}') {
                at++
                depth--
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
                        depth--
                        return out
                    }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun list(): List<Any?> {
            val out = ArrayList<Any?>()
            deeper()
            at++
            space()
            if (at < s.length && s[at] == ']') {
                at++
                depth--
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
                        depth--
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

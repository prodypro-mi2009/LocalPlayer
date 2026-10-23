package com.localplayer.music.data.backup

/**
 * Tiny JSON reader/writer for the settings backup (objects, arrays, strings, numbers, booleans, null).
 * Pure Kotlin, so backup parsing is unit-tested on the JVM and needs no extra dependency.
 * Parsed values are: Map<String, Any?>, List<Any?>, String, Double, Boolean or null. Depth and size are bounded.
 */
object MiniJson {
    class JsonException(message: String) : Exception(message)

    private const val MAX_DEPTH = 24

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value(0)
        p.skipWs()
        if (p.pos != text.length) throw JsonException("Unexpected data after the JSON value")
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: throw JsonException("The JSON root is not an object")

    fun stringify(value: Any?, pretty: Boolean = true): String = StringBuilder().also { write(it, value, pretty, 0) }.toString()

    private fun write(sb: StringBuilder, v: Any?, pretty: Boolean, indent: Int) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v.toString())
            is Float -> sb.append(numberText(v.toString().toDouble()))
            is Double -> sb.append(numberText(v))
            is String -> quote(sb, v)
            is Map<*, *> -> {
                if (v.isEmpty()) { sb.append("{}"); return }
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    if (pretty) sb.append('\n').append("  ".repeat(indent + 1))
                    quote(sb, k.toString()); sb.append(if (pretty) ": " else ":")
                    write(sb, value, pretty, indent + 1)
                }
                if (pretty) sb.append('\n').append("  ".repeat(indent))
                sb.append('}')
            }
            is List<*> -> {
                if (v.isEmpty()) { sb.append("[]"); return }
                sb.append('[')
                var first = true
                for (value in v) {
                    if (!first) sb.append(',')
                    first = false
                    if (pretty) sb.append('\n').append("  ".repeat(indent + 1))
                    write(sb, value, pretty, indent + 1)
                }
                if (pretty) sb.append('\n').append("  ".repeat(indent))
                sb.append(']')
            }
            else -> throw JsonException("Unsupported value type ${v::class.simpleName}")
        }
    }

    private fun numberText(d: Double): String {
        if (d.isNaN() || d.isInfinite()) throw JsonException("NaN/Infinity cannot be stored")
        return if (d == Math.rint(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var pos = 0
        fun skipWs() { while (pos < s.length && s[pos].isWhitespace()) pos++ }
        fun fail(msg: String): Nothing = throw JsonException("$msg at position $pos")

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("Nesting too deep")
            if (pos >= s.length) fail("Unexpected end")
            return when (val c = s[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else fail("Unexpected character '$c'")
            }
        }

        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) fail("Invalid literal")
            pos += word.length
            return v
        }

        fun num(): Double {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            return s.substring(start, pos).toDoubleOrNull()?.takeIf { it.isFinite() } ?: fail("Invalid number")
        }

        fun str(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) fail("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= s.length) fail("Bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> {
                                if (pos + 4 > s.length) fail("Bad unicode escape")
                                sb.append(s.substring(pos, pos + 4).toIntOrNull(16)?.toChar() ?: fail("Bad unicode escape"))
                                pos += 4
                            }
                            else -> fail("Bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun arr(depth: Int): List<Any?> {
            pos++
            val list = ArrayList<Any?>()
            skipWs()
            if (pos < s.length && s[pos] == ']') { pos++; return list }
            while (true) {
                skipWs(); list.add(value(depth + 1)); skipWs()
                if (pos >= s.length) fail("Unterminated array")
                when (s[pos++]) { ',' -> continue; ']' -> return list; else -> fail("Expected , or ]") }
            }
        }

        fun obj(depth: Int): Map<String, Any?> {
            pos++
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (pos < s.length && s[pos] == '}') { pos++; return map }
            while (true) {
                skipWs()
                if (pos >= s.length || s[pos] != '"') fail("Expected a key")
                val key = str()
                skipWs()
                if (pos >= s.length || s[pos++] != ':') fail("Expected :")
                skipWs()
                map[key] = value(depth + 1)
                skipWs()
                if (pos >= s.length) fail("Unterminated object")
                when (s[pos++]) { ',' -> continue; '}' -> return map; else -> fail("Expected , or }") }
            }
        }
    }
}

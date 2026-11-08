package com.davidlang.vehicleexpensesautomated.data.torque

import java.security.MessageDigest
import java.text.Normalizer

/**
 * Canonical JSON for the fill bridge.
 * UTF-8, no whitespace, ASCII-sorted object keys, NFC strings.
 */
object TorqueFillJson {
    fun canonical(value: Any?): String = write(value)

    fun sha256(canonical: String): String {
        val dig = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return dig.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }

    fun parse(text: String): Any? = Parser(text).parse()

    private fun write(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> value.toString()
        is Int -> value.toLong().toString()
        is Long -> value.toString()
        is String -> quote(Normalizer.normalize(value, Normalizer.Form.NFC))
        is Map<*, *> -> {
            val keys = value.keys.map { it as String }.sorted()
            keys.joinToString(",", "{", "}") { key -> quote(key) + ":" + write(value[key]) }
        }
        is List<*> -> value.joinToString(",", "[", "]") { write(it) }
        else -> error("not a bridge value")
    }

    private fun quote(text: String): String = buildString {
        append('"')
        for (c in text) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\u000c' -> append("\\f")
            '\r' -> append("\\r")
            else -> if (c < ' ') append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
        }
        append('"')
    }

    private class Parser(private val text: String) {
        private var at = 0

        fun parse(): Any? {
            val value = value()
            skip()
            if (at != text.length) throw IllegalArgumentException("trailing")
            return value
        }

        private fun skip() {
            while (at < text.length && text[at] in " \t\r\n") at++
        }

        private fun value(): Any? {
            skip()
            if (at >= text.length) throw IllegalArgumentException("end")
            return when (text[at]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        private fun objectValue(): Map<String, Any?> {
            at++
            val out = LinkedHashMap<String, Any?>()
            skip()
            if (peek('}')) return out
            while (true) {
                skip()
                val key = string()
                if (out.containsKey(key)) throw IllegalArgumentException("duplicate")
                skip()
                if (!peek(':')) throw IllegalArgumentException("colon")
                out[key] = value()
                skip()
                when {
                    peek(',') -> continue
                    peek('}') -> return out
                    else -> throw IllegalArgumentException("object")
                }
            }
        }

        private fun arrayValue(): List<Any?> {
            at++
            val out = ArrayList<Any?>()
            skip()
            if (peek(']')) return out
            while (true) {
                out.add(value())
                skip()
                when {
                    peek(',') -> continue
                    peek(']') -> return out
                    else -> throw IllegalArgumentException("array")
                }
            }
        }

        private fun string(): String {
            if (!peek('"')) throw IllegalArgumentException("string")
            val out = StringBuilder()
            while (at < text.length) {
                val c = text[at++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (at >= text.length) throw IllegalArgumentException("escape")
                        out.append(
                            when (val e = text[at++]) {
                                '"', '\\', '/' -> e
                                'b' -> '\b'
                                't' -> '\t'
                                'n' -> '\n'
                                'f' -> '\u000c'
                                'r' -> '\r'
                                'u' -> {
                                    if (at + 4 > text.length) throw IllegalArgumentException("u")
                                    val hex = text.substring(at, at + 4)
                                    at += 4
                                    hex.toInt(16).toChar()
                                }
                                else -> throw IllegalArgumentException("escape")
                            },
                        )
                    }
                    else -> if (c < ' ') throw IllegalArgumentException("control") else out.append(c)
                }
            }
            throw IllegalArgumentException("string")
        }

        private fun number(): Long {
            val start = at
            if (peek('-')) throw IllegalArgumentException("sign")
            if (at >= text.length || text[at] !in '0'..'9') throw IllegalArgumentException("number")
            if (text[at] == '0') at++ else while (at < text.length && text[at] in '0'..'9') at++
            if (at < text.length && text[at] in ".eE") throw IllegalArgumentException("fraction")
            return text.substring(start, at).toLong()
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, at)) throw IllegalArgumentException("literal")
            at += word.length
            return value
        }

        private fun peek(c: Char): Boolean {
            if (at < text.length && text[at] == c) {
                at++
                return true
            }
            return false
        }
    }
}

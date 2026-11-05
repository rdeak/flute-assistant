// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.core.log

import java.util.Locale

/**
 * One structured log line: a dotted event name followed by `key=value` fields in the order
 * given, e.g. `tuner.frame f=440.12 p=0.80 level=0.40`. Floating-point values get two
 * decimals, null is `none`, enums their name, and text containing whitespace is quoted, so a
 * line can always be split on spaces by a script. Pure Kotlin, unit-tested on the JVM.
 */
object LogLine {
    fun format(event: String, vararg fields: Pair<String, Any?>): String {
        if (fields.isEmpty()) return event
        val line = StringBuilder(event)
        for ((key, value) in fields) line.append(' ').append(key).append('=').append(render(value))
        return line.toString()
    }

    /**
     * The reverse of [format]: the event name and its fields as text, quotes removed, `none` kept
     * as the text `none`. What the replay of a recorded session reads `session.log` with.
     */
    fun parse(line: String): Pair<String, Map<String, String>> {
        val tokens = tokens(line)
        val fields = LinkedHashMap<String, String>()
        for (token in tokens.drop(1)) {
            val at = token.indexOf('=')
            if (at > 0) fields[token.substring(0, at)] = token.substring(at + 1)
        }
        return tokens.firstOrNull().orEmpty() to fields
    }

    /** Splits on spaces outside quotes, dropping the quotes. */
    private fun tokens(line: String): List<String> {
        val out = mutableListOf<String>()
        val token = StringBuilder()
        var quoted = false
        for (c in line) {
            when {
                c == '"' -> quoted = !quoted
                c == ' ' && !quoted -> if (token.isNotEmpty()) {
                    out += token.toString()
                    token.clear()
                }
                else -> token.append(c)
            }
        }
        if (token.isNotEmpty()) out += token.toString()
        return out
    }

    private fun render(value: Any?): String = when (value) {
        null -> "none"
        is Double -> String.format(Locale.ROOT, "%.2f", value)
        is Float -> String.format(Locale.ROOT, "%.2f", value)
        is Enum<*> -> value.name
        is String -> if (value.any { it.isWhitespace() }) "\"$value\"" else value
        else -> value.toString()
    }
}

package io.ktor.experimental.client.db


// https://dev.mysql.com/doc/refman/5.7/en/string-literals.html#character-escape-sequences
fun escape(value: String): String = buildString {
    value.forEach {
        when (it) {
            '\u0000' -> append("\\0")
            '\'' -> append("\\'")
            '\"' -> append("\\\"")
            '\b' -> append("\\b")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\u0026' -> append("\\Z")
            '\\' -> append("\\\\")
            '_' -> append("\\_")
            '`' -> append("\\`")
            else -> append(it)
        }
    }
}

fun quoteString(str: String): String = "'${escape(str)}'"

fun quoteTable(str: String): String = "\"${escape(str)}\""

fun quoteColumn(str: String): String = "\"${escape(str)}\""

fun quoteConstant(value: Any?): String = when (value) {
    null -> "NULL"
    is Int, is Long, is Float, is Double -> "$value"
    else -> quoteString(value.toString())
}
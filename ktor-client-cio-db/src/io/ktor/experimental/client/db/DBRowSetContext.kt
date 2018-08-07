package io.ktor.experimental.client.db

import java.nio.charset.*
import java.text.*
import java.util.*

data class DBRowSetContext(
    val charset: Charset = Charsets.UTF_8,
    val timeFormat: SimpleDateFormat = SimpleDateFormat("HH:mm:ss"),
    val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd"),
    val datetimeFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
) {
    private val formats = listOf(timeFormat, dateFormat, datetimeFormat)

    companion object {
        val DEFAULT = DBRowSetContext()
    }

    fun date(str: String): Date {
        for (format in formats) {
            try {
                return format.parse(str)
            } catch (e: ParseException) {
            }
        }
        throw ParseException("Couldn't format $str as date", -1)
    }
}
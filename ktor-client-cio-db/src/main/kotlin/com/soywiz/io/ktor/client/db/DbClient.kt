package com.soywiz.io.ktor.client.db

import com.soywiz.io.ktor.client.util.*
import java.nio.charset.*
import java.text.*
import java.time.*
import java.util.*

interface DbClient : AsyncCloseable, WithProperties {
    suspend fun query(query: String): DbRowSet

    suspend fun transactionStart(): Unit = run { query("START TRANSACTION;") }
    suspend fun transactionRollback(): Unit = run { query("ROLLBACK TRANSACTION;") }
    suspend fun transactionCommit(): Unit = run { query("COMMIT TRANSACTION;") }

    suspend fun transaction(callback: suspend DbClient.(DbClient) -> Unit) {
        transactionStart()
        try {
            callback(this)
        } catch (e: Throwable) {
            transactionRollback()
            throw e
        }
        transactionCommit()
    }

    // https://dev.mysql.com/doc/refman/5.7/en/string-literals.html#character-escape-sequences
    fun escape(str: String): String {
        var out = ""
        for (c in str) {
            when (c) {
                '\u0000' -> out += "\\0"
                '\'' -> out += "\\'"
                '\"' -> out += "\\\""
                '\b' -> out += "\\b"
                '\n' -> out += "\\n"
                '\r' -> out += "\\r"
                '\t' -> out += "\\t"
                '\u0026' -> out += "\\Z"
                '\\' -> out += "\\\\"
                //'%' -> out += "\\%"
                '_' -> out += "\\_"
                '`' -> out += "\\`"
                else -> out += c
            }
        }
        return out
    }

    fun quoteString(str: String): String = "'${escape(str)}'"
    fun quoteTable(str: String): String = "\"${escape(str)}\""
    fun quoteColumn(str: String): String = "\"${escape(str)}\""

    fun quoteConstant(value: Any?): String = value?.let {
        when (it) {
            is Int, is Long, is Float, is Double -> "$it"
            else -> quoteString(it.toString())
        }
    } ?: "NULL"
}

data class DbRowSetContext(
    val charset: Charset = Charsets.UTF_8,
    val timeFormat: SimpleDateFormat = SimpleDateFormat("HH:mm:ss"),
    val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd"),
    val datetimeFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
) {
    private val formats = listOf(timeFormat, dateFormat, datetimeFormat)

    companion object {
        val DEFAULT = DbRowSetContext()
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

data class DbColumns(val columns: List<DbColumn>, val context: DbRowSetContext = DbRowSetContext.DEFAULT) :
    Iterable<DbColumn> by columns {
    val columnsByName = columns.associateBy { it.name }
    operator fun get(name: String) = columnsByName[name]
    operator fun get(index: Int) = columns.getOrNull(index)
}

interface DbColumn {
    val index: Int
    val name: String
}

class DbRow(val columns: DbColumns, val cells: List<ByteArray?>) : List<Any?> {
    val context get() = columns.context

    fun bytes(key: Int) = cells.getOrNull(key)
    fun bytes(key: String) = columns[key]?.let { bytes(it.index) }

    fun string(key: Int) = bytes(key)?.toString(context.charset)
    fun string(key: String) = bytes(key)?.toString(context.charset)

    fun int(key: Int) = string(key)?.toInt()
    fun int(key: String) = string(key)?.toInt()

    fun date(key: Int) = string(key)?.let { context.date(it) }
    fun date(key: String) = string(key)?.let { context.date(it) }

    operator fun get(key: String) = bytes(key)

    val pairs get() = columns.columns.map { it.name }.zip(typedList)
    override fun toString(): String = "DbRow($pairs)"

    //////////////////////////////////////////////////////////////

    val typedList by lazy {
        (0 until size).map { string(it) } // Proper type
    }

    override val size: Int get() = cells.size
    override fun contains(element: Any?): Boolean = typedList.contains(element)
    override fun containsAll(elements: Collection<Any?>): Boolean = typedList.containsAll(elements)
    override fun get(index: Int): Any? = typedList.get(index)
    override fun indexOf(element: Any?): Int = typedList.indexOf(element)
    override fun isEmpty(): Boolean = typedList.isEmpty()
    override fun iterator(): Iterator<Any?> = typedList.iterator()
    override fun lastIndexOf(element: Any?): Int = typedList.lastIndexOf(element)
    override fun listIterator(): ListIterator<Any?> = typedList.listIterator()
    override fun listIterator(index: Int): ListIterator<Any?> = typedList.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): List<Any?> = typedList.subList(fromIndex, toIndex)
}

data class DbRowSetInfo(
    val query: String? = null,
    val duration: Duration? = null
)

class DbRowSet(
    val columns: DbColumns,
    val rows: SuspendingSequence<DbRow>,
    val info: DbRowSetInfo
) : SuspendingSequence<DbRow> by rows

fun DbClient.withInfoHook(handler: DbQueryInfo.() -> Unit): DbClientWithStats = DbClientWithStats(this, handler)

data class DbQueryInfo(val query: String, val duration: Duration, val error: Throwable?) {
    var info: DbRowSetInfo? = null
}

class DbClientWithStats(val client: DbClient, val handler: (info: DbQueryInfo) -> Unit) : DbClient by client {
    override suspend fun query(query: String): DbRowSet {
        var error: Throwable? = null
        var info: DbRowSetInfo? = null
        val start = System.currentTimeMillis()
        try {
            val result = client.query(query)
            info = result.info
            return result
        } catch (e: Throwable) {
            error = e
            throw e
        } finally {
            val end = System.currentTimeMillis()
            handler(DbQueryInfo(query, Duration.ofMillis(end - start), error).apply {
                this.info = info
            })
        }
    }
}

/*

data class MysqlColumns(val columns: List<MysqlColumn> = listOf()) : Collection<MysqlColumn> by columns {
    val columnIndex = columns.withIndex().map { it.value.columnAlias to it.index }.toMap()
}

data class MysqlRow(val columns: MysqlColumns, val data: List<Any?>) : List<Any?> by data {
    fun raw(name: String): Any? = columns.columnIndex[name]?.let { data[it] }
    fun int(name: String): Int? = (raw(name) as? Number?)?.toInt()
    fun long(name: String): Long? = (raw(name) as? Number?)?.toLong()
    fun double(name: String): Double? = (raw(name) as? Number?)?.toDouble()
    fun string(name: String): String? = (raw(name))?.toString()
    fun byteArray(name: String): ByteArray? = raw(name) as? ByteArray?
    fun date(name: String): Date? = raw(name) as? Date?

    fun raw(index: Int): Any? = data.getOrNull(index)
    fun int(index: Int): Int? = (raw(index) as? Number?)?.toInt()
    fun long(index: Int): Long? = (raw(index) as? Number?)?.toLong()
    fun double(index: Int): Double? = (raw(index) as? Number?)?.toDouble()
    fun string(index: Int): String? = (raw(index))?.toString()
    fun byteArray(index: Int): ByteArray? = raw(index) as? ByteArray?
    fun date(index: Int): Date? = raw(index) as? Date?

    override fun toString(): String =
        "MysqlRow(" + columns.zip(data).map { "${it.first.columnAlias}=${it.second}" }.joinToString(", ") + ")"
}

typealias MysqlRows = SuspendingSequence<MysqlRow>

fun MysqlRows(columns: MysqlColumns, list: List<MysqlRow>): MysqlRows {
    return list.toSuspendingSequence()
}
 */

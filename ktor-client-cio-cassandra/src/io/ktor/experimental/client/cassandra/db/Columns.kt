package io.ktor.experimental.client.cassandra.db

data class Columns(val columns: List<Column>) : List<Column> by columns {
    fun contains(name: String): Boolean = columns.any { it.name == name }

    fun getIndex(name: String): Int {
        val index = columns.indexOfFirst { it.name == name }
        if (index < 0) throw IllegalArgumentException("Can't find column '$name'")
        return index
    }
}
package io.ktor.experimental.client.db

data class DbColumns(val columns: List<DbColumn>, val context: DbRowSetContext = DbRowSetContext.DEFAULT) :
    Iterable<DbColumn> by columns {
    val columnsByName = columns.associateBy { it.name }

    operator fun get(name: String) = columnsByName[name]
    operator fun get(index: Int) = columns.getOrNull(index)
}

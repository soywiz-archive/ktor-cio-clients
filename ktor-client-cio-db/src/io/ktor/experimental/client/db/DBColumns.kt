package io.ktor.experimental.client.db

class DBColumns(
    columns: List<DBColumn>,
    val context: DBRowSetContext = DBRowSetContext.DEFAULT
) : List<DBColumn> by columns {
    private val columnsByName = columns.associateBy { it.name }

    operator fun get(name: String): DBColumn? = columnsByName[name]
}

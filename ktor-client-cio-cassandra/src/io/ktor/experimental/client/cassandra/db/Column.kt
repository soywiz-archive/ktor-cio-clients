package io.ktor.experimental.client.cassandra.db

data class Column(
    val index: Int,
    val ksname: String,
    val tablename: String,
    val name: String,
    val type: ColumnType<*>
) {
    fun interpret(data: ByteArray): Any? = type.interpret(data)
}
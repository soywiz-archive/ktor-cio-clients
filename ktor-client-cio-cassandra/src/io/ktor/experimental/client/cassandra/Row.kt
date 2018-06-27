package io.ktor.experimental.client.cassandra

import io.ktor.experimental.client.cassandra.db.*

data class Row(
    val columns: Columns,
    val data: List<ByteArray>
) : Map<String, Any?> {
    data class MyEntry(override val key: String, override val value: Any?) : Map.Entry<String, Any?>

    override val size: Int = data.size
    override val keys: Set<String> by lazy { columns.columns.map { it.name }.toSet() }
    override val values: List<Any?> by lazy { (0 until size).map { this[it] } }
    override val entries: Set<Map.Entry<String, Any?>> by lazy {
        (0 until size).map {
            MyEntry(
                columns[it].name,
                values[it]
            )
        }.toSet()
    }

    override fun containsKey(key: String): Boolean = keys.contains(key)
    override fun containsValue(value: Any?): Boolean = values.contains(value)

    override fun isEmpty(): Boolean = size == 0

    fun getColumn(name: String) = columns[columns.getIndex(name)]
    fun getColumnIndex(name: String) = columns.getIndex(name)

    operator fun get(index: Int) = columns[index].interpret(data[index])
    fun getRaw(index: Int) = data[index]
    fun getString(index: Int) = getRaw(index).toString(Charsets.UTF_8)

    override operator fun get(key: String) = get(getColumnIndex(key))
    fun getRaw(key: String) = getRaw(getColumnIndex(key))
    fun getString(key: String) = getString(getColumnIndex(key))

    override fun toString(): String =
        columns.columns.map { "${it.name}=${it.interpret(data[it.index])}" }.joinToString(", ")
}
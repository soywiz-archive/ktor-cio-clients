package io.ktor.experimental.client.db.map

import io.ktor.experimental.client.db.*
import io.ktor.experimental.client.util.*
import kotlin.reflect.*

suspend fun <T : Any> DbClient.update(
    clazz: KClass<T>,
    item: T? = null,
    fields: List<KProperty1<T, *>>? = null,
    sets: List<Pair<KProperty1<T, *>, Any>>? = null,
    increments: List<Pair<KProperty1<T, *>, Any>>? = null,
    where: Where.Builder.() -> Where? = { null }
): Long {
    val info = tablesInfo.get(clazz)
    val columns by lazy { (fields?.let { info.getColumns(fields) } ?: info.columns).associateBy { it.name } }
    val map by lazy { item?.let { info.toMap(item, skipSerials = true) } ?: mapOf() }
    val allEntries by lazy { map.entries.toList() }
    val filteredEntries by lazy { allEntries.filter { columns.containsKey(it.key) } }

    val setParts = arrayListOf<String>()

    if (item != null) {
        for (entry in filteredEntries) {
            val qname = quoteColumn(entry.key)
            setParts += "$qname = ${quoteConstant(entry.value)}"
        }
    }

    if (increments != null) {
        for (increment in increments) {
            val qname = info.getColumn(increment.first).quotedName
            setParts += "$qname = $qname + ${quoteConstant(increment.second)}"
        }
    }

    if (sets != null) {
        for (set in sets) {
            val qname = info.getColumn(set.first).quotedName
            setParts += "$qname = ${quoteConstant(set.second)}"
        }
    }

    var query = "UPDATE ${info.quotedTableName} SET ${setParts.joinToString(", ")}"

    val whr = where(Where.Builder)
    if (whr == null) {
        val column = info.primaryColumn ?: info.uniqueColumn ?: error("UPDATE without where without primary key")
        query += " WHERE ${column.quotedName} = ${quoteConstant(column.get(item!!))}"
    } else {
        query += " WHERE ${whr.render(this)}"
    }

    val results = query("$query;").toList()
    return results.firstOrNull()?.firstOrNull().convertTo(Long::class) ?: 0L
}

suspend inline fun <reified T : Any> DbClient.update(
    item: T? = null,
    fields: List<KProperty1<T, *>>? = null,
    sets: List<Pair<KProperty1<T, *>, Any>>? = null,
    increments: List<Pair<KProperty1<T, *>, Any>>? = null,
    noinline where: Where.Builder.() -> Where? = { null }
) = update(T::class, item, fields = fields, sets = sets, increments = increments, where = where)

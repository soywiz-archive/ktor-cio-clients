package io.ktor.experimental.client.db.map

import io.ktor.experimental.client.db.*
import io.ktor.experimental.client.util.*
import kotlin.reflect.*


suspend fun <T : Any> DBClient.delete(
    clazz: KClass<T>,
    item: T? = null,
    where: Where.Builder.() -> Where? = { null }
): Long {
    val db = this
    val info = tablesInfo.get(clazz)
    var query = "DELETE FROM ${info.quotedTableName}"

    val whr = where(Where.Builder)
    when {
        whr != null && item != null -> error("Use item or where, not both")
        whr == null && item == null -> error("You must specify item or where")
        whr != null -> query += " WHERE ${whr.render(db)}"
        item != null -> query += " WHERE " + Where.AND(info.columns.map {
            Where.BINARY(
                info.klazz,
                it.prop,
                "=",
                it.get(item)
            )
        }).render(db)
    }

    val results = query("$query;").toList()
    return results.firstOrNull()?.firstOrNull().convertTo(Long::class) ?: 0L
}

suspend inline fun <reified T : Any> DBClient.delete(
    item: T? = null,
    noinline where: Where.Builder.() -> Where? = { null }
) = delete(T::class, item, where = where)

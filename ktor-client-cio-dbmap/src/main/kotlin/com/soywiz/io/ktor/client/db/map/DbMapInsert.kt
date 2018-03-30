package com.soywiz.io.ktor.client.db.map

import com.soywiz.io.ktor.client.db.*
import com.soywiz.io.ktor.client.util.*
import kotlin.reflect.*

suspend fun <T : Any> DbClient.insert(clazz: KClass<T>, item: T): Long {
    val info = tablesInfo.get(clazz)
    val map = info.toMap(item, skipSerials = true)
    val keys = map.keys
    val values = map.values

    val keysPart = keys.map { quoteColumn(it) }.joinToString(", ")
    val valuesPart = values.map { quoteString("$it") }.joinToString(", ")
    val serialColumn = info.columns.firstOrNull { it.serial }

    var query = "INSERT INTO ${info.quotedTableName} ($keysPart) VALUES ($valuesPart)"

    if (serialColumn != null) {
        query += " RETURNING ${serialColumn.quotedName}"
    }

    val results = query("$query;").toList()
    return results.firstOrNull()?.firstOrNull().convertTo(Long::class) ?: 0L
}

suspend inline fun <reified T : Any> DbClient.insert(item: T) = insert(T::class, item)

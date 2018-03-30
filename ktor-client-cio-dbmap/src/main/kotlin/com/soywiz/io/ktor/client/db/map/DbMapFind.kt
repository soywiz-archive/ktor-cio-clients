package com.soywiz.io.ktor.client.db.map

import com.soywiz.io.ktor.client.db.*
import com.soywiz.io.ktor.client.util.*
import kotlin.reflect.*

suspend fun <T : Any> DbClient.find(
    clazz: KClass<T>,
    limit: Int? = null,
    orderBy: OrderSpec<T>? = null,
    fields: List<KProperty1<T, *>>? = null,
    where: Where.Builder.() -> Where = { Where.ALWAYS }
): SuspendingSequence<T> {
    val info = tablesInfo.get(clazz)
    val fieldNames = if (fields != null) {
        fields.map { info.getColumn(it).quotedName }.joinToString(", ")
    } else {
        "*"
    }
    var query = "SELECT $fieldNames FROM ${quoteTable(info.tableName)}"
    val where = where(Where.Builder).render(this)
    if (where.isNotEmpty()) query += " WHERE $where"
    if (orderBy != null) query += " ORDER BY ${orderBy.toString(info)}"
    if (limit != null) query += " LIMIT $limit"
    return query("$query;").map { info.toItem(it) }
}

suspend inline fun <reified T : Any> DbClient.find(
    limit: Int? = null,
    orderBy: OrderSpec<T>? = null,
    fields: List<KProperty1<T, *>>? = null,
    noinline where: Where.Builder.() -> Where = { Where.ALWAYS }
): SuspendingSequence<T> = find(T::class, limit = limit, orderBy = orderBy, fields = fields, where = where)

suspend inline fun <reified T : Any> DbClient.first(
    orderBy: OrderSpec<T>? = null,
    fields: List<KProperty1<T, *>>? = null,
    noinline where: Where.Builder.() -> Where = { Where.ALWAYS }
): T? = find(T::class, limit = 1, orderBy = orderBy, fields = fields, where = where).firstOrNull()


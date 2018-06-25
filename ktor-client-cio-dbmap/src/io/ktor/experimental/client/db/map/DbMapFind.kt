package io.ktor.experimental.client.db.map

import io.ktor.experimental.client.db.*
import io.ktor.experimental.client.util.*
import kotlin.reflect.*

@PublishedApi
internal fun <T : Any> buildFindQuery(
    db: DBClient,
    clazz: KClass<T>,
    limit: Int? = null,
    orderBy: OrderSpec<T>? = null,
    fields: List<KProperty1<T, *>>? = null,
    count: Boolean = false,
    where: Where.Builder.() -> Where = { Where.ALWAYS }
): String {
    val info = db.tablesInfo.get(clazz)
    val fieldNames = when {
        fields != null -> fields.map { info.getColumn(it).quotedName }.joinToString(", ")
        count -> "COUNT(*) AS count"
        else -> "*"
    }
    var query = "SELECT $fieldNames FROM ${info.quotedTableName}"
    val whr = where(Where.Builder).render(db)
    if (whr.isNotEmpty()) query += " WHERE $whr"
    if (orderBy != null) query += " ORDER BY ${orderBy.toString(info)}"
    if (limit != null) query += " LIMIT $limit"
    return query
}

suspend fun <T : Any> DBClient.find(
    clazz: KClass<T>,
    limit: Int? = null,
    orderBy: OrderSpec<T>? = null,
    fields: List<KProperty1<T, *>>? = null,
    count: Boolean = false,
    where: Where.Builder.() -> Where = { Where.ALWAYS }
): SuspendingSequence<T> {
    val info = tablesInfo.get(clazz)
    val query = buildFindQuery(this, clazz, limit, orderBy, fields, count, where)
    return query("$query;").map { info.toItem(it) }
}

suspend inline fun <reified T : Any> DBClient.find(
    limit: Int? = null,
    orderBy: OrderSpec<T>? = null,
    fields: List<KProperty1<T, *>>? = null,
    noinline where: Where.Builder.() -> Where = { Where.ALWAYS }
): SuspendingSequence<T> = find(T::class, limit = limit, orderBy = orderBy, fields = fields, where = where)

suspend inline fun <reified T : Any> DBClient.count(
    limit: Int? = null,
    noinline where: Where.Builder.() -> Where = { Where.ALWAYS }
): Long {
    return query(
        buildFindQuery(
            this,
            T::class,
            count = true,
            where = where
        )
    ).first().first()?.toString()?.toLongOrNull() ?: 0L
}

suspend inline fun <reified T : Any> DBClient.first(
    orderBy: OrderSpec<T>? = null,
    fields: List<KProperty1<T, *>>? = null,
    noinline where: Where.Builder.() -> Where = { Where.ALWAYS }
): T? = find(T::class, limit = 1, orderBy = orderBy, fields = fields, where = where).firstOrNull()


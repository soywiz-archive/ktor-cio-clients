package com.soywiz.io.ktor.client.db.map

import com.soywiz.io.ktor.client.db.*
import kotlin.reflect.*

suspend inline fun <reified T> DbClient.createTable() = createTable(T::class)

suspend fun DbClient.createTable(clazz: KClass<*>) {
    val info = tablesInfo.get(clazz)
    val columnParts = arrayListOf<String>()
    for (column in info.columns) {
        var columnPart = "${quoteColumn(column.name)} ${column.sqlType}"
        if (column.primaryKey) columnPart += " PRIMARY KEY"
        if (column.unique) columnPart += " UNIQUE"
        if (!column.nullable) columnPart += " NOT NULL"
        columnParts += columnPart
    }
    val query = "CREATE TABLE IF NOT EXISTS ${quoteTable(info.tableName)} (${columnParts.joinToString(", ")})"
    query(query)
}

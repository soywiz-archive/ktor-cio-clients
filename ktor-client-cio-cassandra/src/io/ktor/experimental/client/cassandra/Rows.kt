package io.ktor.experimental.client.cassandra

import io.ktor.experimental.client.cassandra.db.*

data class Rows(
    val columns: Columns,
    val rows: List<Row>
) : Collection<Row> by rows {
    operator fun get(index: Int) = rows[index]
}
package io.ktor.experimental.client.db

import io.ktor.experimental.client.util.*

class DbRowSet(
    val columns: DbColumns,
    val rows: SuspendingSequence<DbRow>,
    val info: DbRowSetInfo
) : SuspendingSequence<DbRow> by rows

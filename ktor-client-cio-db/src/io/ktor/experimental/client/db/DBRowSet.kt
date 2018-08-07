package io.ktor.experimental.client.db

import io.ktor.experimental.client.util.*

class DBRowSet(
    val columns: DBColumns,
    rows: SuspendingSequence<DBRow>,
    val info: DbRowSetInfo
) : SuspendingSequence<DBRow> by rows

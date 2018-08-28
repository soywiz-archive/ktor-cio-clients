package io.ktor.experimental.client.db

import io.ktor.experimental.client.util.*

class DBResponseRaws(
    val columns: DBColumns,
    val rows: List<DBRow>,
    val info: DbRowSetInfo
)

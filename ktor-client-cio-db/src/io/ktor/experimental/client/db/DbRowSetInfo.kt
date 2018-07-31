package io.ktor.experimental.client.db

import java.time.*

data class DbRowSetInfo(
    val query: String? = null,
    val duration: Duration? = null
)
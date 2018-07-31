package io.ktor.experimental.client.db

import java.time.*

class DBClientWithStats(
    val client: DBClient,
    val handler: (info: DbQueryInfo) -> Unit
) : DBClient by client {

    override suspend fun query(query: String): DbRowSet {
        var error: Throwable? = null
        var info: DbRowSetInfo? = null
        val start = System.currentTimeMillis()
        try {
            val result = client.query(query)
            info = result.info
            return result
        } catch (e: Throwable) {
            error = e
            throw e
        } finally {
            val end = System.currentTimeMillis()
            handler(DbQueryInfo(query, Duration.ofMillis(end - start), error).apply {
                this.info = info
            })
        }
    }

}
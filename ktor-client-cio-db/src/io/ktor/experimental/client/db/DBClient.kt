package io.ktor.experimental.client.db

import io.ktor.experimental.client.util.*
import kotlinx.coroutines.experimental.*
import java.io.*
import java.time.*

interface DBClient : WithProperties, Closeable {
    /**
     * Execution context. Use it to cancel tasks or force shutdown
     */
    val context: Job

    suspend fun query(query: String): DBResponse

    suspend fun transactionStart(): Unit = run { query("START TRANSACTION;") }
    suspend fun transactionRollback(): Unit = run { query("ROLLBACK TRANSACTION;") }
    suspend fun transactionCommit(): Unit = run { query("COMMIT TRANSACTION;") }

    suspend fun transaction(callback: suspend DBClient.(DBClient) -> Unit) {
        transactionStart()
        try {
            callback(this)
        } catch (e: Throwable) {
            transactionRollback()
            throw e
        }
        transactionCommit()
    }

}

fun DBClient.withInfoHook(handler: DbQueryInfo.() -> Unit): DBClientWithStats =
    DBClientWithStats(this, handler)

data class DbQueryInfo(val query: String, val duration: Duration, val error: Throwable?) {
    var info: DbRowSetInfo? = null
}

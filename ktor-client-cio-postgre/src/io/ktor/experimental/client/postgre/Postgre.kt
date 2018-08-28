package io.ktor.experimental.client.postgre

import io.ktor.experimental.client.postgre.scheme.*
import kotlinx.coroutines.experimental.*
import kotlinx.io.core.*
import java.net.*

// https://www.postgresql.org/docs/11/static/index.html

class PostgreClient(
    address: InetSocketAddress,
    val database: String = "default",
    val user: String = "root",
    password: String? = null
) : Closeable {
    val context: Job = CompletableDeferred<Unit>()

    /**
     * TBD: use multiple pipelines
     */
    private val connection: PostgreConnection = PostgreConnection(
        address, user, password, database, context
    )

    suspend fun query(queryString: String): PostgreRawResponse = connection.query(queryString)

    override fun close() {
        (context as CompletableDeferred<Unit>).complete(Unit)
        connection.close()
    }
}

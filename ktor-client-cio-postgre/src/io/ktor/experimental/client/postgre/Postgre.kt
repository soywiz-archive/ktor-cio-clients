package io.ktor.experimental.client.postgre

import io.ktor.experimental.client.db.*
import io.ktor.experimental.client.util.*
import kotlinx.coroutines.experimental.*


// https://www.postgresql.org/docs/9.3/static/protocol.html
// https://www.postgresql.org/docs/9.3/static/protocol-message-formats.html
// https://www.postgresql.org/docs/9.3/static/protocol-flow.html
// https://www.postgresql.org/docs/9.2/static/datatype-oid.html

class PostgreClient(
    val host: String = "127.0.0.1",
    val port: Int = 5432,
    val database: String = "default",
    val user: String = "root",
    val password: String? = null
) : DBClient, WithProperties by WithProperties.Mixin() {
    override val context: Job = Job().apply {
        invokeOnCompletion { close() }
    }

    /**
     * TBD: use multiple pipelines
     */
    private val connection: PostgreConnection = PostgreConnection(
        host, port, user, password, database
    )

    override suspend fun query(queryString: String): DbRowSet = connection.query(queryString)

    override fun close() {
        connection.close()
    }
}

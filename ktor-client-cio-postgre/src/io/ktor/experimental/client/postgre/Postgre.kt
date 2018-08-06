package io.ktor.experimental.client.postgre

import io.ktor.experimental.client.db.*
import io.ktor.experimental.client.util.*
import kotlinx.coroutines.experimental.*
import java.net.*

// https://www.postgresql.org/docs/11/static/index.html

class PostgreClient(
    val address: InetSocketAddress,
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
        address, user, password, database
    )

    override suspend fun query(queryString: String): DbRowSet = connection.query(queryString)

    override fun close() {
        connection.close()
    }
}

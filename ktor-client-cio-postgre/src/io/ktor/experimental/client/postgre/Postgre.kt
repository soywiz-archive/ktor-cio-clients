package io.ktor.experimental.client.postgre

import io.ktor.experimental.client.db.*
import io.ktor.experimental.client.util.*
import kotlinx.coroutines.experimental.*
import java.net.*

// https://www.postgresql.org/docs/11/static/index.html

class PostgreClient(
    address: InetSocketAddress,
    val database: String = "default",
    val user: String = "root",
    password: String? = null
) : DBClient, WithProperties by WithProperties.Mixin() {
    override val context: Job = Job()

    /**
     * TBD: use multiple pipelines
     */
    private val connection: PostgreConnection = PostgreConnection(
        address, user, password, database
    )

    override suspend fun query(queryString: String): DBResponse = connection.query(queryString)

    override fun close() {
        connection.close()
    }
}

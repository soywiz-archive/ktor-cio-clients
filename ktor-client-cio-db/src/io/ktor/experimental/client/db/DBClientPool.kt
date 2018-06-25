package io.ktor.experimental.client.db

import io.ktor.experimental.client.util.*
import kotlinx.coroutines.experimental.*

class DBClientPool(
    val maxClients: Int = 1024,
    val factory: ObjectFactory<DBClient>
) : DBClient, WithProperties by WithProperties.Mixin() {
    override val context: Job = Job()

    private val pool = AsyncPool(maxClients, ObjectFactory {
        factory.create().also {
            context.invokeOnCompletion { close() }
        }
    })

    override suspend fun transaction(callback: suspend DBClient.(DBClient) -> Unit) {
        pool.use { client ->
            client.transaction(callback)
        }
    }

    override suspend fun query(query: String): DbRowSet = pool.use { client -> client.query(query) }

    override fun close() {
        context.cancel()
    }
}

fun <T : DBClient> DBClientPool(maxClients: Int = 1024, block: () -> T): DBClientPool =
    DBClientPool(maxClients, factory = ObjectFactory { block() })

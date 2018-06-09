package io.ktor.experimental.client.db

import io.ktor.experimental.client.util.*

class DbClientPool(
    val maxClients: Int = 1024,
    val generator: suspend (index: Int) -> DbClient
) : DbClient, WithProperties by WithProperties.Mixin() {
    val pool = AsyncPool<DbClient>(maxItems = maxClients) { generator(it) }

    override suspend fun transaction(callback: suspend DbClient.(DbClient) -> Unit) {
        pool.tempAlloc { client ->
            client.transaction(callback)
        }
    }

    override suspend fun query(query: String): DbRowSet {
        return pool.tempAlloc { client ->
            client.query(query)
        }
    }

    override suspend fun close() {
        while (pool.availableFreed > 0) {
            val client = pool.alloc()
            client.close()
        }
    }
}

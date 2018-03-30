package com.soywiz.io.ktor.client.db

import com.soywiz.io.ktor.client.util.*

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
    }
}

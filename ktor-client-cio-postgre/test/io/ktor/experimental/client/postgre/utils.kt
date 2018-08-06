package io.ktor.experimental.client.postgre

import kotlinx.coroutines.experimental.*
import kotlinx.io.core.*
import java.net.*

fun postgreTest(
    address: InetSocketAddress,
    database: String = "default", user: String = "myuser", password: String = "hello",
    block: suspend PostgreClient.() -> Unit
): Unit = runBlocking {
    PostgreClient(address, database, user, password).use { it.block() }
}
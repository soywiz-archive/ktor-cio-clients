package io.ktor.experimental.client.db

import kotlinx.coroutines.experimental.io.*
import java.io.*

class DbClientConnection(
    val read: ByteReadChannel,
    val write: ByteWriteChannel,
    val close: Closeable
)

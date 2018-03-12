package com.soywiz.io.ktor.client.util

interface AsyncCloseable {
    suspend fun close()
}

suspend inline fun <T : AsyncCloseable> T.use(callback: (T) -> Unit) {
    try {
        callback(this)
    } finally {
        this.close()
    }
}

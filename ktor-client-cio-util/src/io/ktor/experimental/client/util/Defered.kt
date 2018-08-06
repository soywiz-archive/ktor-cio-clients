package io.ktor.experimental.client.util

import kotlinx.coroutines.experimental.*

/**
 * Use [block] to complete [deferred], also handles [block] exceptions
 */
inline fun <T> CompletableDeferred<T>.completeWith(block: () -> T) {
    try {
        complete(block())
    } catch (cause: Throwable) {
        completeExceptionally(cause)
    }
}
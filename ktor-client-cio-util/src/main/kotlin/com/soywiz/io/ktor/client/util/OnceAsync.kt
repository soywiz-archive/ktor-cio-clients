package com.soywiz.io.ktor.client.util

import kotlinx.coroutines.experimental.*

class OnceAsync {
    var deferred: kotlinx.coroutines.experimental.Deferred<Unit>? = null

    suspend operator fun invoke(callback: suspend () -> Unit) {
        if (deferred == null) {
            deferred = async { callback() }
        }
        return deferred!!.await()
    }
}

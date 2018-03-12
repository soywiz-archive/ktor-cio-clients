package com.soywiz.io.ktor.client.postgre

import kotlinx.coroutines.experimental.*

object PostgreSpike {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val client = PostgreClient(user = "soywiz")
            for (row in client.query("SELECT 1;")) {
                println(row.columns)
                println(row.cellsBytes.size)
            }
        }
    }
}

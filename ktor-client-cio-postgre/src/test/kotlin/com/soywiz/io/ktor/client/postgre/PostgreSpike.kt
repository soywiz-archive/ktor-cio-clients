package com.soywiz.io.ktor.client.postgre

import kotlinx.coroutines.experimental.*

object PostgreSpike {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val client = PostgreClient(user = "soywiz")
            for (row in client.query("SELECT now();")) {
            //for (row in client.query("SELECT 1;")) {
                for (cell in row) {
                    println(cell)
                }
                println(row.string(0))
                println(row.string("now"))
                println(row.columns)
                println(row.cells.size)
                println(row.cells.map { it?.size })
                println(row.cells.map { it?.toString(Charsets.UTF_8) })
            }
        }
    }
}

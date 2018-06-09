package io.ktor.experimental.client.cassandra

import kotlinx.coroutines.experimental.*

class CassandraTest {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runBlocking {
                val db = Cassandra(debug = false)
                val rows = db.query("SELECT uuid() FROM system.local;")
                for (row in rows) {
                    println(row)
                }
            }
        }
    }
}
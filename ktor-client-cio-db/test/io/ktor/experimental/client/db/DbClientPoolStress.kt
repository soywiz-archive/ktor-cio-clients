package io.ktor.experimental.client.db

import io.ktor.experimental.client.postgre.*
import io.ktor.experimental.client.util.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.atomic.*

object DbClientPoolStress {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val counter = AtomicInteger()
            val db = DBClientPool(maxClients = 32) {
                println("Creating postgre client: ${counter.getAndIncrement()}")
                runBlocking {
                    PostgreClient(
                        user = "ktor-cio-sample",
                        database = "ktor-cio-sample",
                        config = PostgreConfig(
                            useByteReadReadInt = true
                            //useByteReadReadInt = false
                        )
                    ).apply {
                        notices {
                            //println("NOTICE: $it")
                        }
                    }.withInfoHook {
                        //println("PG[$index]:$this")
                    }
                }
            }

            val jobs = (0 until 64).map {
                launch {
                    for (n in 0 until 1000) {
                        val rows = db.query("SELECT 1;").toList()
                        rows.first().first()
                    }
                }
            }
            println("Waiting jobs...")
            for (job in jobs) job.join()
            println("Done")
        }
    }
}
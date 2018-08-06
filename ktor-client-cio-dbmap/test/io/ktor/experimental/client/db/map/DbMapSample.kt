package io.ktor.experimental.client.db.map

import io.ktor.experimental.client.db.*
import io.ktor.experimental.client.postgre.*
import io.ktor.experimental.client.util.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.atomic.*

annotation class Index(val name: String)

data class City(
    val name: String,
    @Primary val id: Int = AUTO()
)

data class Counter(
    @Unique val name: String,
    val count: Int = 0
)

object DbMapSample {
    @JvmStatic
    fun main(args: Array<String>) {
        val counter = AtomicInteger()
        runBlocking {
            val db = DBClientPool {
                println("Creating postgre client: ${counter.getAndIncrement()}")
                runBlocking {
                    PostgreClient(user = "ktor-cio-sample", database = "ktor-cio-sample").apply {
                    }.withInfoHook {
                        println("PG[$counter]:$this")
                    }
                }
            }

            db.createTable<City>()
            db.createTable<Counter>()

            val job1 = launch {
                println(db.count<City> { City::name EQ "test" })
                println(db.count<City> { City::name EQ "test" })
            }

            val job2 = launch {
                println(db.count<City> { City::name EQ "test" })
            }

            val job3 = launch {
                println(db.count<City> { City::name EQ "test" })
            }

            db.transaction { db ->
                //ignoreErrors { db.insert(Counter("mycounter")) }

                val lastInsertId = db.insert(City("test"))
                println("lastInsertId=$lastInsertId")

                for (item in db.find<City>(
                    limit = 10,
                    fields = listOf(City::id),
                    orderBy = City::id.DESC() AND City::name.ASC()
                ) { City::id GT 4 }) {
                    println(item)
                }
                println(db.find<City>(limit = 2) { City::name LIKE "tes%" }.firstOrNull()?.id)

                db.update<Counter>(increments = listOf(Counter::count to +1)) { Counter::name EQ "mycounter" }
                val counter = db.first<Counter> { Counter::name EQ "mycounter" }
                //db.update(counter!!.copy(count = 9999))
                //val counter2 = db.first<Counter> { Counter::name EQ "mycounter" }
                //println("COUNTER: $counter -> $counter2")
                println("COUNTER: $counter")
                println(db.count<City> { City::id LT 10 })
                //db.delete<Counter>(counter)
                //throw RuntimeException("NONE!")
            }

            job1.join()
            job2.join()
            job3.join()

            db.close()

            /*

            db.transaction {
                createTable<City>()
                insert(City("test"))
                for (city in find<City> { City::name EQ 10 }) {
                    city.name
                }
            }
            */
        }
    }
}

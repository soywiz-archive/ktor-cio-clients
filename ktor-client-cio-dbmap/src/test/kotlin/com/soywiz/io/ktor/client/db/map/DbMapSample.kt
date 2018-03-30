package com.soywiz.io.ktor.client.db.map

import com.soywiz.io.ktor.client.db.*
import com.soywiz.io.ktor.client.postgre.*
import com.soywiz.io.ktor.client.util.*
import kotlinx.coroutines.experimental.*

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
        runBlocking {
            val db = DbClientPool { index ->
                println("Crrating postgre client: $index")
                PostgreClient(user = "ktor-cio-sample", database = "ktor-cio-sample").apply {
                    notices {
                        //println("NOTICE: $it")
                    }
                }.withInfoHook {
                    println("$this")
                }
            }

            db.createTable<City>()
            db.createTable<Counter>()

            launch {
                println(db.count<City> { City::name EQ "test" })
                println(db.count<City> { City::name EQ "test" })
            }

            launch {
                println(db.count<City> { City::name EQ "test" })
            }

            launch {
                println(db.count<City> { City::name EQ "test" })
            }

            db.transaction { db ->
                //ignoreErrors { db.insert(Counter("mycounter")) }

                val lastInsertId = db.insert(City("test"))
                println("lastInsertId=$lastInsertId")

                for (item in db.find<City>(
                    limit = 10,
                    fields = listOf(City::name, City::id),
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
                println(db.count<City> { City::id LT 10})
                //db.delete<Counter>(counter)
                //throw RuntimeException("NONE!")
            }

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

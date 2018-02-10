package com.soywiz.io.ktor.client.mysql

import com.soywiz.io.ktor.client.util.*
import kotlinx.coroutines.experimental.*

class MysqlTest {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runBlocking {
                Mysql().use { mysql ->
                    mysql.query("CREATE DATABASE IF NOT EXISTS mytest;")
                    mysql.useDatabase("mytest")
                    mysql.query("CREATE TABLE IF NOT EXISTS mytable (demo INT, demo2 VARCHAR(64));")
                    mysql.query("INSERT INTO mytable (demo, demo2) VALUES (1, 'hello world')")
                    mysql.query("INSERT INTO mytable (demo, demo2) VALUES (1, 'hello world')")
                    for (row in mysql.query("SELECT * from mytable")) {
                        println(row)
                    }
                    println(mysql.query("SELECT NOW();").first().date(0))
                }
            }
        }
    }
}
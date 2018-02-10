package com.soywiz.io.ktor.client.mysql

import kotlinx.coroutines.experimental.*

class MysqlTest {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runBlocking {
                val mysql = Mysql()
                //val rows = mysql.query("SELECT 1;")
                //val rows = mysql.query("SELECT NOW();")
                val rows = mysql.query("SELECT NULL, 1, NOW();")
                for (row in rows) {
                    println(row)
                }
                mysql.close()
            }
        }
    }
}
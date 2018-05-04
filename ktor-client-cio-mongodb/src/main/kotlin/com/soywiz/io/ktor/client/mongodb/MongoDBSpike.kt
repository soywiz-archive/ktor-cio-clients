package com.soywiz.io.ktor.client.mongodb

import kotlinx.coroutines.experimental.*

object MongoDBSpike {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val mongo = MongoDB()
            //println(mongo.isMaster())
            //println(mongo.listIndexes("simple", "hello"))
            //println(mongo.insert("simple", "hello", mapOf("a" to "b")))
            println(mongo.eval("admin", "function() { return {a: 10}; }"))
            //println(mongo.eval("admin", "functio() { return {a: 10}; }"))
            println(mongo.find("simple", "hello", limit = 1))
        }
    }
}
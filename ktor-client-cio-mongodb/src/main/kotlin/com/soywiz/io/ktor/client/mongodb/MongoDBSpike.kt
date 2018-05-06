package com.soywiz.io.ktor.client.mongodb

import kotlinx.coroutines.experimental.*

object MongoDBSpike {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val mongo = MongoDB()
            //println(mongo.isMaster())
            //println(mongo.listIndexes("simple", "hello"))
            println(mongo.insert("simple", "hello", mapOf("a" to "b")))
            println(mongo.insert("simple", "hello", mapOf("a" to "c")))
            println(mongo.eval("admin", "function() { return {a: 10}; }"))
            //println(mongo.eval("admin", "functio() { return {a: 10}; }"))
            println(mongo.find("simple", "hello") { "a" eq "b" })
            println(mongo.update("simple", "hello",
                MongoUpdate(mapOf("\$set" to mapOf("a" to "e")), multi = true) { "a" eq "d" }
            ))
            mongo.delete("simple", "hello", limit = true) { "a" eq "d" }
            println(mongo.find("simple", "hello"))
        }
    }
}

/*
    updates:
    [
{ q: <query>, u: <update>, upsert: <boolean>, multi: <boolean>, collation: <document> },
{ q: <query>, u: <update>, upsert: <boolean>, multi: <boolean>, collation: <document> },
{ q: <query>, u: <update>, upsert: <boolean>, multi: <boolean>, collation: <document> },
...
],
ordered: <boolean>,
writeConcern: { <write concern> },
bypassDocumentValidation: <boolean>
)

*/
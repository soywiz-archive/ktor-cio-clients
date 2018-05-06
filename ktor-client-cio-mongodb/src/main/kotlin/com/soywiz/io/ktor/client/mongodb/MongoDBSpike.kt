package com.soywiz.io.ktor.client.mongodb

import kotlinx.coroutines.experimental.*

object MongoDBSpike {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val mongo = MongoDB()
            val simpleDb = mongo["simple"]
            val helloCollection = simpleDb["hello"]
            //println(mongo.isMaster())
            //println(mongo.listIndexes("simple", "hello"))
            println(helloCollection.insert(mapOf("a" to "b")))
            println(helloCollection.insert(mapOf("a" to "c")))
            println(simpleDb.eval("function() { return {a: 10}; }"))
            //println(mongo.eval("admin", "functio() { return {a: 10}; }"))
            println(helloCollection.find { "a" eq "b" })
            println(helloCollection.update(
                MongoUpdate(mapOf("\$set" to mapOf("a" to "e")), multi = true) { "a" eq "d" }
            ))
            helloCollection.delete(limit = true) { "a" eq "d" }
            println(helloCollection.find())
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
package com.soywiz.io.ktor.client.mongodb

import com.soywiz.io.ktor.client.mongodb.gridfs.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*

object MongoDBSpike {
    object GridTest {
        @JvmStatic
        fun main(args: Array<String>) {
            runBlocking {
                val mongo = MongoDB()
                val grid = mongo["test"].gridFs()
                val bytes = buildPacket { for (n in 0 until 256 * 1024) writeInt(n) }.readBytes()
                grid.put("multi-chunk-file", ByteReadChannel(bytes))
                val bytes2 = grid.get("multi-chunk-file").readRemaining().readBytes()
                println(bytes.contentEquals(bytes2))

                //println(grid.getInfo("README.md"))
                //val data = grid.get("README.md", 10L until 20L).readRemaining().readBytes()
                //val data = grid.get("README.md").readRemaining().readBytes()
                //println(data.toString(Charsets.UTF_8))
            }
        }
    }
    object A {
        @JvmStatic
        fun main(args: Array<String>) {
            runBlocking {
                val mongo = MongoDB()
                val simpleDb = mongo["simple1"]
                val helloCollection = simpleDb["hello1"]
                println(helloCollection.find().min("a"))
                println(helloCollection.find().max("a"))
                println(helloCollection.find { "a" lt 300 }.max("a"))
                //for (n in 0 until 410) helloCollection.insert(mapOf("a" to n))
                //helloCollection.query().deleteAll()
                //for (item in helloCollection.query()) {
                //    println("item: $item")
                //}
                //println(helloCollection.query().skip(10).limit(20).count())
                //println(helloCollection.query().toList().size)
                /*
                val result = helloCollection.find { all() }
                println(helloCollection.getMore(result.cursorId))
                println(helloCollection.getMore(result.cursorId))
                println(helloCollection.getMore(result.cursorId))
                println(helloCollection.getMore(result.cursorId))
                println(helloCollection.getMore(result.cursorId))
                println(helloCollection.getMore(result.cursorId))
                println(helloCollection.getMore(result.cursorId))
                println(helloCollection.getMore(result.cursorId))
                */

                //for (n in 0 until 410) helloCollection.insert(mapOf("a" to 1))
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val mongo = MongoDB()
            val simpleDb = mongo["simple"]
            val helloCollection = simpleDb["hello"]
            //println(mongo.isMaster())
            //println(mongo.listIndexes("simple", "hello"))
            println(helloCollection.listIndexes())
            println(helloCollection.createIndex("a_index", "a" to +1))
            println(helloCollection.insert(mapOf("a" to "b")))
            println(helloCollection.insert(mapOf("a" to "c")))
            println(simpleDb.eval("function() { return {a: 10}; }"))
            //println(mongo.eval("admin", "functio() { return {a: 10}; }"))
            println(helloCollection.findFirstBatch { "a" eq "b" })
            println(helloCollection.update(
                MongoUpdate(mapOf("\$set" to mapOf("a" to "e")), multi = true) { "a" eq "d" }
            ))
            helloCollection.delete(limit = true) { "a" eq "d" }
            println(helloCollection.findFirstBatch())
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
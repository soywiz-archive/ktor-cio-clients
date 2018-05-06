package com.soywiz.io.ktor.client.mongodb

import com.soywiz.io.ktor.client.mongodb.bson.*

class MongoDBDatabase(val mongo: MongoDB, val db: String)

class MongoDBCollection(val db: MongoDBDatabase, val collection: String) {
    val dbName get() = db.db
    val mongo get() = db.mongo
}

operator fun MongoDB.get(db: String): MongoDBDatabase = MongoDBDatabase(this, db)
operator fun MongoDBDatabase.get(collection: String): MongoDBCollection = MongoDBCollection(this, collection)

suspend inline fun MongoDBDatabase.runCommand(
    numberToSkip: Int = 0, numberToReturn: Int = 1,
    mapGen: MutableMap<String, Any?>.() -> Unit
): MongoDB.Reply = mongo.runCommand(db, mongoMap(mapGen), numberToSkip, numberToReturn)

/*
"client" to mapOf(
"application" to mapOf("name" to "ktor-client-cio-mongodb"),
//"application" to mapOf("name" to "MongoDB Shell"),
"driver" to mapOf(
    "name" to "ktor-client-cio-mongodb",
    //"version" to "0.0.1"
    //"name" to "MongoDB Internal Client",
    "version" to "3.6.4"
),
"os" to mapOf(
    "type" to "Darwin",
    "name" to "Mac OS X",
    "architecture" to "x86_64",
    "version" to "17.5.0"
)
*/
suspend fun MongoDB.isMaster(): MongoDB.Reply = runCommand("admin") { putNotNull("isMaster", true) }

// @TODO: Incomplete
suspend fun MongoDBCollection.listIndexes(): MongoDB.Reply {
    return db.runCommand { putNotNull("listIndexes", collection) }
}

/**
 * https://docs.mongodb.com/v3.4/reference/command/insert/
 */
suspend fun MongoDBCollection.insert(
    vararg documents: BsonDocument,
    ordered: Boolean? = null,
    writeConcern: BsonDocument? = null,
    bypassDocumentValidation: Boolean? = null
): MongoDB.Reply {
    val result = db.runCommand {
        putNotNull("insert", collection)
        putNotNull("documents", documents.toList())
        putNotNull("ordered", ordered)
        putNotNull("writeConcern", writeConcern)
        putNotNull("bypassDocumentValidation", bypassDocumentValidation)
    }
    return result
}

/**
 * Example: mongo.eval("admin", "function() { return {a: 10}; }")
 * Returns the result of the function or throws a [MongoDBException] on error.
 */
suspend fun MongoDBDatabase.eval(function: String, vararg args: Any?): Any? {
    return runCommand {
        putNotNull("eval", BsonJavascriptCode(function))
        putNotNull("args", args.toList())
    }.checkErrors().firstDocument["retval"]
}

/**
 * https://docs.mongodb.com/v3.4/reference/command/find/
 */
suspend fun MongoDBCollection.find(
    sort: BsonDocument? = null,
    projection: BsonDocument? = null,
    hint: Any? = null,
    skip: Int? = null,
    limit: Int? = null,
    batchSize: Int? = null,
    singleBatch: Boolean? = null,
    comment: String? = null,
    maxScan: Int? = null,
    maxTimeMs: Int? = null,
    readConcern: BsonDocument? = null,
    max: BsonDocument? = null,
    min: BsonDocument? = null,
    returnKey: Boolean? = null,
    showRecordId: Boolean? = null,
    snapshot: Boolean? = null,
    tailable: Boolean? = null,
    oplogReplay: Boolean? = null,
    noCursorTimeout: Boolean? = null,
    awaitData: Boolean? = null,
    allowPartialResults: Boolean? = null,
    collation: BsonDocument? = null,
    filter: (MongoDBQueryBuilder.() -> BsonDocument)? = null
): MongoDB.Reply {
    val result = db.runCommand {
        putNotNull("find", collection)
        if (filter != null) putNotNull("filter", filter(MongoDBQueryBuilder))
        putNotNull("sort", sort)
        putNotNull("projection", projection)
        putNotNull("hint", hint)
        putNotNull("skip", skip)
        putNotNull("limit", limit)
        putNotNull("batchSize", batchSize)
        putNotNull("singleBatch", singleBatch)
        putNotNull("comment", comment)
        putNotNull("maxScan", maxScan)
        putNotNull("maxTimeMs", maxTimeMs)
        putNotNull("readConcern", readConcern)
        putNotNull("max", max)
        putNotNull("min", min)
        putNotNull("returnKey", returnKey)
        putNotNull("showRecordId", showRecordId)
        putNotNull("snapshot", snapshot)
        putNotNull("tailable", tailable)
        putNotNull("oplogReplay", oplogReplay)
        putNotNull("noCursorTimeout", noCursorTimeout)
        putNotNull("awaitData", awaitData)
        putNotNull("allowPartialResults", allowPartialResults)
        putNotNull("collation", collation)
    }
    return result
}

data class MongoUpdate(
    val u: BsonDocument,
    val upsert: Boolean? = null,
    val multi: Boolean? = null,
    val collation: BsonDocument? = null,
    val q: MongoDBQueryBuilder.() -> BsonDocument
)

/**
 * https://docs.mongodb.com/v3.4/reference/command/update/
 * https://docs.mongodb.com/manual/reference/operator/update-field/
 */
suspend fun MongoDBCollection.update(
    vararg updates: MongoUpdate,
    ordered: Boolean? = null,
    writeConcern: BsonDocument? = null,
    bypassDocumentValidation: Boolean? = null
): BsonDocument {
    //{ok=1, nModified=26, n=26}

    val result = db.runCommand {
        putNotNull("update", collection)
        putNotNull("updates", updates.map { update ->
            mongoMap {
                putNotNull("q", update.q(MongoDBQueryBuilder))
                putNotNull("u", update.u)
                putNotNull("upsert", update.upsert)
                putNotNull("multi", update.multi)
                putNotNull("collation", update.collation)
            }
        })
    }.checkErrors()
    return result.firstDocument
}

/**
 * https://docs.mongodb.com/v3.4/reference/command/delete/
 */
suspend fun MongoDBCollection.delete(
    limit: Boolean,
    collation: BsonDocument? = null,
    ordered: Boolean? = null,
    writeConcern: BsonDocument? = null,
    q: (MongoDBQueryBuilder.() -> BsonDocument)
): BsonDocument {
    val result = db.runCommand {
        putNotNull("delete", collection)
        putNotNull("deletes", listOf(
            mongoMap {
                putNotNull("q", q.invoke(MongoDBQueryBuilder))
                putNotNull("limit", if (limit) 1 else 0)
                putNotNull("collation", collation)
            }
        ))
        putNotNull("ordered", ordered)
        putNotNull("writeConcern", writeConcern)
    }.checkErrors()
    return result.firstDocument
}
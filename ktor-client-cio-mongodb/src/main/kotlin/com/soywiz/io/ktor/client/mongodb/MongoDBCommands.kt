package com.soywiz.io.ktor.client.mongodb

import com.soywiz.io.ktor.client.mongodb.bson.*


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
suspend fun MongoDB.listIndexes(db: String, index: String): MongoDB.Reply {
    return runCommand(db) {
        putNotNull("listIndexes", index)
        putNotNull("cursor", mapOf<String, Any?>())
    }
}

suspend fun MongoDB.insert(
    db: String,
    collection: String,
    vararg documents: Map<String, Any?>,
    ordered: Boolean? = null,
    writeConcern: Map<String, Any?>? = null,
    bypassDocumentValidation: Boolean? = null
): MongoDB.Reply {
    val result = runCommand(db) {
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
suspend fun MongoDB.eval(db: String, function: String, vararg args: Any?): Any? {
    val doc = runCommand(db) {
        putNotNull("eval", BsonJavascriptCode(function))
        putNotNull("args", args.toList())
    }.documents.first()
    val errmsg = doc["errmsg"]?.toString()
    if (errmsg != null) throw MongoDBException(errmsg)
    return doc["retval"]
}

suspend fun MongoDB.find(
    db: String,
    collection: String,
    filter: Map<String, Any?>? = null,
    sort: Map<String, Any?>? = null,
    projection: Map<String, Any?>? = null,
    hint: Any? = null,
    skip: Int? = null,
    limit: Int? = null,
    batchSize: Int? = null,
    singleBatch: Boolean? = null,
    comment: String? = null,
    maxScan: Int? = null,
    maxTimeMs: Int? = null,
    readConcern: Map<String, Any?>? = null,
    max: Map<String, Any?>? = null,
    min: Map<String, Any?>? = null,
    returnKey: Boolean? = null,
    showRecordId: Boolean? = null,
    snapshot: Boolean? = null,
    tailable: Boolean? = null,
    oplogReplay: Boolean? = null,
    noCursorTimeout: Boolean? = null,
    awaitData: Boolean? = null,
    allowPartialResults: Boolean? = null,
    collation: Map<String, Any?>? = null
): MongoDB.Reply {
    val result = runCommand(db) {
        putNotNull("find", collection)
        putNotNull("filter", filter)
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

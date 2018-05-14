package com.soywiz.io.ktor.client.mongodb

import com.soywiz.io.ktor.client.mongodb.bson.*
import com.soywiz.io.ktor.client.util.*

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
    }.checkErrors()
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

data class MongoDBQueryConfig(
    val collection: MongoDBCollection,
    val sort: BsonDocument? = null,
    val filter: (MongoDBQueryBuilder.() -> BsonDocument)? = null,
    val projection: BsonDocument? = null,
    val skip: Int? = null,
    val limit: Int? = null
)

data class MongoDBQuery(val config: MongoDBQueryConfig) : SuspendingSequence<BsonDocument> {
    fun skip(count: Int) = copy(config = config.copy(skip = count))
    fun limit(count: Int) = copy(config = config.copy(limit = count))
    fun filter(newFilter: (MongoDBQueryBuilder.() -> BsonDocument)) = copy(config = config.copy(filter = {
        if (config.filter != null) ((config.filter)() and newFilter()) else newFilter()
    }))

    fun include(vararg fieldsToInclude: String) =
        copy(config = config.copy(projection = (config.projection ?: mapOf()) + fieldsToInclude.map { it to 1 }.toMap()))

    fun exclude(vararg fieldsToExclude: String) =
        copy(config = config.copy(projection = (config.projection ?: mapOf()) + fieldsToExclude.map { it to 0 }.toMap()))

    fun sortedBy(vararg pairs: Pair<String, Int>) = copy(config = config.copy(sort = pairs.toList().toMap()))

    override suspend fun iterator(): SuspendingIterator<BsonDocument> {
        return config.collection.find(sort = config.sort, projection = config.projection, skip = config.skip, limit = config.limit, filter = config.filter)
            .toSuspendingSequence().iterator()
    }

    suspend fun firstOrNull(): BsonDocument? = limit(1).toList().firstOrNull()
}

enum class MongoDBDeleteKind {
    ONE, ALL
}

suspend fun MongoDBQuery.delete(kind: MongoDBDeleteKind): BsonDocument =
    config.collection.delete(limit = kind == MongoDBDeleteKind.ONE, q = { config.filter?.invoke(this) ?: mapOf() })

suspend fun MongoDBQuery.deleteOne() = delete(kind = MongoDBDeleteKind.ONE)
suspend fun MongoDBQuery.deleteAll() = delete(kind = MongoDBDeleteKind.ALL)

suspend fun MongoDBCollection.query(filter: (MongoDBQueryBuilder.() -> BsonDocument)? = null): MongoDBQuery =
    MongoDBQuery(MongoDBQueryConfig(this, filter = filter))

suspend fun MongoDBCollection.select(filter: (MongoDBQueryBuilder.() -> BsonDocument)? = null): MongoDBQuery =
    MongoDBQuery(MongoDBQueryConfig(this, filter = filter))

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
): List<BsonDocument> {
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
    }.checkErrors()
    return (result.firstDocument["cursor"] as BsonDocument)["firstBatch"] as List<BsonDocument>
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

/**
 * https://docs.mongodb.com/manual/reference/command/createIndexes/
 */
class MongoDBIndex(
    val name: String,
    vararg val keys: Pair<String, Int>,
    val unique: Boolean? = null,
    val background: Boolean? = null,
    val partialFilterExpression: BsonDocument? = null,
    val sparse: Boolean? = null,
    val expireAfterSeconds: Int? = null,
    val storageEngine: BsonDocument? = null,
    val weights: BsonDocument? = null,
    val default_language: String? = null,
    val language_override: String? = null,
    val textIndexVersion: Int? = null,
    val _2dsphereIndexVersion: Int? = null,
    val bits: Int? = null,
    val min: Number? = null,
    val max: Number? = null,
    val bucketSize: Number? = null,
    val collation: BsonDocument? = null
)

/**
 * https://docs.mongodb.com/manual/reference/command/createIndexes/
 */
suspend fun MongoDBCollection.createIndexes(
    vararg indexes: MongoDBIndex,
    writeConcern: BsonDocument? = null
): BsonDocument {
    val result = db.runCommand {
        putNotNull("createIndexes", collection)
        putNotNull("indexes", indexes.map { index ->
            mongoMap {
                putNotNull("key", mongoMap {
                    for (key in index.keys) {
                        putNotNull(key.first, key.second)
                    }
                })
                putNotNull("name", index.name)
                putNotNull("unique", index.unique)
                putNotNull("background", index.background)
                putNotNull("partialFilterExpression", index.partialFilterExpression)
                putNotNull("sparse", index.sparse)
                putNotNull("expireAfterSeconds", index.expireAfterSeconds)
                putNotNull("storageEngine", index.storageEngine)
                putNotNull("weights", index.weights)
                putNotNull("default_language", index.default_language)
                putNotNull("language_override", index.language_override)
                putNotNull("textIndexVersion", index.textIndexVersion)
                putNotNull("2dsphereIndexVersion", index._2dsphereIndexVersion)
                putNotNull("bits", index.bits)
                putNotNull("min", index.min)
                putNotNull("max", index.max)
                putNotNull("bucketSize", index.bucketSize)
                putNotNull("collation", index.collation)
            }
        })
        putNotNull("writeConcern", writeConcern)
    }.checkErrors()
    return result.firstDocument
}

suspend fun MongoDBCollection.createIndex(
    name: String,
    vararg keys: Pair<String, Int>,
    unique: Boolean? = null,
    background: Boolean? = null,
    partialFilterExpression: BsonDocument? = null,
    sparse: Boolean? = null,
    expireAfterSeconds: Int? = null,
    storageEngine: BsonDocument? = null,
    weights: BsonDocument? = null,
    default_language: String? = null,
    language_override: String? = null,
    textIndexVersion: Int? = null,
    _2dsphereIndexVersion: Int? = null,
    bits: Int? = null,
    min: Number? = null,
    max: Number? = null,
    bucketSize: Number? = null,
    collation: BsonDocument? = null,
    writeConcern: BsonDocument? = null
): BsonDocument {
    return createIndexes(
        MongoDBIndex(
            name, *keys,
            unique = unique, background = background, partialFilterExpression = partialFilterExpression,
            sparse = sparse, expireAfterSeconds = expireAfterSeconds, storageEngine = storageEngine,
            weights = weights, default_language = default_language, language_override = language_override,
            textIndexVersion = textIndexVersion, _2dsphereIndexVersion = _2dsphereIndexVersion, bits = bits,
            min = min, max = max, bucketSize = bucketSize, collation = collation
        ),
        writeConcern = writeConcern
    )
}

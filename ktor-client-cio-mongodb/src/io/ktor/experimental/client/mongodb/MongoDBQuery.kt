package io.ktor.experimental.client.mongodb

import io.ktor.experimental.client.mongodb.bson.*
import io.ktor.experimental.client.mongodb.util.*
import io.ktor.experimental.client.util.*

data class MongoDBQuery(val config: Config) : SuspendingSequence<BsonDocument> {
    data class Config(
        val collection: MongoDBCollection,
        val sort: BsonDocument? = null,
        val filter: (MongoDBQueryBuilder.() -> BsonDocument)? = null,
        val projection: BsonDocument? = null,
        val skip: Int? = null,
        val limit: Int? = null
    ) {
        val db get() = collection.db
        val filterDocOrNull by lazy { filter?.invoke(MongoDBQueryBuilder) }
        val filterDoc by lazy { filterDocOrNull ?: mapOf() }
    }

    fun skip(count: Int) = copy(config = config.copy(skip = count))
    fun limit(count: Int) = copy(config = config.copy(limit = count))
    fun filter(newFilter: (MongoDBQueryBuilder.() -> BsonDocument)) = copy(config = config.copy(filter = {
        if (config.filter != null) ((config.filter)() and newFilter()) else newFilter()
    }))

    fun include(vararg fieldsToInclude: String) =
        copy(
            config = config.copy(
                projection = (config.projection ?: mapOf()) + fieldsToInclude.map { it to 1 }.toMap()
            )
        )

    fun exclude(vararg fieldsToExclude: String) =
        copy(
            config = config.copy(
                projection = (config.projection ?: mapOf()) + fieldsToExclude.map { it to 0 }.toMap()
            )
        )

    fun sortedBy(vararg pairs: Pair<String, Int>) = copy(config = config.copy(sort = pairs.toList().toMap()))

    override suspend fun iterator(): SuspendingIterator<BsonDocument> {
        return config.collection.findFirstBatch(
            sort = config.sort,
            projection = config.projection,
            skip = config.skip,
            limit = config.limit,
            filter = config.filter
        ).pagedIterator()
    }

    suspend fun firstOrNull(): BsonDocument? = limit(1).toList().firstOrNull()
}

enum class MongoDBDeleteKind {
    ONE, ALL
}

suspend fun MongoDBQuery.count(): Long {
    val collection = config.collection
    val db = collection.db
    val result = db.runCommand {
        putNotNull("count", collection.collection)
        putNotNull("query", config.filterDoc)
        putNotNull("limit", config.limit)
        putNotNull("skip", config.skip)
    }.checkErrors()
    return Dynamic { result.firstDocument["n"].long }
}

suspend fun MongoDBQuery.delete(kind: MongoDBDeleteKind): BsonDocument =
    config.collection.delete(limit = kind == MongoDBDeleteKind.ONE, q = { config.filterDoc })

suspend fun MongoDBQuery.deleteOne() = delete(kind = MongoDBDeleteKind.ONE)
suspend fun MongoDBQuery.deleteAll() = delete(kind = MongoDBDeleteKind.ALL)

suspend fun MongoDBCollection.query(filter: (MongoDBQueryBuilder.() -> BsonDocument)? = null): MongoDBQuery =
    MongoDBQuery(
        MongoDBQuery.Config(
            this,
            filter = filter
        )
    )

suspend fun MongoDBCollection.select(filter: (MongoDBQueryBuilder.() -> BsonDocument)? = null): MongoDBQuery =
    MongoDBQuery(
        MongoDBQuery.Config(
            this,
            filter = filter
        )
    )

suspend fun MongoDBCollection.find(filter: (MongoDBQueryBuilder.() -> BsonDocument)? = null): MongoDBQuery =
    MongoDBQuery(
        MongoDBQuery.Config(
            this,
            filter = filter
        )
    )

suspend fun MongoDBQuery.group(group: BsonDocument): List<BsonDocument> {
    val pipelines = arrayListOf<BsonDocument>()
    if (config.skip != null) pipelines += mapOf("\$skip" to config.skip)
    if (config.limit != null) pipelines += mapOf("\$limit" to config.limit)
    if (config.filterDocOrNull != null) pipelines += mapOf("\$match" to config.filterDocOrNull)
    pipelines += mapOf("\$group" to group)
    val result = this.config.collection.aggregate(*pipelines.toTypedArray())
        .checkErrors()
    return result.firstDocument["result"] as List<BsonDocument>
}

suspend fun MongoDBQuery.max(field: String): Any? =
    group(mapOf("_id" to null, "max" to mapOf("\$max" to "\$$field"))).first()["max"]

suspend fun MongoDBQuery.min(field: String): Any? =
    group(mapOf("_id" to null, "min" to mapOf("\$min" to "\$$field"))).first()["min"]


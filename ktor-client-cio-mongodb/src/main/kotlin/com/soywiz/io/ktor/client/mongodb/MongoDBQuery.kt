package com.soywiz.io.ktor.client.mongodb

import com.soywiz.io.ktor.client.mongodb.bson.*
import com.soywiz.io.ktor.client.util.*

data class MongoDBQuery(val config: Config) : SuspendingSequence<BsonDocument> {
    data class Config(
        val collection: MongoDBCollection,
        val sort: BsonDocument? = null,
        val filter: (MongoDBQueryBuilder.() -> BsonDocument)? = null,
        val projection: BsonDocument? = null,
        val skip: Int? = null,
        val limit: Int? = null
    )

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
        var current = config.collection.findFirstBatch(
            sort = config.sort,
            projection = config.projection,
            skip = config.skip,
            limit = config.limit,
            filter = config.filter
        )

        return object : SuspendingIterator<BsonDocument> {
            private val hasMoreInBatch get() = pos < current.batch.size
            private val hasMoreBatches get() = current.batch.isNotEmpty()
            var pos = 0

            private suspend fun getMore() {
                if (hasMoreBatches) {
                    current = current.getMore()
                }
                pos = 0
            }

            private suspend fun getMoreIfRequired() {
                if (!hasMoreInBatch) getMore()
            }

            override suspend fun hasNext(): Boolean {
                getMoreIfRequired()
                return hasMoreInBatch
            }

            override suspend fun next(): BsonDocument {
                getMoreIfRequired()
                if (!hasMoreInBatch) throw NoSuchElementException()
                return current.batch[pos++]
            }
        }
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
    MongoDBQuery(MongoDBQuery.Config(this, filter = filter))

suspend fun MongoDBCollection.select(filter: (MongoDBQueryBuilder.() -> BsonDocument)? = null): MongoDBQuery =
    MongoDBQuery(MongoDBQuery.Config(this, filter = filter))

suspend fun MongoDBCollection.find(filter: (MongoDBQueryBuilder.() -> BsonDocument)? = null): MongoDBQuery =
    MongoDBQuery(MongoDBQuery.Config(this, filter = filter))

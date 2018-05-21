package com.soywiz.io.ktor.client.mongodb.gridfs

import com.soywiz.io.ktor.client.mongodb.*
import com.soywiz.io.ktor.client.mongodb.bson.*
import com.soywiz.io.ktor.client.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.security.*
import java.util.*
import kotlin.math.*

/**
 * https://docs.mongodb.com/manual/core/gridfs/
 */
class MongoDBGridFS(val db: MongoDBDatabase) {
    private val fsFiles = db["fs.files"]
    private val fsChunks = db["fs.chunks"]

    private suspend fun init() {
        fsChunks
            .createIndex("files_id_1_n_1", "files_id" to +1, "n" to +1, unique = true)
    }

    companion object {
        val DEFAULT_CHUNK_SIZE = 255 * 1024
    }

    suspend fun delete(filename: String) {
        val info = getInfoOrNull(filename) ?: return
        fsChunks.find { "files_id" eq info.fileId }.deleteAll()
        fsFiles.find { "_id" eq info.fileId }.deleteOne()
    }

    suspend fun put(
        filename: String,
        data: ByteReadChannel,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        uploadDate: Date = Date(),
        aliases: List<String> = listOf(),
        contentType: String? = null,
        metadata: BsonDocument? = null
    ) {
        // Delete if already exists
        delete(filename)

        val fileObjectId = BsonObjectId()
        val md5Builder = MessageDigest.getInstance("MD5")
        var n = 0
        var length = 0L
        val chunk = ByteArray(chunkSize)
        while (!data.isClosedForRead) {
            val thisChunkSize = data.readAvailable(chunk, 0, chunkSize)
            md5Builder.update(chunk)
            fsChunks.insert(mongoMap {
                put("_id", BsonObjectId())
                put("files_id", fileObjectId)
                put("n", n)
                put("data", chunk.copyOf(thisChunkSize))
            })
            length += thisChunkSize
            n++
        }
        val md5 = md5Builder.digest()
        fsFiles.insert(mongoMap {
            put("_id", fileObjectId)
            put("length", length)
            put("chunkSize", chunkSize)
            put("uploadDate", uploadDate)
            put("md5", md5.hex)
            put("filename", filename)
            putNotNull("contentType", contentType)
            if (aliases.isNotEmpty()) put("aliases", aliases)
            putNotNull("metadata", metadata)
        })
    }

    data class FileInfo(
        val fileId: BsonObjectId,
        val length: Long,
        val chunkSize: Int,
        val uploadDate: Date?,
        val md5: String,
        val filename: String,
        val contentType: String?,
        val aliases: List<String>?,
        val metadata: BsonDocument?
    )

    suspend fun getInfoOrNull(name: String): FileInfo? {
        val result = fsFiles.find { FileInfo::filename.name eq name }.firstOrNull()
        return result?.let {
            Dynamic {
                FileInfo(
                    fileId = result["_id"] as BsonObjectId,
                    length = result["length"].long,
                    chunkSize = result["chunkSize"].toIntDefault(DEFAULT_CHUNK_SIZE),
                    uploadDate = result["uploadDate"] as? Date?,
                    md5 = result["md5"].str,
                    filename = result["filename"].str,
                    contentType = result["contentType"]?.str,
                    aliases = result["aliases"]?.list?.filterIsInstance<String>(),
                    metadata = result["metadata"] as? BsonDocument?
                )
            }
        }
    }

    suspend fun getInfo(name: String): FileInfo = getInfoOrNull(name) ?: throw MongoDBFileNotFoundException(name)

    private suspend fun getChunk(fileId: BsonObjectId, n: Int): ByteArray {
        val doc = fsChunks.find { ("files_id" eq fileId) and ("n" eq n) }.firstOrNull()
        val data = (doc?.get("data") as? ByteArray?) ?: ((doc?.get("data") as? BsonBinaryBase?)?.data)
        return data ?: throw MongoDBChunkNotFoundException(fileId, n)
    }

    suspend fun get(name: String, range: LongRange? = null): ByteReadChannel {
        val info = getInfo(name)
        val rangeNotNull = range ?: (0L until info.length)
        val realRange =
            rangeNotNull.start.clamp(0L, info.length) until (rangeNotNull.endInclusive + 1).clamp(0L, info.length)
        return writer(DefaultDispatcher) {
            val end = realRange.endInclusive + 1
            var position = realRange.start
            fun remaining() = end - position
            while (remaining() > 0L) {
                val remaining = remaining()
                val chunk = (position / info.chunkSize).toInt()
                val skip = (position % info.chunkSize).toInt()
                val chunkData = getChunk(info.fileId, chunk)
                val availableInChunk = (chunkData.size - skip).toLong()
                val toRead = min(availableInChunk, remaining)
                if (toRead <= 0) throw IllegalStateException("invalid GridFS")
                //println("chunk: $chunk, skip:$skip, availableInChunk: $availableInChunk, toRead: $toRead, remaining: $remaining")
                channel.writeFully(chunkData, skip, toRead.toInt())
                position += toRead
            }
            this.channel.flush()

        }.channel
    }
}

class MongoDBChunkNotFoundException(val fileId: BsonObjectId, val n: Int) :
    MongoDBException("Can't find chunk $fileId@$n", -1)

fun MongoDBDatabase.gridFs() = MongoDBGridFS(this)

package com.soywiz.io.ktor.mongodb.gridfs

import com.soywiz.io.ktor.client.mongodb.*
import com.soywiz.io.ktor.client.mongodb.gridfs.*
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.experimental.io.*

@ContextDsl
fun Route.gridFS(grid: MongoDBGridFS, validate: suspend ApplicationCall.(filename: String) -> Unit = { }) {
    get("/{name}") {
        val name = call.parameters["name"] ?: error("name not specified")
        call.validate(name)
        call.respondMongoDBGridFSFile(grid, name)
    }
}

suspend fun ApplicationCall.respondMongoDBGridFSFile(
    db: MongoDBGridFS,
    file: String,
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    headers: Headers = Headers.build { }
) {
    try {
        respond(GridFSFile(db, file, contentType, status, headers))
    } catch (e: MongoDBFileNotFoundException) {

    }
}

class GridFSFile private constructor(
    val db: MongoDBGridFS,
    val file: String,
    val info: MongoDBGridFS.FileInfo,
    override val contentType: ContentType,
    override val status: HttpStatusCode?,
    override val headers: Headers
) : OutgoingContent.ReadChannelContent() {
    companion object {
        suspend operator fun invoke(
            db: MongoDBGridFS,
            file: String,
            contentType: ContentType?,
            status: HttpStatusCode?,
            headers: Headers
        ): GridFSFile {
            val info = db.getInfo(file)
            val realContentType = contentType ?: info.contentType?.let { ContentType.parse(it) } ?: ContentType.defaultForFilePath(file)
            //println("realContentType=$realContentType, contentLength=${info.length}")
            return GridFSFile(
                db, file, info,
                realContentType,
                status, headers
            )
        }
    }

    override val contentLength: Long get() = info.length

    // @TODO: These two functions should be suspend!
    override fun readFrom(): ByteReadChannel = db.get(info)

    override fun readFrom(range: LongRange): ByteReadChannel = db.get(info, range)
}

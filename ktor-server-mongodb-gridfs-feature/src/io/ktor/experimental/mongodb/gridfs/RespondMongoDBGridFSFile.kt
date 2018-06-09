package io.ktor.experimental.mongodb.gridfs

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.experimental.client.mongodb.*
import io.ktor.experimental.client.mongodb.gridfs.*
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
    grid: MongoDBGridFS,
    file: String,
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    headers: Headers = Headers.build { }
) {
    try {
        respond(GridFSFileContent(grid, file, contentType, status, headers))
    } catch (e: MongoDBFileNotFoundException) {

    }
}

class GridFSFileContent private constructor(
    val grid: MongoDBGridFS,
    val file: String,
    val info: MongoDBGridFS.FileInfo,
    override val contentType: ContentType,
    override val status: HttpStatusCode?,
    override val headers: Headers
) : OutgoingContent.ReadChannelContent() {
    companion object {
        suspend operator fun invoke(
            grid: MongoDBGridFS,
            file: String,
            contentType: ContentType?,
            status: HttpStatusCode?,
            headers: Headers
        ): GridFSFileContent {
            val info = grid.getInfo(file)
            val realContentType =
                contentType ?: info.contentType?.let { ContentType.parse(it) } ?: ContentType.defaultForFilePath(file)
            //println("realContentType=$realContentType, contentLength=${info.length}")
            return GridFSFileContent(
                grid, file, info,
                realContentType,
                status, headers
            )
        }
    }

    override val contentLength: Long get() = info.length

    // @TODO: These two functions should be suspend!
    override fun readFrom(): ByteReadChannel = grid.get(info)

    override fun readFrom(range: LongRange): ByteReadChannel = grid.get(info, range)
}

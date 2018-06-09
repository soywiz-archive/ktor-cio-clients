package io.ktor.experimental.amazon.s3

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.experimental.client.amazon.s3.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlin.coroutines.experimental.*

@ContextDsl
fun Route.amazonS3(bucket: S3Bucket, validate: suspend ApplicationCall.(filename: String) -> Unit = { }) {
    get("/{name}") {
        val name = call.parameters["name"] ?: error("name not specified")
        call.validate(name)
        call.respondAmazonS3File(bucket, name)
    }
}

suspend fun ApplicationCall.respondAmazonS3File(
    bucket: S3Bucket,
    file: String,
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    headers: Headers = Headers.build { }
) {
    respond(AmazonS3FileContent(bucket.file(file), contentType, status, headers))
}

class AmazonS3FileContent private constructor(
    val file: S3File,
    val stat: S3.Stat,
    override val contentType: ContentType,
    override val status: HttpStatusCode?,
    override val headers: Headers
) : OutgoingContent.ReadChannelContent() {
    companion object {
        suspend operator fun invoke(
            file: S3File,
            contentType: ContentType?,
            status: HttpStatusCode?,
            headers: Headers
        ): AmazonS3FileContent {
            val stat = file.stat()
            val realContentType = contentType ?: stat.contentType ?: ContentType.defaultForFilePath(file.file)
            return AmazonS3FileContent(file, stat, realContentType, status, headers)
        }
    }

    override val contentLength: Long get() = stat.length

    // @TODO: These two functions should be suspend!
    override fun readFrom(): ByteReadChannel = deferredByteReadChannel { file.get() }

    override fun readFrom(range: LongRange): ByteReadChannel =
        deferredByteReadChannel { file.get(range) }
}

// @TODO: Hack because OutgoingContent.ReadChannelContent.readFrom should be suspend!
fun deferredByteReadChannel(
    context: CoroutineContext = DefaultDispatcher,
    later: suspend () -> ByteReadChannel
): ByteReadChannel {
    return writer(context) {
        later().copyTo(this.channel)
    }.channel
}

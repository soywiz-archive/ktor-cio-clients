package com.soywiz.io.ktor.amazon.s3

import com.soywiz.io.ktor.client.amazon.s3.*
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.experimental.*
import java.nio.charset.*

object RespondAmazonS3FileSpike {
    @JvmStatic
    fun main(args: Array<String>) {
        embeddedServer(Netty, port = 8080) {
            val s3 = runBlocking {
                S3(
                    "http://127.0.0.1:4567/{bucket}/{key}",
                    accessKey = "demo", secretKey = "demo"
                ).apply {
                    put("mybucket/index.html", ByteArrayContentWithLength("HELLO WORLD FROM S3"))
                }
            }
            val bucket = s3.bucket("mybucket")
            install(PartialContent)
            routing {
                get("/{name}") {
                    val name = call.parameters["name"] ?: error("name not specified")
                    call.respondAmazonS3File(bucket, name)
                }
            }
        }.start(wait = true)
    }
}

// @TODO: ByteArrayContent should provide contentLength
class ByteArrayContentWithLength(private val bytes: ByteArray) : OutgoingContent.ByteArrayContent() {
    override val contentLength: Long = bytes.size.toLong()
    override fun bytes(): ByteArray = bytes
}

fun ByteArrayContentWithLength(
    str: String,
    charset: Charset = Charsets.UTF_8
): ByteArrayContentWithLength = ByteArrayContentWithLength(str.toByteArray(charset))

package io.ktor.experimental.client.amazon.s3

import io.ktor.content.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.nio.charset.*

object S3Spike {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val s3 = S3(
                "http://127.0.0.1:4567/{bucket}/{key}",
                accessKey = "demo", secretKey = "demo"
            )
            s3.put(
                "mybucket/hello.txt",
                ByteArrayContentWithLength("HELLO WORLD")
            )
            println(s3.stat("mybucket/hello.txt"))
            println(s3.get("mybucket/hello.txt").readRemaining().readText())
        }
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
): ByteArrayContentWithLength =
    ByteArrayContentWithLength(str.toByteArray(charset))

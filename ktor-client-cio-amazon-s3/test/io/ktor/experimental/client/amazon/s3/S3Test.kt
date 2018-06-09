package io.ktor.experimental.client.amazon.s3

import io.ktor.experimental.test.host.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import kotlin.test.*

class S3Test {
    val httpClient = LogHttpClient()
    val timeProvider = { 1486426548734L }
    lateinit var s3: S3

    init {
        runBlocking {
            s3 = S3(
                region = "demo",
                accessKey = "myaccesskey",
                secretKey = "mysecretKey",
                httpClient = httpClient.client,
                timeProvider = timeProvider
            )
        }
    }

    @Test
    fun checkGet() {
        runBlocking {
            httpClient.defaultResponse = httpClient.defaultResponse.withStringResponse("hello")

            assertEquals("hello", s3.bucket("test").file("hello.txt").readString())
            assertEquals(
                listOf("GET, https://test.s3-demo.amazonaws.com/hello.txt, Headers [date=[Tue, 07 Feb 2017 00:15:48 UTC], Authorization=[AWS myaccesskey:I/jL9Lkq+n6DT0ZuLmK71B/wABQ=]], "),
                httpClient.getAndClearLog()
            )
        }
    }

    @Test
    fun checkPut() {
        runBlocking {
            s3.bucket("test").file("hello.json").writeString("hello")
            assertEquals(
                listOf("PUT, https://test.s3-demo.amazonaws.com/hello.json, Headers [Content-Type=[application/json], Content-Length=[5], x-amz-acl=[private], date=[Tue, 07 Feb 2017 00:15:48 UTC], Authorization=[AWS myaccesskey:lzucas2uhwPa2vsVJoRzta6RAtg=]], hello"),
                httpClient.getAndClearLog()
            )
        }
    }

    @Test
    fun checkPut2() {
        runBlocking {
            s3.file("test", "hello.txt")
                .writeString("hello", contentType = ContentType.Image.JPEG, access = S3.ACL.PUBLIC_READ)
            assertEquals(
                listOf("PUT, https://test.s3-demo.amazonaws.com/hello.txt, Headers [Content-Type=[image/jpeg], Content-Length=[5], x-amz-acl=[public-read], date=[Tue, 07 Feb 2017 00:15:48 UTC], Authorization=[AWS myaccesskey:DceOjup5BapxMUuh6Ww07viLyxg=]], hello"),
                httpClient.getAndClearLog()
            )
        }
    }

    @Test
    fun checkAbsoluteUrl() {
        runBlocking {
            assertEquals("https://.s3-demo.amazonaws.com/", s3.url)
            assertEquals("https://test.s3-demo.amazonaws.com/", s3.bucket("test").url)
            assertEquals("https://hello.s3-demo.amazonaws.com/world", s3.bucket("hello").file("world").url)
        }
    }
}

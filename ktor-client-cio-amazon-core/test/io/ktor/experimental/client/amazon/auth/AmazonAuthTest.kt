package io.ktor.experimental.client.amazon.auth

import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import java.net.*
import kotlin.test.*

class AmazonAuthTest {
    val method = HttpMethod.Get
    val accessKey = "AKIDEXAMPLE"
    val secretKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"
    val url = URL("https://iam.amazonaws.com/?Action=ListUsers&Version=2010-05-08")
    val region = "us-east-1"
    val service = "iam"
    val headers = Headers.build {
        append("Host", "iam.amazonaws.com")
        append("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        append("X-Amz-Date", "20150830T123600Z")
    }
    val payload = byteArrayOf()

    // http://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html
    // Task 1: Create a Canonical Request for Signature Version 4
    @Test
    fun task1Test() {
        runBlocking {
            val expected = listOf(
                "GET",
                "/",
                "Action=ListUsers&Version=2010-05-08",
                "content-type:application/x-www-form-urlencoded; charset=utf-8",
                "host:iam.amazonaws.com",
                "x-amz-date:20150830T123600Z",
                "",
                "content-type;host;x-amz-date",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            ).joinToString("\n")

            val request = AmazonAuth.V4.getCannonicalRequest(method, url, headers, payload)

            assertEquals(expected, request)
            assertEquals(
                "f536975d06c0309214f805bb90ccff089219ecd68b2577efef23edd43b7e1a59",
                hex(AmazonAuth.V4.SHA256(request.toByteArray(Charsets.UTF_8)))
            )
        }
    }

    // http://docs.aws.amazon.com/general/latest/gr/sigv4-create-string-to-sign.html
    // Task 2: Create a String to Sign for Signature Version 4
    @Test
    fun task2Test() {
        runBlocking {
            assertEquals(
                listOf(
                    "AWS4-HMAC-SHA256",
                    "20150830T123600Z",
                    "20150830/us-east-1/iam/aws4_request",
                    "f536975d06c0309214f805bb90ccff089219ecd68b2577efef23edd43b7e1a59"
                ).joinToString("\n"),
                AmazonAuth.V4.getStringToSign(method, url, headers, payload, region, service)
            )
        }
    }

    // http://docs.aws.amazon.com/general/latest/gr/sigv4-calculate-signature.html
    // Task 3: Calculate the Signature for AWS Signature Version 4
    @Test
    fun task3Test() {
        runBlocking {
            assertEquals(
                "f4780e2d9f65fa895f9c67b32ce1baf0b0d8a43505a000a1a9e090d414db404d",
                hex(AmazonAuth.V4.getSignatureKey(secretKey, "20120215", "us-east-1", "iam"))
            )
        }
    }

    @Test
    fun task4Test() {
        runBlocking {
            assertEquals(
                "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/iam/aws4_request, SignedHeaders=content-type;host;x-amz-date, Signature=5d672d79c15b13162d9279b0855cfba6789a8edb4c82c400e06b5924a6f2b5d7",
                AmazonAuth.V4.getAuthorization(accessKey, secretKey, method, url, headers, payload, region, service)
            )
        }
    }
}

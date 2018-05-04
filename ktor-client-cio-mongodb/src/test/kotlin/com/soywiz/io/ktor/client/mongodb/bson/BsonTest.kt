package com.soywiz.io.ktor.client.mongodb.bson

import org.junit.*
import kotlin.test.*

class BsonTest {
    @Test
    fun testHelloWorld() {
        assertEquals(
            "" +
                    "\\x16\\x00\\x00\\x00" +
                    "\\x02" +
                    "hello\\x00" +
                    "\\x06\\x00\\x00\\x00world\\x00" +
                    "\\x00",
            Bson.build(mapOf("hello" to "world")).toEscapedString()
        )
    }

    @Test
    fun testSample2() {
        assertEquals(
            "1\\x00\\x00\\x00" +
                    "\\x04BSON\\x00" +
                    "\\x26\\x00\\x00\\x00" +
                    "\\x020\\x00\\x08\\x00\\x00\\x00awesome\\x00" +
                    "\\x011\\x00333333\\x14\\x40" +
                    "\\x102\\x00ￂ\\x07\\x00\\x00" +
                    "\\x00" +
                    "\\x00",
            Bson.build(mapOf("BSON" to listOf("awesome", 5.05, 1986))).toEscapedString()
        )
    }
}

fun ByteArray.toEscapedString(): String {
    var out = ""
    for (c in this) {
        if (c.toChar().isLetterOrDigit()) {
            out += c.toChar()
        } else {
            out += "\\x%02x".format(c.toInt())
        }
    }
    return out
}

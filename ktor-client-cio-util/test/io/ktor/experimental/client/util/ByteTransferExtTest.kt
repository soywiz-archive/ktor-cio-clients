package io.ktor.experimental.client.util

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.junit.*
import kotlin.test.*

class ByteTransferExtTest {
    private val charset = Charsets.UTF_8
    private val delimiter = '\n'.toByte()
    private val bytes = "HELLO\nWORLD\nAB\nA\n\n".toByteArray(charset)
    private val expectedList = listOf("HELLO", "WORLD", "AB", "A", "")

    @Test
    fun testReadUntil() {
        runBlocking {
            for (bufferSize in listOf(1, 2, 3, 4, 5, 6, 1024)) {
                testReadUntil(bufferSize)
            }
        }
    }

    @Test
    fun testReadUntilString() {
        runBlocking {
            for (bufferSize in listOf(1, 2, 3, 4, 5, 6, 1024)) {
                testReadUntilString(bufferSize)
            }
        }
    }

    suspend fun testReadUntil(bufferSize: Int) {
        val bc = ByteChannel(true).apply {
            writeFully(bytes)
        }

        assertEquals(
            expectedList,
            (0 until 5).map { bc.readUntil(delimiter, bufferSize = bufferSize) }.map { it.toString(charset) },
            "Failed for bufferSize=$bufferSize"
        )
    }

    suspend fun testReadUntilString(bufferSize: Int) {
        val bc = ByteChannel(true).apply {
            writeFully(bytes)
        }

        assertEquals(
            expectedList,
            (0 until 5).map { bc.readUntilString(delimiter, bufferSize = bufferSize, charset = charset) },
            "Failed for bufferSize=$bufferSize"
        )
    }
}

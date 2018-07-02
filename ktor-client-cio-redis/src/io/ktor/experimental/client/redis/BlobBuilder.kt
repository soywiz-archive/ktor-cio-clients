package io.ktor.experimental.client.redis

import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.nio.*
import java.nio.ByteBuffer
import java.nio.charset.*

/**
 * Optimized class for allocation-free String/ByteArray building.
 *
 * Features:
 * - Allows to append numbers ([Int], [Long]), [String]s and [ByteArray]s without allocating
 * - Allows to get the original ByteArray containing the data without copying it
 * - Allows to get the current size of the builder
 * - Allows to reset it for reusing
 */
internal class BlobBuilder(size: Int, val charset: Charset) : ByteArrayOutputStream(size) {
    private val charsetEncoder = charset.newEncoder()
    private val tempCB = CharBuffer.allocate(1024)
    private val tempBB = ByteBuffer.allocate((tempCB.count() * charsetEncoder.maxBytesPerChar()).toInt())
    private val tempSB = StringBuilder(64)

    fun buf(): ByteArray = buf

    fun append(value: Long) = this.apply {
        tempSB.setLength(0)
        tempSB.append(value)
        tempCB.clear()
        tempSB.getChars(0, tempSB.length, tempCB.array(), 0)
        tempCB.position(tempSB.length)
        tempCB.flip()
        append(tempCB)
    }

    fun append(value: Int) = this.apply {
        when (value) {
            in 0..9 -> {
                write(('0' + value).toInt())
            }
            in 10..99 -> {
                write(('0' + (value / 10)).toInt())
                write(('0' + (value % 10)).toInt())
            }
            else -> {
                tempSB.setLength(0)
                tempSB.append(value)
                tempCB.clear()
                tempSB.getChars(0, tempSB.length, tempCB.array(), 0)
                tempCB.position(tempSB.length)
                tempCB.flip()
                append(tempCB)
            }
        }
    }

    fun append(char: Char): BlobBuilder {
        if (char.toInt() <= 0xFF) {
            write(char.toInt())
        } else {
            tempCB.clear()
            tempCB.put(char)
            tempCB.flip()
            append(tempCB)
        }

        return this
    }

    fun append(str: String) = this.apply {
        val len = str.length
        if (len == 0) return@apply

        val chunk = Math.min(len, 1024)

        for (n in 0 until len step chunk) {
            tempCB.clear()
            val cend = Math.min(len, n + chunk)
            str.toCharArray(tempCB.array(), 0, n, cend)
            tempCB.position(cend - n)
            tempCB.flip()
            append(tempCB)
        }
    }

    fun append(bb: ByteBuffer): BlobBuilder {
        while (bb.hasRemaining()) {
            write(bb.get().toInt())
        }

        return this
    }

    fun append(cb: CharBuffer): BlobBuilder {
        charsetEncoder.reset()

        while (cb.hasRemaining()) {
            tempBB.clear()
            charsetEncoder.encode(cb, tempBB, false)
            tempBB.flip()
            append(tempBB)
        }

        tempBB.clear()
        charsetEncoder.encode(cb, tempBB, true)
        tempBB.flip()
        append(tempBB)

        return this
    }

    fun append(that: BlobBuilder): BlobBuilder {
        write(that.buf(), 0, that.size())

        return this
    }

    override fun toString(): String = String(buf, 0, size(), charset)
}

internal suspend fun BlobBuilder.writeTo(channel: ByteWriteChannel) {
    channel.writeFully(buf(), 0, size())
}

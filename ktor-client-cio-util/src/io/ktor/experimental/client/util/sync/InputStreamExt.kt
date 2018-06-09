package io.ktor.experimental.client.util.sync

import java.io.*
import java.nio.charset.*

inline fun <T> ByteArray.read(callback: ByteArrayInputStream .() -> T): T = callback(openSync())

fun ByteArray.openSync(): ByteArrayInputStream = ByteArrayInputStream(this)

fun InputStream.readS8(): Int = readU8().toByte().toInt()

fun InputStream.readS16_be(): Int = readU16_be().toShort().toInt()
fun InputStream.readS24_be(): Int = ((readU24_be() shl 8) shr 8)
fun InputStream.readS32_be(): Int =
    (this.read() shl 24) or (this.read() shl 16) or (this.read() shl 8) or (this.read() shl 0)

fun InputStream.readU32_be(): Long = readS32_be().toLong() and 0xFFFFFFFFL
fun InputStream.readS64_be(): Long = (readU32_be() shl 32) or (readU32_be() shl 0)

fun InputStream.readS16_le(): Int = readU16_le().toShort().toInt()
fun InputStream.readS24_le(): Int = ((readU24_le() shl 8) shr 8)
fun InputStream.readS32_le(): Int =
    (this.read() shl 0) or (this.read() shl 8) or (this.read() shl 16) or (this.read() shl 24)

fun InputStream.readS64_le(): Long = (readU32_le() shl 0) or (readU32_le() shl 32)
fun InputStream.readU32_le(): Long = readS32_le().toLong() and 0xFFFFFFFFL

fun InputStream.readU8(): Int = this.read() and 0xFF
fun InputStream.readU16_be(): Int = (this.read() shl 8) or (this.read() shl 0)
fun InputStream.readU24_be(): Int = (this.read() shl 16) or (this.read() shl 8) or (this.read() shl 0)
fun InputStream.readU16_le(): Int = (this.read() shl 0) or (this.read() shl 8)
fun InputStream.readU24_le(): Int = (this.read() shl 0) or (this.read() shl 8) or (this.read() shl 16)

fun InputStream.readBytesExact(count: Int): ByteArray = ByteArray(count).also { read(it, 0, count) }
fun InputStream.readBytesAvailable(): ByteArray = readBytes(available())

fun InputStream.readString(count: Int, charset: Charset) = this.readBytesExact(count).toString(charset)
fun InputStream.readStringz(charset: Charset = Charsets.UTF_8): String {
    var out = ""
    while (true) {
        val c = read()
        if (c == -1 || c == 0) break
        out += c.toChar()
    }
    return out
}

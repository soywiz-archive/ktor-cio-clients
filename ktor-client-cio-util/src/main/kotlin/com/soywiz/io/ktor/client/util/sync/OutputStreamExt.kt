package com.soywiz.io.ktor.client.util.sync

import java.io.*
import java.nio.charset.*

inline fun MemorySyncStreamToByteArray(callback: ByteArrayOutputStream.() -> Unit): ByteArray {
    val bos = ByteArrayOutputStream()
    callback(bos)
    return bos.toByteArray()
}

fun OutputStream.write8(v: Int) {
    this.write(v)
}

fun OutputStream.write16_be(v: Int) {
    this.write((v ushr 8) and 0xFF)
    this.write((v ushr 0) and 0xFF)
}

fun OutputStream.write24_be(v: Int) {
    this.write((v ushr 16) and 0xFF)
    this.write((v ushr 8) and 0xFF)
    this.write((v ushr 0) and 0xFF)
}

fun OutputStream.write32_be(v: Int) {
    this.write((v ushr 24) and 0xFF)
    this.write((v ushr 16) and 0xFF)
    this.write((v ushr 8) and 0xFF)
    this.write((v ushr 0) and 0xFF)
}

fun OutputStream.write64_be(v: Long) {
    write32_be((v ushr 32).toInt())
    write32_be((v ushr 0).toInt())
}

fun OutputStream.write16_le(v: Int) {
    this.write((v ushr 0) and 0xFF)
    this.write((v ushr 8) and 0xFF)
}

fun OutputStream.write24_le(v: Int) {
    this.write((v ushr 0) and 0xFF)
    this.write((v ushr 8) and 0xFF)
    this.write((v ushr 16) and 0xFF)
}

fun OutputStream.write32_le(v: Int) {
    this.write((v ushr 0) and 0xFF)
    this.write((v ushr 8) and 0xFF)
    this.write((v ushr 16) and 0xFF)
    this.write((v ushr 24) and 0xFF)
}

fun OutputStream.write64_le(v: Long) {
    write32_le((v ushr 0).toInt())
    write32_le((v ushr 32).toInt())
}

fun OutputStream.writeBytes(bytes: ByteArray) {
    this.write(bytes)
}

fun OutputStream.writeStringz(str: String, charset: Charset = Charsets.UTF_8) {
    writeBytes(str.toByteArray(charset) + byteArrayOf(0))
}

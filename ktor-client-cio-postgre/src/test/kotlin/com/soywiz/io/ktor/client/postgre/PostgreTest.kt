package com.soywiz.io.ktor.client.postgre

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.junit.*

class PostgreTest {
    @Test
    fun name() {
        runBlocking {
            val bc = ByteChannel(true)
            bc.writePostgreStartup("hello")
            val packet = bc._readPostgrePacket(readType = false)
            println(packet.payload.toString(Charsets.ISO_8859_1))
            println(packet.payload.toList())
        }
    }
}


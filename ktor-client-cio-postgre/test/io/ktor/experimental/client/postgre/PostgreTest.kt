package io.ktor.experimental.client.postgre

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.junit.*
import kotlin.test.*

class PostgreTest {
    @Test
    fun testWritePostgreStartup() {
        runBlocking {
            for (useByteReadReadInt in listOf(true, false)) {
                val bc = ByteChannel(true)
                bc.writePostgreStartup("hello")
                val packet = bc._readPostgrePacket(
                    readType = false, config = PostgresConfig(
                        useByteReadReadInt = useByteReadReadInt
                    )
                )

                assertEquals(
                    "\u0000\u0003\u0000\u0000user\u0000hello\u0000database\u0000hello\u0000application_name\u0000ktor-cio\u0000client_encoding\u0000UTF8\u0000\u0000",
                    packet.payload.toString(Charsets.UTF_8)
                )
            }
        }
    }
}


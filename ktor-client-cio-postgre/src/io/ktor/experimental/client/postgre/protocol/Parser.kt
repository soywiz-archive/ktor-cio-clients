package io.ktor.experimental.client.postgre.protocol

import io.ktor.experimental.client.postgre.*
import io.ktor.experimental.client.util.*

sealed class ParseResult
object CONTINUE : ParseResult()
object END : ParseResult()
class Parameter(val key: String, val value: String) : ParseResult()
class Signal(val exception: PostgreException) : ParseResult()
class EXCEPTION(val exception: PostgreException) : ParseResult()

internal fun parsePacket(query: String, packet: PostgrePacket): ParseResult = when (packet.typeChar) {
    'R' -> {
        val type = packet.payload.readInt()
        when (type) {
            // AuthenticationOk
            0 -> CONTINUE
            // AuthenticationKerberosV5
            2 -> TODO("AuthenticationKerberosV5")
            // AuthenticationCleartextPassword
            3 -> TODO("AuthenticationCleartextPassword")
            // AuthenticationMD5Password
            5 -> {
                val salt = packet.payload.readInt()
                TODO("AuthenticationMD5Password")
            }
            // AuthenticationSCMCredential
            6 -> TODO("AuthenticationSCMCredential")
            // AuthenticationGSS
            7 -> TODO("AuthenticationGSS")
            // AuthenticationGSSContinue
            8 -> TODO("AuthenticationGSSContinue")
            // AuthenticationSSPI
            9 -> TODO("AuthenticationSSPI")
            else -> TODO("Authentication$type")
        }
    }
    'S' -> {
        packet.read {
            val key = readStringz()
            val value = readStringz()
            return@read Parameter(key, value)
        }
    }
    'K' -> {
        packet.read {
            val processId = readInt()
            val processSecretKey = readInt()
            TODO()
            //println("processId=$processId")
            //println("processSecretKey=$processSecretKey")
        }

    }
    'Z' -> {
        packet.read {
            val status = readByte().toChar()
            // 'I' if idle (not in a transaction block)
            // 'T' if in a transaction block
            // 'E' if in a failed transaction
        }
        END
    }
    'C' -> { // CommandComplete
        val command = packet.read {
            readStringz()
        }

        TODO()
        //println("CommandComplete: $command")
    }
    'E', 'N' -> {
        val parts = packet.read {
            mapWhile({ !endOfInput }) { readStringz() }
        }

        when (packet.typeChar) {
            'E' -> EXCEPTION(PostgreException(query, parts))
            'N' -> Signal(PostgreException(query, parts))
        }
        TODO()
    }
    else -> {
        println(packet)
        println(packet.payload.readText())
        CONTINUE
    }
}

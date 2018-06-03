package com.soywiz.io.ktor.client.ftp

import com.soywiz.io.ktor.client.util.*
import kotlinx.coroutines.experimental.*

object FtpSpike {
    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            // wine ftpdmin.exe .
            val client = FtpClient("127.0.0.1", port = 2100)
            client.apply {
                println("pwd=" + PWD())
                println(SIZE("/"))
                CWD("/")
                println(LIST("-l"))
                println(NLST())
                println(SIZE("/ftpdmin.exe"))
                println(STAT("/ftpdmin.exe"))
                //val res = RETR("/ftpdmin.exe", range = 0L until 10L)
                println(MDTM("/ftpdmin.exe"))
                val res = RETR("/ftpdmin.exe")
                println(res.hex)
                //STOU(byteArrayOf(1, 2, 3, 4))
                QUIT()
            }
        }
    }
}

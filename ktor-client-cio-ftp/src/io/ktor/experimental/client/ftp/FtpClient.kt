package io.ktor.experimental.client.ftp

import io.ktor.experimental.client.util.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import io.ktor.network.util.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*
import java.net.*

// https://www.ietf.org/rfc/rfc959.txt
// https://en.wikipedia.org/wiki/File_Transfer_Protocol
// http://slacksite.com/other/ftp.html
// http://www.nsftools.com/tips/RawFTP.htm
class FtpClient private constructor(val socket: Socket, val secure: Boolean, val logger: FtpLogger) {
    private val read = socket.openReadChannel()
    private val write = socket.openWriteChannel(autoFlush = true)

    companion object {
        private val PASV_REGEX = Regex("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)")

        suspend operator fun invoke(
            host: String,
            port: Int = 21,
            secure: Boolean = false,
            user: String = "",
            password: String = "",
            logger: FtpLogger = FtpLogger()
        ): FtpClient =
            FtpClient(
                aSocket(ActorSelectorManager(ioCoroutineDispatcher)).tcp().connect(InetSocketAddress(host, port)).optTls(secure), secure, logger
            ).apply {
                init(user, password)
            }
    }

    var motd = ""
    var server = "unknown"

    private suspend fun init(user: String, password: String) {
        motd = readResponse().rawMsg
        logger.logMotd(motd)
        USER(user)
        PASS(password)
        server = SYST()
        TYPE('I')
    }

    private suspend fun readResponse(raw: Boolean = false): FtpResponse {
        val res = read.readASCIILine() ?: ""
        if (raw) return FtpResponse(200, res)
        if (res.length < 3) error("Invalid FTP response line res='$res'")
        val code = res.substring(0, 3).toIntOrNull() ?: error("Invalid ftp response code res='$res'")
        val msg = res.substring(3).trimStart()
        return FtpResponse(code, msg)
    }


    private suspend fun CMD(req: FtpRequest, raw: Boolean = false): FtpResponse {
        logger.logRequest(req)
        write.writeStringUtf8("${req.fullCmd}\r\n")
        write.flush()
        val res = readResponse(raw = raw)
        logger.logRequestResponse(res, req)
        if (res.outcome == FtpResponse.Outcome.FAILURE) throw FtpException(
            res,
            req
        )
        return res
    }

    private suspend fun CMD(cmd: String, vararg args: String, raw: Boolean = false): FtpResponse =
        CMD(FtpRequest(cmd, *args), raw = raw)

    private suspend fun <T> DATA_CMD(
        cmd: String, vararg args: String,
        start: Long? = null,
        handler: suspend (socket: Socket) -> T
    ): T {
        val socketDataAddress = PASV()
        if (start != null) REST(start)
        val res = CMD(cmd, *args)
        aSocket(ActorSelectorManager(ioCoroutineDispatcher)).tcp().connect(socketDataAddress).optTls(secure).use { socketData ->
            try {
                val result = handler(socketData)
                println(readResponse())
                return result
            } catch (e: Throwable) {
                ABOR()
                throw e
            }
        }
    }

    private suspend fun DATA_CMD_READ_BYTES(cmd: String, vararg args: String, start: Long? = null): ByteArray =
        DATA_CMD(cmd, *args, start = start) { it.openReadChannel().readRemaining().readBytes() }

    suspend fun PASV(): InetSocketAddress {
        val res = CMD("PASV")
        val ep = PASV_REGEX.find(res.rawMsg)?.groupValues?.drop(1)?.map { it.toInt() } ?: error("Invalid PASV result")
        val port = (ep[4] shl 8) or (ep[5])
        return InetSocketAddress("${ep[0]}.${ep[1]}.${ep[2]}.${ep[3]}", port)
    }

    suspend fun <T> RETR(file: String, start: Long? = null, reader: suspend (brc: ByteReadChannel) -> T): T {
        return DATA_CMD("RETR", file, start = start) { reader(it.openReadChannel()) }
    }

    suspend fun RETR(file: String, range: LongRange? = null): ByteArray {
        return RETR(file, start = range?.start) {
            if (range != null) {
                it.readPacket(range.length.toInt()).readBytes()
            } else {
                it.readRemaining().readBytes()
            }
        }
    }

    suspend fun <T> APPE(file: String, start: Long? = null, writer: suspend (brc: ByteWriteChannel) -> T): T {
        return DATA_CMD("APPE", file, start = start) { writer(it.openWriteChannel(autoFlush = true)) }
    }

    suspend fun <T> STOR(file: String, start: Long? = null, writer: suspend (brc: ByteWriteChannel) -> T): T {
        return DATA_CMD("STOR", file, start = start) { writer(it.openWriteChannel(autoFlush = true)) }
    }

    suspend fun <T> STOU(writer: suspend (brc: ByteWriteChannel) -> T): T {
        return DATA_CMD("STOU") { writer(it.openWriteChannel(autoFlush = true)) }
    }

    suspend fun APPE(file: String, data: ByteArray, start: Long? = null) =
        APPE(file, start = start) { it.writeFully(data) }

    suspend fun STOR(file: String, data: ByteArray, start: Long? = null) =
        STOR(file, start = start) { it.writeFully(data) }

    suspend fun STOU(data: ByteArray, start: Long? = null) =
        STOU { it.writeFully(data) }

    private suspend fun ABOR() = CMD("ABOR")
    private suspend fun REST(value: Long) = CMD("REST", "$value")
    private suspend fun USER(user: String) = CMD("USER", user)
    private suspend fun PASS(pass: String) = CMD("PASS", pass)
    suspend fun LIST(vararg args: String): String = DATA_CMD_READ_BYTES("LIST", *args).toString(Charsets.UTF_8)
    suspend fun NLST(vararg args: String): String = DATA_CMD_READ_BYTES("NLST", *args).toString(Charsets.UTF_8)
    suspend fun PWD(): String = CMD("PWD").unquotedMsg
    suspend fun CWD(path: String): String = CMD("CWD", path).rawMsg
    suspend fun MKD(path: String) = CMD("MKD", path).rawMsg
    suspend fun CDUP(): String = CMD("CDUP").rawMsg
    suspend fun DELE(file: String): String = CMD("DELE", file).rawMsg
    suspend fun RMD(dir: String): String = CMD("RMD", dir).rawMsg
    suspend fun RENAME(src: String, dst: String): FtpResponse {
        CMD("RNFR", src)
        return CMD("RNTO", dst)
    }

    suspend fun HELP(cmd: String? = null): String = CMD("HELP", *listOfNotNull(cmd).toTypedArray()).rawMsg
    suspend fun SIZE(path: String): Long? = try {
        CMD("SIZE", path).rawMsg.toLongOrNull()
    } catch (e: FtpException) {
        0L
    }

    suspend fun STAT(path: String? = null): FtpResponse = CMD("STAT", *listOfNotNull(path).toTypedArray(), raw = true)

    private suspend fun PORT(addr: InetSocketAddress): FtpResponse {
        val a1 = addr.address.address[0].toInt() and 0xFF
        val a2 = addr.address.address[1].toInt() and 0xFF
        val a3 = addr.address.address[2].toInt() and 0xFF
        val a4 = addr.address.address[3].toInt() and 0xFF
        val p1 = (addr.port ushr 8) and 0xFF
        val p2 = (addr.port ushr 0) and 0xFF
        return CMD("PORT", "$a1,$a2,$a3,$a4,$p1,$p2")
    }

    suspend fun SITE(cmd: String): FtpResponse = CMD("SITE", cmd)
    suspend fun SYST(): String = CMD("SYST").rawMsg
    suspend fun NOOP(): String = CMD("NOOP").rawMsg
    data class FtpDate(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int, val second: Int)

    suspend fun MDTM(file: String): FtpDate {
        val format = CMD("MDTM", file).rawMsg
        var start = 0
        fun int(size: Int): Int {
            val s = start
            start += size
            return format.substring(s, s + size).toInt()
        }
        return FtpDate(int(4), int(2), int(2), int(2), int(2), int(2))
    }

    /**
     * Syntax: TYPE type-character [second-type-character]
     *
     * Sets the type of file to be transferred. type-character can be any of:
     * - A - ASCII text
     * - E - EBCDIC text
     * - I - image (binary data)
     * - L - local format
     *
     * For A and E, the second-type-character specifies how the text should be interpreted. It can be:
     * - N - Non-print (not destined for printing). This is the default if second-type-character is omitted.
     * - T - Telnet format control (<CR>, <FF>, etc.)
     * - C - ASA Carriage Control
     *
     * For L, the second-type-character specifies the number of bits per byte on the local system, and may not be omitted.
     */
    suspend fun TYPE(type: Char, secondType: Char? = null): String =
        CMD("TYPE", *listOfNotNull(type.toString(), secondType?.toString()).toTypedArray()).rawMsg

    /**
     * Syntax: MODE mode-character
     *
     * Sets the transfer mode to one of:
     * - S - Stream
     * - B - Block
     * - C - Compressed
     *
     * The default mode is Stream.
     */
    suspend fun MODE(mode: Char) =
        CMD("MODE", "$mode")

    /**
     * Syntax: STRU structure-character
     *
     * Sets the file structure for transfer to one of:
     * - F - File (no structure)
     * - R - Record structure
     * - P - Page structure
     *
     * The default structure is File.
     */
    suspend fun STRU(stru: Char) =
        CMD("STRU", "$stru")

    suspend fun QUIT() = CMD("QUIT")

    private suspend fun REIN() {
        CMD("REIN")
    }
}

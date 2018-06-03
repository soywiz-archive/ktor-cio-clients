package com.soywiz.io.ktor.client.ftp

data class FtpRequest(val cmd: String, val args: List<String>) {
    constructor(cmd: String, vararg args: String) : this(cmd, args.toList())

    val fullCmd = "$cmd ${args.joinToString(" ")}"
}

data class FtpResponse(val code: Int, val rawMsg: String) {
    val outcomeInt get() = ((code / 100) % 10)
    val kindInt get() = ((code / 10) % 10)
    val detailInt get() = ((code / 1) % 10)

    val unquotedMsg by lazy {
        if (rawMsg.startsWith('"')) {
            var out = ""
            for (n in 1 until rawMsg.length) {
                val c = rawMsg[n]
                if (c == '"') {
                    if (rawMsg.getOrNull(n + 1) == '"') {
                        out += '"'
                        continue
                    } else {
                        break
                    }
                } else {
                    out += c
                }
            }
            out
        } else {
            rawMsg
        }
    }

    enum class Outcome(vararg val ids: Int) {
        SUCCESS(2), FAILURE(4, 5), ERROR_INCOMPLETE(1, 3);

        companion object {
            val BY_ID = LinkedHashMap<Int, Outcome>().apply {
                for (v in Outcome.values()) {
                    for (id in v.ids) this[id] = v
                }
            }
        }
    }

    enum class Kind(val id: Int) {
        SYNTAX(0), INFO(1), CONNECTION(2), AUTH(3), UNDEF(4), FILES(5);

        companion object {
            val BY_ID = values().associateBy { it.id }
        }
    }

    val outcome = Outcome.BY_ID[outcomeInt] ?: Outcome.FAILURE
    val kind = Kind.BY_ID[kindInt] ?: Kind.UNDEF
}

open class FtpException(val response: FtpResponse, val request: FtpRequest) :
    RuntimeException("$request : $response")

open class FtpLogger {
    open fun logMotd(motd: String) {
    }

    open fun logRequest(req: FtpRequest) {
    }

    open fun logRequestResponse(res: FtpResponse, req: FtpRequest) {
    }
}
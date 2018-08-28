package io.ktor.experimental.client.postgre.scheme

import io.ktor.experimental.client.db.*

class PostgreRawResponse(
    val info: String,
    val notice: DBNotice?,
    val columns: List<PostgreColumn>?,
    val rows: List<List<ByteArray?>>
) {
    override fun toString(): String = info
}


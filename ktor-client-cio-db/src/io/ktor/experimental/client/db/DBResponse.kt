package io.ktor.experimental.client.db

class DBResponse(
    val info: String,
    val rows: DBRowSet,
    val notice: DBNotice? = null
) {
    override fun toString(): String = buildString {
        append("[DBResponse]: $info")
        notice?.let { appendln(); append(it) }
    }
}

class DBNotice(val text: String, cause: Throwable? = null) {
    override fun toString(): String = "[Notice]: $text"
}

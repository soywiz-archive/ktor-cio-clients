package io.ktor.experimental.client.postgre.stats

import io.ktor.experimental.client.postgre.*
import java.util.*

class PostgreStats internal constructor(private val client: InternalPostgreClient) : PostgreStatsMBean {
    override val connected get() = client.connection != null
    override val preconnectionTime: Date? get() = client.preconnectionTime
    override val connectionTime: Date? get() = client.connectionTime
    override val params get() = client.params
    override val errorCount get() = client.errorCount
    override val queryStarted: Int get() = client.queryStarted
    override val queryCompleted: Int get() = client.queryCompleted
}
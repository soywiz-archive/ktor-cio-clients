package io.ktor.experimental.client.postgre.stats

import java.util.*

interface PostgreStatsMBean {
    val connected: Boolean
    val preconnectionTime: Date?
    val connectionTime: Date?
    val params: Map<String, String>
    val errorCount: Int
    val queryStarted: Int
    val queryCompleted: Int
}
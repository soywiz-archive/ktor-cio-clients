package io.ktor.experimental.client.cassandra.db

import io.ktor.experimental.client.cassandra.*
import io.ktor.experimental.client.util.*

data class Channel(val id: Int) {
    val data = ProduceConsumer<Packet>()
}
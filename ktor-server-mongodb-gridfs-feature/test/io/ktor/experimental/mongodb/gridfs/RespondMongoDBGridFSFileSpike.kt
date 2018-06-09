package io.ktor.experimental.mongodb.gridfs

import io.ktor.application.*
import io.ktor.experimental.client.mongodb.*
import io.ktor.experimental.client.mongodb.gridfs.*
import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

object RespondMongoDBGridFSFileSpike {
    @JvmStatic
    fun main(args: Array<String>) {
        embeddedServer(Netty, port = 8080) {
            val mongo = MongoDB()
            val db = mongo["test"]
            val grid = db.gridFs()
            install(PartialContent)
            routing {
                get("/{name}") {
                    val name = call.parameters["name"] ?: error("name not specified")
                    call.respondMongoDBGridFSFile(grid, name)
                }
            }
        }.start(wait = true)
    }
}

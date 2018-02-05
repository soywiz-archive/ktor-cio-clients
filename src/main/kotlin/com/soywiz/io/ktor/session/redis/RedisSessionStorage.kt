package com.soywiz.io.ktor.session.redis

import com.soywiz.io.ktor.client.redis.*
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.sessions.*
import kotlinx.coroutines.experimental.io.ByteReadChannel
import kotlinx.coroutines.experimental.io.ByteWriteChannel
import kotlinx.coroutines.experimental.io.reader
import java.io.ByteArrayOutputStream
import java.io.Closeable

// @TODO: TTL + Strategy
class RedisSessionStorage(val redis: Redis, val prefix: String = "session_", val ttlSeconds: Int = 3600) :
    SessionStorage, Closeable {
    private fun buildKey(id: String) = "$prefix$id"

    override suspend fun invalidate(id: String) {
        redis.del(buildKey(id))
    }

    override suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R {
        return consumer(ByteReadChannel((redis.get(buildKey(id)) ?: "").hex))
    }

    override suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit) {
        return provider(reader(getCoroutineContext(), autoFlush = true) {
            val data = ByteArrayOutputStream()
            val temp = ByteArray(1024)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(temp)
                if (read <= 0) break
                data.write(temp, 0, read)
            }
            redis.set(buildKey(id), data.toByteArray().hex)
        }.channel)
    }

    override fun close() {
    }
}

internal object RedisSessionStorageSpike {
    data class TestSession(val visits: Int = 0)

    @JvmStatic fun main(args: Array<String>) {
        val redis = Redis()

        embeddedServer(Netty, 8080) {
            install(Sessions) {
                val cookieName = "SESSION2"
                val sessionStorage = RedisSessionStorage(redis)
                cookie<TestSession>(cookieName, sessionStorage)
                //header<TestUserSession>(cookieName, sessionStorage) {
                //    transform(SessionTransportTransformerDigest())
                //}
            }
            routing {
                get("/") {
                    val ses = call.sessions.get<TestSession>() ?: TestSession()
                    call.sessions.set(TestSession(ses.visits + 1))
                    call.respondText("hello: " + ses)
                }
                get("/set") {
                    val ses = call.sessions.get<TestSession>() ?: TestSession()
                    call.sessions.set(TestSession(ses.visits + 1))
                    call.respondText("ok")
                }
                get("/get") {
                    call.respondText("ok: " + call.sessions.get<TestSession>())
                }
            }
        }.apply {
            start(wait = true)
        }
    }
}

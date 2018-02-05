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

// @TODO: Ask: Could this be the default interface since Sessions are going to be small
// @TODO: Ask: and most of the time (de)serialized in-memory
abstract class SimplifiedSessionStorage : SessionStorage {
    abstract suspend fun delete(id: String): Unit
    abstract suspend fun read(id: String): ByteArray?
    abstract suspend fun write(id: String, data: ByteArray): Unit

    override suspend fun invalidate(id: String) {
        delete(id)
    }

    override suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R {
        val data = read(id) ?: throw NoSuchElementException("Session $id not found")
        return consumer(ByteReadChannel(data))
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
            write(id, data.toByteArray())
        }.channel)
    }
}

class RedisSessionStorage(val redis: Redis, val prefix: String = "session_", val ttlSeconds: Int = 3600) :
    SimplifiedSessionStorage() {
    private fun buildKey(id: String) = "$prefix$id"

    override suspend fun delete(id: String) {
        redis.del(buildKey(id))
    }

    override suspend fun read(id: String): ByteArray? {
        val key = buildKey(id)
        return redis.get(key)?.hex?.apply {
            redis.expire(key, ttlSeconds)
        }
    }

    override suspend fun write(id: String, data: ByteArray) {
        val key = buildKey(id)
        redis.set(key, data.hex)
        redis.expire(key, ttlSeconds)
    }
}

internal object RedisSessionStorageSpike {
    data class TestSession(val visits: Int = 0)

    @JvmStatic
    fun main(args: Array<String>) {
        val redis = Redis()

        embeddedServer(Netty, 8080) {
            install(Sessions) {
                val cookieName = "SESSION4"
                val sessionStorage = RedisSessionStorage(redis, ttlSeconds = 10)
                cookie<TestSession>(cookieName, sessionStorage)
                //header<TestUserSession>(cookieName, sessionStorage) {
                //    transform(SessionTransportTransformerDigest())
                //}
            }
            routing {
                get("/") {
                    val ses = call.sessions.getOrNull<TestSession>() ?: TestSession()
                    call.sessions.set(TestSession(ses.visits + 1))
                    call.respondText("hello: " + ses)
                }
                get("/set") {
                    val ses = call.sessions.getOrNull<TestSession>() ?: TestSession()
                    call.sessions.set(TestSession(ses.visits + 1))
                    call.respondText("ok")
                }
                get("/get") {
                    //call.respondText("ok: " + call.sessions.getOrNull<TestSession>())
                    call.respondText("ok")
                }
            }
        }.apply {
            start(wait = true)
        }
    }
}

inline fun <reified T> CurrentSession.getOrNull(): T? = try {
    get(findName(T::class)) as T?
} catch (e: NoSuchElementException) {
    null
}
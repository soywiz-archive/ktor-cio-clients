package io.ktor.experimental.sessions.redis

import io.ktor.application.*
import io.ktor.experimental.client.redis.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import io.ktor.sessions.*
import org.junit.*

class RedisSessionStorageIntegrationTest {
    @Test
    fun simple() = withTestApplication(Application::testModule) {
        var cookie = ""

        with(handleRequest(HttpMethod.Get, "/")) {
            Assert.assertEquals(HttpStatusCode.OK, response.status())
            Assert.assertEquals("hello: TestSession(visits=0)", response.content)
            cookie = response.cookies["SESSION"]!!.value
        }

        with(handleRequest(HttpMethod.Get, "/") {
            addHeader("Cookie", "SESSION=$cookie")
        }) {
            Assert.assertEquals(HttpStatusCode.OK, response.status())
            Assert.assertEquals("hello: TestSession(visits=1)", response.content)
        }
    }
}

private fun Application.testModule() {
    val redis = Redis()

    data class TestSession(val visits: Int = 0)

    install(Sessions) {
        val cookieName = "SESSION"
        val sessionStorage = RedisSessionStorage(redis, ttlSeconds = 10)
        cookie<TestSession>(cookieName, sessionStorage)
        //header<TestUserSession>(cookieName, sessionStorage) {
        //    transform(SessionTransportTransformerDigest())
        //}
    }
    routing {
        get("/") {
            val ses =
                call.sessions.getOrNull<TestSession>() ?: TestSession()
            call.sessions.set(TestSession(ses.visits + 1))
            call.respondText("hello: $ses")
        }
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
                    call.respondText("hello: $ses")
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

package com.soywiz.io.ktor.sessions.redis

import com.soywiz.io.ktor.client.redis.Redis
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import org.junit.Assert
import org.junit.Test

class RedisSessionStorageIntegrationTest {
    @Test
    fun `simple`() = withTestApplication(Application::testModule) {
        var cookie = ""

        with(handleRequest(HttpMethod.Get, "/")) {
            Assert.assertEquals(HttpStatusCode.OK, response.status())
            Assert.assertEquals("hello: TestSession(visits=0)", response.content)
            cookie = response.cookies["SESSION"]!!.value
        }

        //with(handleRequest(HttpMethod.Get, "/") {
        //    addHeader("SESSION", cookie) // @TODO: This doesn't seems to work
        //}) {
        //    Assert.assertEquals(HttpStatusCode.OK, response.status())
        //    Assert.assertEquals("Hello, world!", response.content)
        //}
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
            call.respondText("hello: " + ses)
        }
    }
}

import io.ktor.application.*
import io.ktor.experimental.circuitbreaker.*
import io.ktor.experimental.client.redis.*
import io.ktor.features.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.*

class CircuitBreakerTest {
    companion object Spike {
        val redis = Redis()

        val REDIS_SERVICE = CircuitBreaker.Service("redis", timeout = 5, timeoutUnit = TimeUnit.SECONDS) {
            // Verify that the service is alive by returning true and not throwing exceptions.
            // We can also introspect monitoring services, number of connections, etc.
            redis.get("/") // It would fail if redis service is not available.
            true
        }

        val io.ktor.application.ApplicationCall.redis: Redis get() = object : Redis {
            override suspend fun commandAny(vararg args: Any?): Any? = circuitBreaker.wrap(REDIS_SERVICE) {
                this@Spike.redis.commandAny(args)
            }
        }

        val PipelineContext<Unit, ApplicationCall>.redis get() = this.call.redis

        @JvmStatic fun main(args: Array<String>) {
            embeddedServer(Netty, port = 8080) {
                install(CircuitBreaker) {
                    register(REDIS_SERVICE)
                }
                install(StatusPages) {
                    exception<TimeoutCancellationException> {
                        call.respond("Timeout!")
                    }
                }

                routing {
                    get("/") {
                        val newValue = redis.hincrby("myhash", "mykey", 1L)
                        call.respondText("OK:$newValue")
                    }
                    routeTimeout(1, TimeUnit.SECONDS) {
                        get("/timeout") {
                            delay(2, TimeUnit.SECONDS)
                            call.respondText("OK")
                        }
                    }
                }
            }.start(wait = true)
        }
    }
}

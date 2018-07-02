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
        val RedisService: CircuitBreaker.Service<Redis> = CircuitBreaker.Service(
            "redis",
            Redis(maxConnections = 1),
            timeout = 5,
            timeoutUnit = TimeUnit.SECONDS
        ) { redis ->
            // Verify that the service is alive by returning true and not throwing exceptions.
            // We can also introspect monitoring services, number of connections, etc.
            redis.get("/") // It would fail if redis service is not available.
            true
        }

        val PipelineContext<Unit, ApplicationCall>.redisWrapped: Redis
            get() {
                return object : Redis {
                    override suspend fun capturePipes(): Redis.Pipes = TODO()

                    override suspend fun commandAny(vararg args: Any?): Any? = withService(RedisService) { service ->
                        service.commandAny(*args)
                    }
                }
            }

        @JvmStatic fun main(args: Array<String>) {
            embeddedServer(Netty, port = 8080) {
                install(StatusPages) {
                    exception<TimeoutCancellationException> {
                        call.respond("Timeout!")
                    }
                    exception<ServiceNotAvailableException> {
                        call.respond("Service ${it.service.name} is not available at this point! We are working on it, try again in a few seconds!")
                    }
                }

                routing {
                    get("/") {
                        // Automatically wrapped
                        val newValue = redisWrapped.hincrby("myhash", "mykey", 1L)
                        call.respondText("OK:$newValue")
                    }
                    get("/inline") {
                        // Manual wrapping
                        val newValue = withService(RedisService) { redis ->
                            redis.hincrby("myhash", "mykey", 1L)
                        }
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

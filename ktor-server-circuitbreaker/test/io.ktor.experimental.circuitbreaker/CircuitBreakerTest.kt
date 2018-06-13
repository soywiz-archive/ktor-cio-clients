import io.ktor.application.*
import io.ktor.experimental.circuitbreaker.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.*

class CircuitBreakerTest {
    companion object Spike {
        @JvmStatic fun main(args: Array<String>) {
            embeddedServer(Netty, port = 8080) {
                routing {
                    routeTimeout(4, TimeUnit.SECONDS) {
                        get("/") {
                            delay(3000L)
                            call.respondText("OK")
                        }
                    }
                }
            }.start(wait = true)
        }
    }
}
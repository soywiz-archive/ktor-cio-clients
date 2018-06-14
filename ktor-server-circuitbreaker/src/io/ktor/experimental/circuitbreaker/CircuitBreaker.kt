package io.ktor.experimental.circuitbreaker

import io.ktor.application.*
import io.ktor.pipeline.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.*

class CircuitBreaker private constructor(pipeline: ApplicationCallPipeline) {
    val config: Configuration = Configuration()

    /**
     * Defines a service with a [name] and a [checkStatus] block that checks the status of the service.
     * If the service is working and available, [checkStatus] should return true. Returning false or throwing
     * an exception will be interpreted as a non-working service.
     * [checkStatus] is a suspended block that will be called only once at a time.
     *
     * [timeout] would
     */
    class Service<T>(
        val name: String,
        val instance: T,
        val timeout: Long,
        val timeoutUnit: TimeUnit,
        val checkStatus: suspend (T) -> Boolean
    ) {
        var isAvailable = true; private set

        fun markAsUnavailable() {
            synchronized(this) {
                if (isAvailable) {
                    isAvailable = false
                    launch {
                        while (true) {
                            delay(timeout)
                            if (try {
                                    checkStatus(instance)
                                } catch (e: Throwable) {
                                    e.printStackTrace(); false
                                }
                            ) {
                                isAvailable = true
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    class Configuration

    suspend fun <T, T2> wrap(service: Service<T2>, block: suspend (T2) -> T): T {
        if (!service.isAvailable) {
            throw ServiceNotAvailableException(service)
        }
        return try {
            withTimeout(service.timeout, service.timeoutUnit) {
                block(service.instance)
            }
        } catch (e: TimeoutCancellationException) {
            service.markAsUnavailable()
            throw ServiceNotAvailableException(service)
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, CircuitBreaker> {
        override val key = AttributeKey<CircuitBreaker>("CircuitBreaker")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): CircuitBreaker {
            return CircuitBreaker(pipeline).apply {
                config.configure()
            }
        }
    }
}

class ServiceNotAvailableException(val service: CircuitBreaker.Service<*>) :
    RuntimeException("Service ${service.name} not available")

val Application.circuitBreaker: CircuitBreaker get() = this.featureOrNull(CircuitBreaker) ?: this.install(CircuitBreaker)
val ApplicationCall.circuitBreaker: CircuitBreaker get() = this.application.feature(CircuitBreaker)
val PipelineContext<Unit, ApplicationCall>.circuitBreaker: CircuitBreaker
    get() = this.application.feature(
        CircuitBreaker
    )

suspend fun <T, T2> PipelineContext<Unit, ApplicationCall>.withService(
    service: CircuitBreaker.Service<T2>,
    callback: suspend (T2) -> T
) = this.circuitBreaker.wrap(service, callback)

fun Route.routeTimeout(time: Long, unit: TimeUnit = TimeUnit.SECONDS, callback: Route.() -> Unit): Route {
    val routeWithTimeout = createChild(object : RouteSelector(1.0) {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
            RouteSelectorEvaluation.Constant
    })

    routeWithTimeout.intercept(ApplicationCallPipeline.Infrastructure) {
        withTimeout(time, unit) {
            proceed()
        }
    }

    return routeWithTimeout.apply(callback)
}

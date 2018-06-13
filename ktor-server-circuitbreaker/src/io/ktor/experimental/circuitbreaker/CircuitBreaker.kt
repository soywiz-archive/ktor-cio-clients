package io.ktor.experimental.circuitbreaker

import io.ktor.application.*
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
    class Service(
        val name: String,
        val timeout: Long = 15,
        val timeoutUnit: TimeUnit = TimeUnit.SECONDS,
        val checkStatus: suspend () -> Boolean
    )

    class Configuration {
        internal val services = LinkedHashMap<String, Service>()

        /**
         * Registers a CircuitBreaker [Service].
         */
        fun register(service: Service) {
            services[service.name] = service
        }
    }

    class ServiceStatus(val service: Service) {
        var isAvailable = true; private set

        fun markAsUnavailable() {
            synchronized(this) {
                if (isAvailable) {
                    isAvailable = false
                    launch {
                        while (true) {
                            delay(service.timeout)
                            if (try { service.checkStatus() } catch (e: Throwable) { e.printStackTrace(); false }) {
                                isAvailable = true
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    val serviceStatusMap = LinkedHashMap<Service, ServiceStatus>()
    val Service.status: ServiceStatus get() = serviceStatusMap.getOrPut(this) { ServiceStatus(this) }

    suspend fun <T> wrap(service: Service, block: suspend () -> T): T {
        val serviceStatus = service.status
        if (!serviceStatus.isAvailable) {
            throw ServiceNotAvailableException(service)
        }
        return try {
            withTimeout(service.timeout, service.timeoutUnit) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            serviceStatus.markAsUnavailable()
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

class ServiceNotAvailableException(val service: CircuitBreaker.Service) :
    RuntimeException("Service ${service.name} not available")

val ApplicationCall.circuitBreaker: CircuitBreaker get() = this.application.feature(CircuitBreaker)

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

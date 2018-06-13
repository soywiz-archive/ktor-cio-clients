package io.ktor.experimental.circuitbreaker

import io.ktor.application.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.*

class CircuitBreaker private constructor(config: Configuration, pipeline: ApplicationCallPipeline) {
    class Configuration {
    }

    init {
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, CircuitBreaker> {
        override val key = AttributeKey<CircuitBreaker>("CORS")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): CircuitBreaker {
            return CircuitBreaker(Configuration().apply(configure), pipeline)
        }
    }
}

fun Route.routeTimeout(time: Long, unit: TimeUnit = TimeUnit.SECONDS, callback: Route.() -> Unit): Route {
    val routeWithTimeout = createChild(object : RouteSelector(1.0) {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
            return RouteSelectorEvaluation.Constant
        }
    })

    routeWithTimeout.intercept(ApplicationCallPipeline.Infrastructure) {
        withTimeout(time, unit) {
            proceed()
        }
    }

    return routeWithTimeout.apply(callback)
}

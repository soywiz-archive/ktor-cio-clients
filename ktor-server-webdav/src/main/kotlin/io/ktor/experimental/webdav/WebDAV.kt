package io.ktor.experimental.webdav

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.dataformat.xml.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import java.io.*

open class FileWebDavFilesystem(val root: File) : WebDavFilesystem() {
    private fun file(path: String) = File(root, path) // @TODO: @SECURITY: Check .. to access outside

    override fun copy(src: String, dst: String): Unit = run { file(src).copyRecursively(file(dst)) }
    override fun move(src: String, dst: String): Unit = run { file(src).renameTo(file(dst)) }
}

open class WebDavFilesystem {
    open fun copy(src: String, dst: String) {
    }

    open fun move(src: String, dst: String) {
    }
}

fun Route.webdav(fs: WebDavFilesystem) {
    application.install(ContentNegotiation) {
        jacksonXml {
        }
    }
    //this.selector
    route("{path...}") {
        handle {
            val fullPath = call.parameters.getAll("path")
            val request = call.request
            val uri = request.uri
            val path = request.path()
            val headers = call.request.headers
            val method = request.httpMethod

            println("REQ[$method]: path=$path, fullPath=$fullPath, uri=$uri")

            when (request.httpMethod) {
                HttpMethod.Head -> {
                    call.respondText(path)
                }
                HttpMethod.Get -> {
                    call.respondText(path)
                }
                // https://tools.ietf.org/html/rfc2518#section-8.9
                HttpMethod.Move -> {
                    val depth = headers[HttpHeaders.Depth]
                    val destination = headers[HttpHeaders.Destination] ?: error("Destination not provided")
                    val overwriteS = headers[HttpHeaders.Overwrite] ?: "T"
                    val overwrite = if (overwriteS == "T") true else if (overwriteS == "F") false else error("Invalid Overwrite")
                    call.respondText("Move")
                }
            }
        }
    }
}

val HttpMethod.Companion.PropFind get() = WebDavHttpMethod.PropFind
val HttpMethod.Companion.PropPatch get() = WebDavHttpMethod.PropPatch
val HttpMethod.Companion.Mkcol get() = WebDavHttpMethod.Mkcol
val HttpMethod.Companion.Copy get() = WebDavHttpMethod.Copy
val HttpMethod.Companion.Move get() = WebDavHttpMethod.Move
val HttpMethod.Companion.Lock get() = WebDavHttpMethod.Lock
val HttpMethod.Companion.Unlock get() = WebDavHttpMethod.Unlock

object WebDavHttpMethod {
    val PropFind = HttpMethod("PROPFIND")
    val PropPatch = HttpMethod("PROPPATCH")
    val Mkcol = HttpMethod("MKCOL")
    val Copy = HttpMethod("COPY")
    val Move = HttpMethod("MOVE")
    val Lock = HttpMethod("LOCK")
    val Unlock = HttpMethod("UNLOCK")
}

typealias ApplicationCallPipelineInterceptor = PipelineInterceptor<Unit, ApplicationCall>

fun Route.methodHandle(method: HttpMethod, handler: ApplicationCallPipelineInterceptor): Route {
    return method(method) {
        handle(handler)
    }
}

@ContextDsl fun Route.propFind(body: ApplicationCallPipelineInterceptor) = methodHandle(HttpMethod.PropFind, body)
@ContextDsl fun Route.propPatch(body: ApplicationCallPipelineInterceptor) = methodHandle(HttpMethod.PropPatch, body)
@ContextDsl fun Route.mkCol(body: ApplicationCallPipelineInterceptor) = methodHandle(HttpMethod.Mkcol, body)
@ContextDsl fun Route.copy(body: ApplicationCallPipelineInterceptor) = methodHandle(HttpMethod.Copy, body)
@ContextDsl fun Route.move(body: ApplicationCallPipelineInterceptor) = methodHandle(HttpMethod.Move, body)
@ContextDsl fun Route.lock(body: ApplicationCallPipelineInterceptor) = methodHandle(HttpMethod.Lock, body)
@ContextDsl fun Route.unlock(body: ApplicationCallPipelineInterceptor) = methodHandle(HttpMethod.Unlock, body)

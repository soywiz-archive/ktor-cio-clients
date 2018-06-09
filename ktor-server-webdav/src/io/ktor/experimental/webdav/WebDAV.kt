package io.ktor.experimental.webdav

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.dataformat.xml.*
import com.fasterxml.jackson.dataformat.xml.ser.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.experimental.client.util.*
import io.ktor.http.*
import io.ktor.network.util.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.net.*
import java.text.*
import java.util.*

fun Route.webdav(path: String, root: File) = route(path) { webdav(FileWebDavFilesystem(root)) }
fun Route.webdav(path: String, fs: WebDavFilesystem) = route(path) { webdav(fs) }

/**
 * Acts similar to `Route.static`, but handles WebDAV methods.
 */
fun Route.webdav(root: File) = webdav(FileWebDavFilesystem(root))

/**
 * Acts similar to `Route.static`, with a virtual DAV filesystem
 */
fun Route.webdav(fs: WebDavFilesystem) {
    //this.selector
    route("{path...}") {
        handle {
            val fullPathParts = call.parameters.getAll("path")
            val request = call.request
            val uri = request.uri
            val path = request.path()
            val headers = call.request.headers
            val method = request.httpMethod
            val scheme = request.local.scheme
            val host = request.local.uri
            val port = request.local.port
            val fullPath = fullPathParts?.joinToString("/") ?: "/"
            //val result = try { call.receive<Map<*, *>>() } catch (e: Throwable) { mapOf<String, Any>() }
            //val resultStr = ""
            //val resultData = try { call.receive<Map<*, *>>() } catch (e: Throwable) { mapOf<String, Any>() }
            val currentUrl = URI("$scheme://$host:$port$uri")

            println("REQ[$method]: path=$path, fullPath=$fullPathParts, uri=$uri, currentUrl=$currentUrl")

            when (method) {
            // https://tools.ietf.org/html/rfc2518#section-8.1
                HttpMethod.PropFind -> {
                    val props = Dynamic { call.readXmlAsMap()["prop"].keys.strList }

                    //println(props)

                    val pathRTrim = path.trimEnd('/')

                    val rootProps = fs.propfind(path)
                    val extraProps = if (rootProps[WebDavProps.resourcetype] == ResourceTypeCollection) {
                        fs.list(path).map { "$pathRTrim/$it" to fs.propfind("$pathRTrim/$it") }
                    } else {
                        listOf()
                    }

                    val result = xml("D:multistatus", "xmlns:D" to "DAV:") {
                        renderResponse(path, props, rootProps)
                        for ((rpath, rprops) in extraProps) {
                            renderResponse(rpath, props, rprops)
                        }
                    }

                    //println("RESP: $result")

                    call.respondText(
                        result.toString(),
                        ContentType.Application.Xml,
                        status = WebDavHttpStatusCode.MultiStatus
                    )
                }
            // https://tools.ietf.org/html/rfc2518#section-8.2
                HttpMethod.PropPatch -> {
                    // @TODO: PROPPATCH not implemented!
                    call.respondText("Method Not Allowed", status = HttpStatusCode.MethodNotAllowed)

                }
            // https://tools.ietf.org/html/rfc2518#section-8.3
                HttpMethod.Mkcol -> {
                    fs.mkdir(fullPath)
                    call.respondText("", status = HttpStatusCode.Created)
                }
            // https://tools.ietf.org/html/rfc2518#section-8.4
                HttpMethod.Get, HttpMethod.Head -> {
                    val rprops = fs.propfind(path)
                    val result =
                        if (rprops[WebDavProps.resourcetype] == ResourceTypeCollection) NoContent else fs.read(path)
                    call.respond(if (method == HttpMethod.Get) result else NoContent)
                }
            // https://tools.ietf.org/html/rfc2518#section-8.5
                HttpMethod.Post -> {
                    call.respondText("Method Not Allowed", status = HttpStatusCode.MethodNotAllowed)
                }
            // https://tools.ietf.org/html/rfc2518#section-8.6
                HttpMethod.Delete -> {
                    fs.delete(fullPath)
                    call.respondText("", status = HttpStatusCode.NoContent)
                }
            // https://tools.ietf.org/html/rfc2518#section-8.7
                HttpMethod.Put -> {
                    fs.write(fullPath, call.receiveChannel())
                    call.respondText("", status = HttpStatusCode.Created)
                }
            // https://tools.ietf.org/html/rfc2518#section-8.8
            // https://tools.ietf.org/html/rfc2518#section-8.9
                HttpMethod.Copy, HttpMethod.Move -> {
                    val depth = headers[HttpHeaders.Depth]
                    val destinationUri = URI(headers[HttpHeaders.Destination] ?: error("Destination not provided"))
                    val overwriteS = headers[HttpHeaders.Overwrite] ?: "T"
                    val overwrite =
                        if (overwriteS == "T") true else if (overwriteS == "F") false else error("Invalid Overwrite")
                    val destination = destinationUri.path
                    val destination2 = currentUrl.relativize(destinationUri)

                    //println("MOVE: depth=$depth, source=$fullPath, destinationUrl=$destinationUri, destination=$destination, destination2=$destination2, overwrite=$overwrite")
                    if (method == HttpMethod.Copy) {
                        fs.copy(fullPath, destination)
                    } else {
                        fs.move(fullPath, destination, overwrite = overwrite)
                    }

                    call.response.header("Location", destinationUri.toString())
                    call.respondText("", status = HttpStatusCode.Created)
                }
            // https://tools.ietf.org/html/rfc2518#section-8.10
            // https://tools.ietf.org/html/rfc2518#section-8.11
                HttpMethod.Lock, HttpMethod.Unlock -> {
                    // @TODO: LOCK & UNLOCK not implemented!
                    call.respondText("Method Not Allowed", status = HttpStatusCode.MethodNotAllowed)
                }
                else -> {
                    call.respondText("Method Not Allowed", status = HttpStatusCode.MethodNotAllowed)
                }
            }
        }
    }
}

object WebDavProps {
    val displayname = "displayname"
    val getlastmodified = "getlastmodified"
    val lastmodified = "lastmodified"
    val resourcetype = "resourcetype"
    val getcontentlength = "getcontentlength"
    val getcontenttype = "getcontenttype"
    val getetag = "getetag"
}

open class FileWebDavFilesystem(val root: File) : WebDavFilesystem() {
    private fun file(path: String): File {
        val child = File(root, path)
        if (!child.absolutePath.startsWith(root.absolutePath)) error("Path outside folder!")
        return child
    }

    override suspend fun list(path: String): List<String> {
        return withContext(ioCoroutineDispatcher) { file(path).list().toList() }
    }

    override suspend fun propfind(path: String): Map<String, Any?> {
        val file = file(path)
        return when {
            file.isDirectory -> mapOf(
                WebDavProps.displayname to null,
                WebDavProps.getlastmodified to Date(),
                WebDavProps.resourcetype to ResourceTypeCollection
            )
            file.exists() -> mapOf(
                WebDavProps.displayname to file.name,
                WebDavProps.getcontentlength to file.length(),
                WebDavProps.getcontenttype to ContentType.defaultForFilePath(file.name).toString(),
                //WebDavProps.getetag to file,
                WebDavProps.getlastmodified to file.lastModified(),
                WebDavProps.resourcetype to null
            )
            else -> mapOf()
        }
    }

    override suspend fun read(path: String): OutgoingContent {
        val file = file(path)
        if (file.isDirectory) {
            error("Directory")
        } else {
            return LocalFileContent(file)
        }
    }

    override suspend fun write(path: String, channel: ByteReadChannel) {
        val file = file(path)
        withContext(ioCoroutineDispatcher) {
            channel.copyAndClose(file.writeChannel())
        }
    }

    override suspend fun mkdir(path: String) {
        val file = file(path)
        file.mkdirs()
    }

    override suspend fun delete(path: String) {
        val file = file(path)
        file.deleteRecursively()
    }

    override suspend fun copy(src: String, dst: String): Unit = run { file(src).copyRecursively(file(dst)) }
    override suspend fun move(src: String, dst: String, overwrite: Boolean): Unit = run {
        val fileSrc = file(src)
        val fileDst = file(dst)
        if (overwrite && fileDst.exists()) error("Destination exists")
        fileSrc.renameTo(fileDst)
    }
}

object ResourceTypeCollection
object NoContent : OutgoingContent.NoContent()

open class WebDavFilesystem {
    open suspend fun read(path: String): OutgoingContent {
        return NoContent
    }

    open suspend fun write(path: String, channel: ByteReadChannel) {
    }

    open suspend fun list(path: String): List<String> {
        return listOf()
    }

    open suspend fun propfind(path: String): Map<String, Any?> {
        return mapOf(
            WebDavProps.displayname to null,
            WebDavProps.getlastmodified to Date(),
            WebDavProps.resourcetype to ResourceTypeCollection
        )
    }

    open suspend fun mkdir(path: String) {
    }

    open suspend fun delete(path: String) {
    }

    open suspend fun copy(src: String, dst: String) {
    }

    open suspend fun move(src: String, dst: String, overwrite: Boolean = false) {
    }
}

val webDavXmlMapper by lazy {
    XmlMapper().apply {
        enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
        enable(SerializationFeature.INDENT_OUTPUT)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
}

suspend fun ApplicationCall.readXmlAsMap(): Map<String, Any?> {
    val resultStr = try {
        receiveText()
    } catch (e: Throwable) {
        ""
    }
    val resultData = try {
        webDavXmlMapper.readValue<Map<String, Any?>>(StringReader(resultStr))
    } catch (e: Throwable) {
        mapOf<String, Any?>()
    }

    //println("-->$resultStr")
    //println("-->$resultData")
    return resultData
}

val WebDAVDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z")

val PROP_NAMESPACES = mapOf(
    WebDavProps.lastmodified to ""
)

private fun xml.renderProps(props: List<String>, rprops: Map<String, Any?>) {
    for (key in props) {
        val value = rprops[key]
        val ns = PROP_NAMESPACES[key] ?: "D:"
        val dkey = "$ns$key"
        when (value) {
            ResourceTypeCollection -> dkey { "D:collection"() }
            is Date -> dkey(WebDAVDateFormat.format(value))
            null -> dkey()
            else -> dkey("$value")
        }
    }
}

private fun xml.renderResponse(
    path: String,
    props: List<String>,
    rprops: Map<String, Any?>
): Map<String, Any?> {
    "D:response" {
        //"D:href"("$scheme://${request.host()}:${request.port()}$uri")
        "D:href"(path)
        val foundProps = props.filter { it in rprops }
        val notFoundProps = props.filter { it !in rprops }

        if (foundProps.isNotEmpty()) {
            "D:propstat" {
                "D:status"("HTTP/1.1 200 OK")
                "D:prop" {
                    renderProps(foundProps, rprops)
                }
            }
        }
        if (notFoundProps.isNotEmpty()) {
            "D:propstat" {
                "D:status"("HTTP/1.1 404 Not Found")
                "D:prop" {
                    renderProps(notFoundProps, rprops)
                }
            }
        }
    }
    return rprops
}

object WebDavHttpStatusCode {
    val MultiStatus = HttpStatusCode(207, "Multi-Status")
}

val HttpStatusCode.Companion.MultiStatus get() = WebDavHttpStatusCode.MultiStatus

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

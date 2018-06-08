package io.ktor.experimental.webdav

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.dataformat.xml.*
import com.fasterxml.jackson.dataformat.xml.ser.*
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.pipeline.*
import io.ktor.request.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.jvm.javaio.*

class JacksonXmlConverter(private val objectmapper: XmlMapper = XmlMapper()) : ContentConverter {
    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any
    ): Any? {
        return TextContent(
            objectmapper.writeValueAsString(value),
            contentType.withCharset(context.call.suitableCharset())
        )
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val type = request.type
        val value = request.value as? ByteReadChannel ?: return null
        val reader = value.toInputStream().reader(context.call.request.contentCharset() ?: Charsets.UTF_8)
        return objectmapper.readValue(reader, type.javaObjectType)
    }
}

fun ContentNegotiation.Configuration.jacksonXml(block: XmlMapper.() -> Unit) {
    val mapper = XmlMapper().apply {
        enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
        enable(SerializationFeature.INDENT_OUTPUT)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
    mapper.apply(block)
    val converter = JacksonConverter(mapper)
    register(ContentType.Application.Xml, converter)
}

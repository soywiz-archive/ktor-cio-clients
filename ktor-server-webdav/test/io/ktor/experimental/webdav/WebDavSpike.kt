package io.ktor.experimental.webdav

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.dataformat.xml.*
import com.fasterxml.jackson.dataformat.xml.annotation.*
import com.fasterxml.jackson.dataformat.xml.ser.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.io.*


object WebDavSpike {
    @JvmStatic
    fun main(args: Array<String>) {
        val mapper = XmlMapper().apply {
            enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
            enable(SerializationFeature.INDENT_OUTPUT)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
        //println(mapper.writeValueAsString(propfind()))

        /*
        val text = """<?xml version="1.0" encoding="utf-8" ?>
   <D:propfind xmlns:D="DAV:">
     <D:prop xmlns:R="http://www.foo.bar/boxschema/">
          <R:bigbox/>
          <R:author/>
          <R:DingALing/>
          <R:Random/>
     </D:prop>
   </D:propfind>"""
        val reader = StringReader(text)
        val factory = XMLInputFactory.newInstance() // Or newFactory()
        val xmlReader = factory.createXMLStreamReader(reader)

        //println(mapper.readValue(xmlReader, Map::class.java))
        println(mapper.readValue(xmlReader, propfind::class.java))
        println(mapper.writeValueAsString(propfind()))
        */

        //println(mapper.writeValueAsString(mapOf("a" to "b")))

        embeddedServer(Netty, port = 9090) {
            routing {
                val webdavRoot = File("webdav-root")
                webdavRoot.mkdirs()
                webdav(FileWebDavFilesystem(webdavRoot))
            }
        }.start(wait = true)
    }
}

class MyPropFind

//@JacksonXmlElementWrapper(namespace = "DAV", localName = "propfind", useWrapping = true)
//@JacksonXmlRootElement(namespace = "DAV", localName = "propfind")
data class propfind(
    @JacksonXmlProperty(namespace = "DAV")
    var prop: prop? = prop()
)

@JacksonXmlRootElement(namespace = "DAV", localName = "prop")
class prop(
    //@JacksonXmlProperty(
    //    isAttribute = true, namespace = "urn:stackify:jacksonxml", localName = "_id")
    //var bigbox: bigbox? = bigbox()

) {
    //@JacksonXmlProperty(isAttribute = true, namespace = "urn:stackify:jacksonxml", localName = "_id")
    private val id: String? = "id"

    @JacksonXmlProperty(namespace = "DAV")
    private val name: String? = "name"

    private val note: String? = "note"
}

//@JacksonXmlRootElement(namespace = "http://www.foo.bar/boxschema/", localName = "bigbox")
class bigbox {

}
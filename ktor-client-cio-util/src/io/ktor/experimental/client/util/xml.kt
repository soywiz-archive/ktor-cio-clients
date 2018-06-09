package io.ktor.experimental.client.util

class xml @PublishedApi internal constructor(val sb: Appendable) {
    @PublishedApi
    internal var indent = 0

    @PublishedApi
    internal fun start(name: String, attributes: Array<out Pair<String, Any?>>) {
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append(buildOpenTag(name, attributes))
        sb.append('\n')
    }

    @PublishedApi
    internal fun end(name: String) {
        sb.append(buildCloseTag(name))
        sb.append('\n')
    }

    object Indents {
        private val INDENTS = arrayListOf("")

        operator fun get(index: Int): String {
            if (index >= INDENTS.size) {
                val calculate = INDENTS.size * 10
                var indent = INDENTS[INDENTS.size - 1]
                while (calculate >= INDENTS.size) {
                    indent += "\t"
                    INDENTS.add(indent)
                }
            }
            return if (index <= 0) "" else INDENTS[index]
        }
    }

    fun String.escape(): String {
        val text = this@escape
        if (text.isEmpty()) return text

        return buildString(length) {
            for (ch in text) {
                when (ch) {
                    '\'' -> append("&apos;")
                    '\"' -> append("&quot")
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    else -> append(ch)
                }
            }
        }
    }

    @PublishedApi
    internal fun buildOpenTag(name: String, attributes: Array<out Pair<String, Any?>>, close: Boolean = false): String {
        val closeStr = if (close) " /" else ""
        val attr =
            if (attributes.isEmpty()) "" else " " + attributes.joinToString(" ") { "${it.first}=\"${it.second.toString().escape()}\"" }
        return "<$name$attr$closeStr>"
    }

    @PublishedApi
    internal fun buildCloseTag(name: String): String {
        return "</$name>"
    }

    @PublishedApi
    internal inline fun indent(callback: () -> Unit) {
        indent++
        try {
            callback()
        } finally {
            indent--
        }
    }

    @PublishedApi
    internal fun appendOpenTag(
        name: String,
        vararg attributes: Pair<String, Any?>,
        eol: Boolean,
        close: Boolean = false
    ) {
        sb.append(Indents[indent])
        sb.append(buildOpenTag(name, attributes, close = close))
        if (eol) sb.append('\n')
    }

    @PublishedApi
    internal fun appendCloseTag(name: String, eol: Boolean = true, doIndent: Boolean = true) {
        if (doIndent) sb.append(Indents[indent])
        sb.append(buildCloseTag(name))
        if (eol) sb.append('\n')
    }

    @PublishedApi
    internal fun appendContent(content: String, eol: Boolean = true) {
        sb.append(content.escape())
        if (eol) sb.append('\n')
    }

    inline operator fun String.invoke(vararg attributes: Pair<String, Any?>, callback: xml.() -> Unit) {
        appendOpenTag(this, *attributes, eol = true)
        indent {
            callback()
        }
        appendCloseTag(this)
    }

    operator fun String.invoke(content: String, vararg attributes: Pair<String, Any?>) {
        appendOpenTag(this, *attributes, eol = false)
        appendContent(content, eol = false)
        appendCloseTag(this, doIndent = false)
    }

    operator fun String.invoke(vararg attributes: Pair<String, Any?>) {
        appendOpenTag(this, *attributes, eol = true, close = true)
    }

    operator fun String.unaryPlus() {
        appendContent(this)
    }

    override fun toString(): String = sb.toString()
}

inline fun xml(name: String, vararg attributes: Pair<String, Any?>, callback: xml.() -> Unit): String {
    val sb = StringBuilder()
    xml(sb).apply {
        start(name, attributes)
        indent {
            callback()
        }
        end(name)
    }
    return sb.toString()
}

inline fun xml(appendable: Appendable, name: String, vararg attributes: Pair<String, Any?>, callback: xml.() -> Unit) {
    xml(appendable).apply {
        start(name, attributes)
        indent {
            callback()
        }
        end(name)
    }
}

//fun main(args: Array<String>) {
//    println(xml("helo", "world" to 10) {
//        "D:hello"("test" to 10) {
//            +"hello <test>"
//        }
//    })
//}

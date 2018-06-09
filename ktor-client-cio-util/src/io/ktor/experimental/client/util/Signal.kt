package io.ktor.experimental.client.util

import java.io.*
import java.util.*

class Signal<T>(val onRegister: () -> Unit = {}) { //: AsyncSequence<T> {
    inner class Node(val once: Boolean, val item: (T) -> Unit) : Closeable {
        override fun close() {
            handlers.remove(this)
        }
    }

    private var handlersToRun = ArrayList<Node>()
    private var handlers = ArrayList<Node>()
    private var handlersNoOnce = ArrayList<Node>()

    val listenerCount: Int get() = handlers.size

    fun once(handler: (T) -> Unit): Closeable = _add(true, handler)
    fun add(handler: (T) -> Unit): Closeable = _add(false, handler)

    fun clear() = handlers.clear()

    private fun _add(once: Boolean, handler: (T) -> Unit): Closeable {
        onRegister()
        val node = Node(once, handler)
        handlers.add(node)
        return node
    }

    operator fun invoke(value: T) {
        val oldHandlers = handlers
        handlersNoOnce.clear()
        handlersToRun.clear()
        for (handler in oldHandlers) {
            handlersToRun.add(handler)
            if (!handler.once) handlersNoOnce.add(handler)
        }
        val temp = handlers
        handlers = handlersNoOnce
        handlersNoOnce = temp

        for (handler in handlersToRun) {
            handler.item(value)
        }
    }

    operator fun invoke(handler: (T) -> Unit): Closeable = add(handler)
}

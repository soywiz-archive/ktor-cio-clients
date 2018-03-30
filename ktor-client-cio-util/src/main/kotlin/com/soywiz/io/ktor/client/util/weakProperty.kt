package com.soywiz.io.ktor.client.util

import java.util.*
import kotlin.reflect.*

interface WithProperties {
    val properties: MutableMap<String, Any>

    class Mixin(override val properties: MutableMap<String, Any> = hashMapOf()) : WithProperties
}

class weakProperty<T, V>(val default: () -> V) {
    val map = WeakHashMap<T, V>()

    operator fun getValue(obj: T, prop: KProperty<*>): V {
        return map.getOrPut(obj, default)
    }

    operator fun setValue(obj: T, prop: KProperty<*>, value: V) {
        map[obj] = value
    }
}

class property<R: WithProperties, T : Any>(val default: R.() -> T) {
    operator fun getValue(obj: R, prop: KProperty<*>): T =
        obj.properties.getOrPut(prop.name) { default(obj) } as T

    operator fun setValue(obj: R, prop: KProperty<*>, value: T) {
        obj.properties[prop.name] = value
    }
}

//var String.demo: Int by weakProperty { 10 }

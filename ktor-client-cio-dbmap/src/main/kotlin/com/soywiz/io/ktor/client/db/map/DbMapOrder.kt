package com.soywiz.io.ktor.client.db.map

import kotlin.reflect.*

enum class OrderDir { ASC, DESC }
class OrderSpec<T : Any>(vararg val pairs: Pair<KProperty1<T, *>, OrderDir?>) {
    companion object {
        operator fun <T : Any> invoke(vararg props: KProperty1<T, *>): OrderSpec<T> =
            OrderSpec<T>(*props.map { it to null }.toTypedArray())
    }

    fun toString(info: TableInfo<T>): String =
        pairs.map { info.getColumn(it.first).quotedName + " " + it.second }.joinToString(", ")
}

fun <T : Any> KProperty1<T, *>.DESC() = OrderSpec<T>(this to OrderDir.DESC)
fun <T : Any> KProperty1<T, *>.ASC() = OrderSpec<T>(this to OrderDir.ASC)
fun <T : Any> KProperty1<T, *>.ORDER(order: OrderDir? = null) = OrderSpec<T>(this to order)
infix fun <T : Any> OrderSpec<T>.AND(order: OrderSpec<T>) =
    OrderSpec<T>(*(this.pairs.toList() + order.pairs.toList()).toTypedArray())

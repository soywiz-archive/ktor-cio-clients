package com.soywiz.io.ktor.client.db.map

import com.soywiz.io.ktor.client.db.*
import kotlin.reflect.*


interface Where {
    fun render(db: DbClient): String

    object ALWAYS : Where {
        override fun render(db: DbClient): String = ""
    }

    class BINARY<T : Any, R>(val klazz: KClass<T>, val prop: KProperty1<T, R>, val op: String, val cst: R) : Where {
        override fun render(db: DbClient): String =
            "(" + db.quoteColumn(db.tablesInfo.get(klazz).getColumn(prop).name) + " $op " + db.quoteConstant(cst) + ")"
    }

    class BINARY_EXPR(val left: Where, val op: String, val right: Where) : Where {
        override fun render(db: DbClient): String = "(${left.render(db)} $op ${right.render(db)})"
    }

    class UNARY(val op: String, val right: Where) : Where {
        override fun render(db: DbClient): String = "($op ${right.render(db)})"
    }

    object Builder {
        inline infix fun <reified T : Any, R> KProperty1<T, R>.EQ(cst: R) = BINARY(T::class, this, "=", cst)
        inline infix fun <reified T : Any, R> KProperty1<T, R>.LIKE(cst: R) = BINARY(T::class, this, "LIKE", cst)
        inline infix fun <reified T : Any, R> KProperty1<T, R>.NE(cst: R) = BINARY(T::class, this, "<>", cst)
        inline infix fun <reified T : Any, R> KProperty1<T, R>.GT(cst: R) = BINARY(T::class, this, ">", cst)
        inline infix fun <reified T : Any, R> KProperty1<T, R>.GE(cst: R) = BINARY(T::class, this, ">=", cst)
        inline infix fun <reified T : Any, R> KProperty1<T, R>.LT(cst: R) = BINARY(T::class, this, "<", cst)
        inline infix fun <reified T : Any, R> KProperty1<T, R>.LE(cst: R) = BINARY(T::class, this, "<=", cst)
        infix fun Where.AND(right: Where) = BINARY_EXPR(this, "AND", right)
        infix fun Where.OR(right: Where) = BINARY_EXPR(this, "OR", right)
        infix fun NOT(right: Where) = UNARY("NOT", right)
    }

    companion object {
        fun AND(items: List<Where>): Where = items.reduce { l, r -> Where.BINARY_EXPR(l, "AND", r) }
    }
}

package io.ktor.experimental.client.db.map

import io.ktor.experimental.client.db.*
import io.ktor.experimental.client.util.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

//private fun <T : Any> createDefaultUnsafe(klazz: KClass<T>): Any? {
//    val clazz = klazz.java
//    return when {
//        clazz.isAssignableFrom(String::class.java) -> ""
//        clazz.isAssignableFrom(Int::class.java) -> 0
//        clazz.isAssignableFrom(java.lang.Integer::class.java) -> 0
//        clazz.isAssignableFrom(java.lang.Integer.TYPE::class.java) -> 0
//        clazz.isAssignableFrom(java.lang.Long::class.java) -> 0L
//        clazz.isAssignableFrom(java.lang.Long.TYPE::class.java) -> 0L
//        clazz.isAssignableFrom(java.util.List::class.java) -> arrayListOf<Any?>()
//        clazz.isAssignableFrom(java.util.Map::class.java) -> hashMapOf<Any?, Any?>()
//        else -> {
//            val konstructor = klazz.primaryConstructor ?: error("$clazz doesn't have constructors")
//            konstructor.isAccessible = true
//            val params = konstructor.parameters
//            val args = arrayOfNulls<Any?>(params.size)
//            for ((n, param) in params.withIndex()) {
//                args[n] = createDefaultUnsafe(param.type as KClass<*>)
//            }
//            return konstructor.call(*args)
//        }
//    }
//}
//private fun <T : Any> createDefault(clazz: KClass<T>): T? = createDefaultUnsafe(clazz) as? T?

fun <T : Any> convertStringToUnsafe(value: String?, to: KClass<T>): Any? {
    return when {
        to.isInstance(value) -> value
        to.isSubclassOf(Int::class) -> value?.toIntOrNull() ?: 0
        to.isSubclassOf(Long::class) -> value?.toLongOrNull() ?: 0L
        to.isSubclassOf(String::class) -> value ?: ""
        else -> TODO("Unsupported")
    }
}

//fun <T : Any> convertTo(value: Any?, to: KClass<T>): T? {
//    return convertStringToUnsafe(value.toString(), to) as? T?
//}

fun <T : Any> Any?.convertTo(to: KClass<T>): T? {
    return convertStringToUnsafe(this.toString(), to) as? T?
}

class TableInfo<T : Any>(val db: DbClient, val klazz: KClass<T>) {
    val clazz = klazz.java
    private val annotationTableName = klazz.findAnnotation<Name>()
    val tableName: String get() = annotationTableName?.name ?: klazz.simpleName ?: "unknown"
    val quotedTableName by lazy { db.quoteTable(tableName) }

    val primaryConstructor = klazz.primaryConstructor ?: error("No primary constructor")
    val primaryParams = primaryConstructor.parameters.associateBy { it.name }

    inner class Column(val prop: KProperty1<T, *>) {
        val klazz = prop.returnType.jvmErasure
        val param = primaryParams[prop.name]
        private val annotationName = prop.findAnnotation<Name>()

        val name get() = annotationName?.name ?: prop.name
        val primaryKey = param?.findAnnotation<Primary>() != null || prop?.findAnnotation<Primary>() != null
        val unique = param?.findAnnotation<Unique>() != null || prop?.findAnnotation<Unique>() != null
        val nullable = prop.returnType.isMarkedNullable
        val serial = primaryKey
        val sqlType = when {
            serial -> "serial"
            klazz.isSubclassOf(CharSequence::class) -> "VARCHAR"
            klazz.isSubclassOf(Int::class) -> "INT"
            klazz.isSubclassOf(ByteArray::class) -> "BLOB"
            else -> "VARCHAR"
        }

        val quotedName by lazy { db.quoteColumn(name) }

        init {
            prop.isAccessible = true
        }

        fun get(item: T): Any? {
            return prop.javaGetter!!.invoke(item)
        }
    }

    val columns = klazz.memberProperties.map { Column(it) }
    val columnsByFieldName = columns.associateBy { it.prop.name }
    val primaryColumn by lazy { columns.firstOrNull { it.primaryKey } }
    val uniqueColumn by lazy { columns.firstOrNull { it.unique } }

    fun toMap(item: T, skipSerials: Boolean = false): Map<String, Any?> {
        val out = hashMapOf<String, Any?>()
        for (column in columns) {
            if (skipSerials && column.serial) continue // Skip serials

            out[column.name] = column.get(item)
        }
        return out
    }

    fun toItem(row: DbRow): T {
        val constructor = primaryConstructor
        val params = constructor.parameters
        val args = arrayOfNulls<Any?>(params.size)
        for ((index, param) in params.withIndex()) {
            val paramKlazz = param.type.jvmErasure
            args[index] = row.string(param.name ?: "unknown").convertTo(paramKlazz)
        }
        return constructor.call(*args)
    }

    fun <R> getColumn(prop: KProperty1<T, R>): Column = columnsByFieldName[prop.name]!!
    fun <R> getColumns(props: List<KProperty1<T, R>>): List<Column> = props.map { getColumn(it) }
}

@Suppress("UNCHECKED_CAST")
class TablesInfo(val db: DbClient) {
    val infos = hashMapOf<KClass<*>, TableInfo<*>>()
    fun <T : Any> get(clazz: KClass<T>): TableInfo<T> = infos.getOrPut(clazz) {
        TableInfo(
            db,
            clazz
        )
    } as TableInfo<T>
}

val DbClient.tablesInfo: TablesInfo by property {
    TablesInfo(
        this
    )
}

package io.ktor.experimental.client.db

class DBRow(val columns: DBColumns, val cells: List<ByteArray?>) : List<Any?> {
    val context get() = columns.context

    fun bytes(key: Int) = cells.getOrNull(key)
    fun bytes(key: String) = columns[key]?.let { bytes(it.index) }

    fun string(key: Int) = bytes(key)?.toString(context.charset)
    fun string(key: String) = bytes(key)?.toString(context.charset)

    fun int(key: Int) = string(key)?.toInt()
    fun int(key: String) = string(key)?.toInt()

    fun date(key: Int) = string(key)?.let { context.date(it) }
    fun date(key: String) = string(key)?.let { context.date(it) }

    operator fun get(key: String) = bytes(key)

    val pairs get() = columns.map { it.name }.zip(typedList)
    override fun toString(): String = "{${pairs.map { "${it.first}=${it.second}" }.joinToString(", ")}}"

    //////////////////////////////////////////////////////////////

    val typedList by lazy {
        (0 until size).map { string(it) } // Proper type
    }

    override val size: Int get() = cells.size
    override fun contains(element: Any?): Boolean = typedList.contains(element)
    override fun containsAll(elements: Collection<Any?>): Boolean = typedList.containsAll(elements)
    override fun get(index: Int): Any? = typedList.get(index)
    override fun indexOf(element: Any?): Int = typedList.indexOf(element)
    override fun isEmpty(): Boolean = typedList.isEmpty()
    override fun iterator(): Iterator<Any?> = typedList.iterator()
    override fun lastIndexOf(element: Any?): Int = typedList.lastIndexOf(element)
    override fun listIterator(): ListIterator<Any?> = typedList.listIterator()
    override fun listIterator(index: Int): ListIterator<Any?> = typedList.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): List<Any?> = typedList.subList(fromIndex, toIndex)
}

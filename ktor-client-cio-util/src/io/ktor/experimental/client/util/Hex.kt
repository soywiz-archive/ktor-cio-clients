package io.ktor.experimental.client.util

object Hex {
    const val DIGITS = "0123456789ABCDEF"
    val DIGITS_UPPER = DIGITS.toUpperCase()
    val DIGITS_LOWER = DIGITS.toLowerCase()

    fun isHexDigit(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    fun decodeHexDigit(c: Char): Int {
        return when (c) {
            in '0'..'9' -> (c - '0') + 0
            in 'a'..'f' -> (c - 'a') + 10
            in 'A'..'F' -> (c - 'A') + 10
            else -> throw IllegalArgumentException("Not an hex digit")
        }
    }

    fun decode(str: String): ByteArray {
        val out = ByteArray(str.length / 2)
        var m = 0
        for (n in 0 until out.size) {
            val high = str[m++]
            val low = str[m++]
            out[n] = ((decodeHexDigit(high) shl 4) or (decodeHexDigit(low) shl 0)).toByte()
        }
        return out
    }

    fun encode(src: ByteArray): String = encodeBase(src, DIGITS_LOWER)
    fun encodeLower(src: ByteArray): String = encodeBase(src, DIGITS_LOWER)
    fun encodeUpper(src: ByteArray): String = encodeBase(src, DIGITS_UPPER)

    private fun encodeBase(data: ByteArray, digits: String = DIGITS): String {
        val out = StringBuilder(data.size * 2)
        for (n in data.indices) {
            val v = data[n].toInt() and 0xFF
            out.append(digits[(v ushr 4) and 0xF])
            out.append(digits[(v ushr 0) and 0xF])
        }
        return out.toString()
    }
}

val ByteArray.hex: String get() = Hex.encodeLower(this)
val String.unhex: ByteArray get() = Hex.decode(this)

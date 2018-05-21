package com.soywiz.io.ktor.client.util

fun Long.clamp(min: Long, max: Long) = if (this < min) min else if (this > max) max else this
fun Int.clamp(min: Int, max: Int) = if (this < min) min else if (this > max) max else this
fun Double.clamp(min: Double, max: Double) = if (this < min) min else if (this > max) max else this
fun Float.clamp(min: Float, max: Float) = if (this < min) min else if (this > max) max else this

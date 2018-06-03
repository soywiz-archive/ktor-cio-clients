package com.soywiz.io.ktor.client.util

val LongRange.end get(): Long {
    if (endInclusive == Long.MAX_VALUE) error("Overflow")
    return endInclusive + 1
}
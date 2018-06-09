package io.ktor.experimental.client.amazon.auth

import io.ktor.http.*
import org.junit.*
import java.util.*
import kotlin.reflect.*
import kotlin.test.*

class AmazonAuthV1Test {
    @Test
    fun name() {
        setTemporalLocale(Locale.FRANCE) {
            assertEquals(
                "AWS accessKey:r+9xbIWji99JFGaEdxOaj6iEoyU=",
                AmazonAuth.V1.getAuthorization(
                    accessKey = "accessKey",
                    secretKey = "secretKey",
                    method = HttpMethod.Get,
                    cannonicalPath = "/path",
                    headers = headersOf(HttpHeaders.Date, AmazonAuth.V1.DATE_FORMAT.format(0L))
                )
            )
        }
    }
}

inline fun setTemporalLocale(locale: Locale, callback: () -> Unit) {
    setTemporal(
        { Locale.getDefault() },
        { Locale.setDefault(it) },
        locale,
        callback
    )
}

inline fun <T> setTemporal(get: () -> T, set: (value: T) -> Unit, temporalValue: T, callback: () -> Unit) {
    val old = get()
    try {
        set(temporalValue)
        callback()
    } finally {
        set(old)
    }
}

inline fun <T> setTemporal(property: KMutableProperty0<T>, temporalValue: T, callback: () -> Unit) {
    val old = property.get()
    try {
        property.set(temporalValue)
        callback()
    } finally {
        property.set(old)
    }
}


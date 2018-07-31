package io.ktor.experimental.client.db

import java.time.*

class DbClientProperties(
    val user: String,
    val password: String? = null,
    val database: String = user,
    val reconnectionTime: Duration = Duration.ofSeconds(15L),
    val debug: Boolean = false
)

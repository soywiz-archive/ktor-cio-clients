package io.ktor.experimental.client.postgre.scheme

import io.ktor.experimental.client.db.*

data class PostgreColumn(
    override val index: Int,
    override val name: String, val tableOID: Int, val columnIndex: Int, val typeOID: Int,
    val typeSize: Int,
    val typeMod: Int,
    val text: Boolean
) : DBColumn

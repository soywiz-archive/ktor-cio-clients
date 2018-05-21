package com.soywiz.io.ktor.client.mongodb

import com.soywiz.io.ktor.client.mongodb.bson.*
import com.soywiz.io.ktor.client.util.sync.*
import java.io.*
import java.net.*
import java.security.*

// https://docs.mongodb.com/manual/reference/method/ObjectId/#ObjectId
// a 4-byte value representing the seconds since the Unix epoch,
// a 3-byte machine identifier,
// a 2-byte process id, and
// a 3-byte counter, starting with a random value.

// A globally unique identifier for objects.
// Consists of 12 bytes, divided as follows:
// ObjectID layout
// 0123 456     78   91011
// time machine pid  inc
// Note that the numbers are stored in big-endian order.
object MongoDBObjectIdGenerator {
    private val machineId by lazy { createMachineIdentifier() }
    private val processId by lazy { createProcessIdentifier() }
    private var counter = SecureRandom().nextInt() and 0xFFFFFF

    fun generate(): BsonObjectId = synchronized(this) {
        return BsonObjectId(ByteArrayOutputStream().apply {
            write32_be((System.currentTimeMillis() / 1000L).toInt())
            write24_be(machineId)
            write16_be(processId)
            write24_be(counter++)
        }.toByteArray())
    }

    private fun createProcessIdentifier(): Int {
        return try {
            val mxName = java.lang.management.ManagementFactory.getRuntimeMXBean().name
            return mxName.split("@").firstOrNull()?.toIntOrNull() ?: mxName.hashCode()
        } catch (t: Throwable) {
            SecureRandom().nextInt()
        }
    }

    private fun createMachineIdentifier(): Int = try {
        var sb = 0
        for (ni in NetworkInterface.getNetworkInterfaces()) {
            sb += ni.hashCode()
            val mac = ni.hardwareAddress
            if (mac != null) for (m in mac) sb += m.toInt()
        }
        sb
    } catch (t: Throwable) {
        SecureRandom().nextInt()
    } and 0xFFFFFF
}

fun BsonObjectId() = MongoDBObjectIdGenerator.generate()
fun BsonObjectId.Companion.create() = BsonObjectId()

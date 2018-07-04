package io.ktor.experimental.client.redis

import io.ktor.experimental.client.redis.protocol.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import kotlin.test.*

class RedisIntegrationTest {
    //EVAL "return redis.call('del', unpack(redis.call('keys', ARGV[1])))" 0 prefix:*
    val redis = Redis()
    val prefix = ":redis:integration:test:"
    val key1 = "${prefix}key1"

    @Before
    fun setUp() = cleanup()

    @After
    fun tearDown() = cleanup()

    private fun cleanup() = runBlocking {
        //redis.commandUnit("EVAL", "return redis.call('del', unpack(redis.call('keys', ARGV[1])))", 0, "$prefix*")
    }

    @Test
    fun getSet(): Unit = redisTest {
        for (myvalue in listOf("", "a", "ab", "abc", "myalue", "a".repeat(1024), "a".repeat(2048))) {
            redis.del(key1)
            assertEquals(null, redis.get(key1))
            redis.set(key1, myvalue)
            assertEquals(myvalue, redis.get(key1))
            redis.del(key1)
        }
    }

    @Test
    fun authTest(): Unit = redisTest {
        assertFailsWith<RedisException> {
            runBlocking {
                redis.auth("not the right password")
            }
        }
    }

    private fun redisTest(callback: suspend () -> Unit) {
        runBlocking { callback() }
    }
}
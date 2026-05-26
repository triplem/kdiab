@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.common.plugins

import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.RateLimitExceededException

class RateLimitTest {

    private val nowMs = System.currentTimeMillis()

    private fun freshMap() = ConcurrentHashMap<String, ArrayDeque<Long>>()

    @Test
    fun `requests below the limit succeed and return correct remaining count`() {
        val remaining = checkRateLimit(freshMap(), Uuid.random().toString(), nowMs, limit = 5, windowSeconds = 60)
        assertEquals(4, remaining)
    }

    @Test
    fun `remaining decrements with each request`() {
        val map = freshMap()
        val id = Uuid.random().toString()
        checkRateLimit(map, id, nowMs, limit = 5, windowSeconds = 60)
        checkRateLimit(map, id, nowMs + 1, limit = 5, windowSeconds = 60)
        val remaining = checkRateLimit(map, id, nowMs + 2, limit = 5, windowSeconds = 60)
        assertEquals(2, remaining)
    }

    @Test
    fun `throws RateLimitExceededException when limit is reached`() {
        val map = freshMap()
        val id = Uuid.random().toString()
        repeat(3) { i -> checkRateLimit(map, id, nowMs + i, limit = 3, windowSeconds = 60) }
        assertFailsWith<RateLimitExceededException> {
            checkRateLimit(map, id, nowMs + 3, limit = 3, windowSeconds = 60)
        }
    }

    @Test
    fun `exception carries limit, accurate retryAfterSeconds, and userId`() {
        val map = freshMap()
        val id = Uuid.random().toString()
        repeat(2) { i -> checkRateLimit(map, id, nowMs + i, limit = 2, windowSeconds = 30) }
        val ex = assertFailsWith<RateLimitExceededException> {
            checkRateLimit(map, id, nowMs + 2, limit = 2, windowSeconds = 30)
        }
        assertEquals(2, ex.limit)
        assertEquals(id, ex.userId)
        // retryAfterSeconds = ((firstEntry + windowMs - nowMs+2) / 1000) + 1 = ((nowMs + 30000 - nowMs - 2) / 1000) + 1 = 30
        assertEquals(30L, ex.retryAfterSeconds)
    }

    @Test
    fun `old entries outside the window are pruned and do not count`() {
        val map = freshMap()
        val id = Uuid.random().toString()
        val windowSeconds = 60L
        val windowMs = windowSeconds * 1_000L
        checkRateLimit(map, id, nowMs - windowMs - 1, limit = 3, windowSeconds = windowSeconds)
        checkRateLimit(map, id, nowMs - windowMs - 2, limit = 3, windowSeconds = windowSeconds)
        val remaining = checkRateLimit(map, id, nowMs, limit = 3, windowSeconds = windowSeconds)
        assertEquals(2, remaining)
    }

    @Test
    fun `different user ids have independent rate limit counters`() {
        val map = freshMap()
        val idA = Uuid.random().toString()
        val idB = Uuid.random().toString()
        repeat(5) { i -> checkRateLimit(map, idA, nowMs + i, limit = 5, windowSeconds = 60) }
        val remaining = checkRateLimit(map, idB, nowMs, limit = 5, windowSeconds = 60)
        assertEquals(4, remaining)
    }

    @Test
    fun `resolveRateLimitConfig returns defaults when env vars are absent`() {
        val (requests, windowSeconds) = resolveRateLimitConfig(env = { null })
        assertEquals(300, requests)
        assertEquals(60L, windowSeconds)
    }

    @Test
    fun `resolveRateLimitConfig respects injected env vars`() {
        val (requests, windowSeconds) = resolveRateLimitConfig(env = { key ->
            when (key) {
                "RATE_LIMIT_REQUESTS" -> "10"
                "RATE_LIMIT_WINDOW_SECONDS" -> "30"
                else -> null
            }
        })
        assertEquals(10, requests)
        assertEquals(30L, windowSeconds)
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.common.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.RateLimitExceededException

/**
 * Unit tests for the sliding-window rate limiting logic in [RateLimit.kt].
 *
 * Tests exercise [checkRateLimit] directly (pure function, no Ktor wiring needed)
 * and [resolveRateLimitConfig] for env-var defaults.
 */
class RateLimitTest {

    private val userId = Uuid.random().toString()
    private val nowMs = System.currentTimeMillis()

    @Test
    fun `requests below the limit succeed and return correct remaining count`() {
        val remaining = checkRateLimit(userId, nowMs, limit = 5, windowSeconds = 60)
        assertEquals(4, remaining)
    }

    @Test
    fun `remaining decrements with each request`() {
        val id = Uuid.random().toString()
        checkRateLimit(id, nowMs, limit = 5, windowSeconds = 60)
        checkRateLimit(id, nowMs + 1, limit = 5, windowSeconds = 60)
        val remaining = checkRateLimit(id, nowMs + 2, limit = 5, windowSeconds = 60)
        assertEquals(2, remaining)
    }

    @Test
    fun `throws RateLimitExceededException when limit is reached`() {
        val id = Uuid.random().toString()
        repeat(3) { i -> checkRateLimit(id, nowMs + i, limit = 3, windowSeconds = 60) }
        assertFailsWith<RateLimitExceededException> {
            checkRateLimit(id, nowMs + 3, limit = 3, windowSeconds = 60)
        }
    }

    @Test
    fun `exception carries correct limit and retryAfterSeconds`() {
        val id = Uuid.random().toString()
        repeat(2) { i -> checkRateLimit(id, nowMs + i, limit = 2, windowSeconds = 30) }
        val ex = assertFailsWith<RateLimitExceededException> {
            checkRateLimit(id, nowMs + 2, limit = 2, windowSeconds = 30)
        }
        assertEquals(2, ex.limit)
        assertEquals(30L, ex.retryAfterSeconds)
    }

    @Test
    fun `old entries outside the window are pruned and do not count`() {
        val id = Uuid.random().toString()
        val windowSeconds = 60L
        val windowMs = windowSeconds * 1_000L
        // Add entries older than the window
        checkRateLimit(id, nowMs - windowMs - 1, limit = 3, windowSeconds = windowSeconds)
        checkRateLimit(id, nowMs - windowMs - 2, limit = 3, windowSeconds = windowSeconds)
        // Both old entries should be pruned — first request in the new window should succeed
        val remaining = checkRateLimit(id, nowMs, limit = 3, windowSeconds = windowSeconds)
        assertEquals(2, remaining)
    }

    @Test
    fun `different user ids have independent rate limit counters`() {
        val idA = Uuid.random().toString()
        val idB = Uuid.random().toString()
        repeat(5) { i -> checkRateLimit(idA, nowMs + i, limit = 5, windowSeconds = 60) }
        // User B should still have full quota
        val remaining = checkRateLimit(idB, nowMs, limit = 5, windowSeconds = 60)
        assertEquals(4, remaining)
    }

    @Test
    fun `resolveRateLimitConfig returns defaults when env vars are not set`() {
        val (requests, windowSeconds) = resolveRateLimitConfig()
        assertEquals(300, requests)
        assertEquals(60L, windowSeconds)
    }
}

package org.javafreedom.kdiab.analyze.adapters.outbound.http

import org.javafreedom.kdiab.common.plugins.CircuitBreaker
import org.javafreedom.kdiab.common.plugins.CircuitBreakerOpenException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CircuitBreakerTest {

    // ── CLOSED state ──────────────────────────────────────────────────────────

    @Test
    fun `executes block successfully in CLOSED state`() = runTest {
        val cb = CircuitBreaker("svc")
        val result = cb.execute { 42 }
        assertEquals(42, result)
    }

    @Test
    fun `stays CLOSED when failures are below threshold`() = runTest {
        val cb = CircuitBreaker("svc", failureThreshold = 3, resetTimeoutMs = 60_000)
        repeat(2) {
            runCatching { cb.execute { throw RuntimeException("boom") } }
        }
        // Should still execute (not OPEN)
        val result = cb.execute { "ok" }
        assertEquals("ok", result)
    }

    // ── OPEN state ───────────────────────────────────────────────────────────

    @Test
    fun `opens after reaching failure threshold`() = runTest {
        val cb = CircuitBreaker("svc", failureThreshold = 2, resetTimeoutMs = 60_000)
        repeat(2) {
            runCatching { cb.execute { throw RuntimeException("fail") } }
        }
        assertFailsWith<CircuitBreakerOpenException> {
            cb.execute { "should not run" }
        }
    }

    @Test
    fun `fast-fails with CircuitBreakerOpenException when OPEN`() = runTest {
        val cb = CircuitBreaker("svc", failureThreshold = 1, resetTimeoutMs = 60_000)
        runCatching { cb.execute { throw RuntimeException("fail") } }
        val ex = assertFailsWith<CircuitBreakerOpenException> {
            cb.execute { "unreachable" }
        }
        assertEquals("svc", ex.service)
    }

    // ── HALF_OPEN state — thundering-herd fix ─────────────────────────────────

    @Test
    fun `only one probe is allowed through in HALF_OPEN state`() = runTest {
        // Timeout of 0ms so the breaker moves to HALF_OPEN immediately
        val cb = CircuitBreaker("svc", failureThreshold = 1, resetTimeoutMs = 0)
        // Trip the breaker
        runCatching { cb.execute { throw RuntimeException("fail") } }
        // Brief pause to ensure elapsed >= resetTimeoutMs (0ms)
        delay(1)

        val probeInvocations = java.util.concurrent.atomic.AtomicInteger(0)

        // Launch multiple concurrent callers; only one should reach the upstream
        val results = (1..5).map {
            async {
                runCatching {
                    cb.execute {
                        probeInvocations.incrementAndGet()
                        delay(10) // simulate slow upstream probe
                        "probe-result"
                    }
                }
            }
        }.awaitAll()

        // Exactly one probe should have succeeded and reached the upstream block
        val successes = results.filter { it.isSuccess }
        val openExceptions = results.filter {
            it.isFailure && it.exceptionOrNull() is CircuitBreakerOpenException
        }

        assertEquals(1, successes.size, "Expected exactly one probe to succeed")
        assertEquals(4, openExceptions.size, "Expected 4 concurrent callers to be fast-failed")
        assertEquals(1, probeInvocations.get(), "Expected upstream to be probed exactly once")
    }

    @Test
    fun `HALF_OPEN probe failure transitions back to OPEN`() = runTest {
        val cb = CircuitBreaker("svc", failureThreshold = 1, resetTimeoutMs = 0)
        // Trip the breaker
        runCatching { cb.execute { throw RuntimeException("fail") } }
        delay(1)
        // Probe fails
        runCatching { cb.execute { throw RuntimeException("still failing") } }
        // Should be OPEN again
        assertTrue(cb.isOpen, "Expected circuit to be OPEN after failed probe")
    }

    @Test
    fun `HALF_OPEN probe success transitions to CLOSED`() = runTest {
        val cb = CircuitBreaker("svc", failureThreshold = 1, resetTimeoutMs = 0)
        // Trip the breaker
        runCatching { cb.execute { throw RuntimeException("fail") } }
        delay(1)
        // Probe succeeds
        cb.execute { "recovered" }
        // Should be CLOSED again
        val result = cb.execute { "normal call" }
        assertEquals("normal call", result)
    }

    // ── Exception re-throw behaviour ─────────────────────────────────────────

    @Test
    fun `original exception is propagated when block throws`() = runTest {
        val cb = CircuitBreaker("svc", failureThreshold = 5)
        val ex = assertFailsWith<IllegalStateException> {
            cb.execute { throw IllegalStateException("upstream error") }
        }
        assertEquals("upstream error", ex.message)
    }
}

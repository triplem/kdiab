package org.javafreedom.kdiab.users.infrastructure.keycloak

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

class KeycloakCircuitBreakerOpenException(val service: String) :
    RuntimeException("Circuit breaker OPEN for '$service' — Keycloak Admin API unavailable")

/**
 * Lightweight coroutine-safe circuit breaker for the Keycloak Admin API.
 *
 * States:
 *   CLOSED     — normal operation; failures accumulate
 *   OPEN       — fast-fail; no upstream calls until reset timeout elapses
 *   HALF_OPEN  — probe state; exactly one probe in-flight; concurrent callers fast-fail
 *                to prevent the thundering-herd problem on recovery
 */
class CircuitBreaker(
    val name: String,
    private val failureThreshold: Int = 5,
    private val resetTimeoutMs: Long = 30_000L,
    private val isInfrastructureFailure: (Exception) -> Boolean = { true },
) {
    private enum class State { CLOSED, OPEN, HALF_OPEN }

    private val state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0L)
    // Guards HALF_OPEN: only one concurrent caller may probe the upstream at a time.
    private val probeInFlight = AtomicBoolean(false)

    suspend fun <T> execute(block: suspend () -> T): T {
        when (state.get()) {
            State.OPEN -> {
                val elapsed = System.currentTimeMillis() - lastFailureTime.get()
                if (elapsed >= resetTimeoutMs) {
                    state.compareAndSet(State.OPEN, State.HALF_OPEN)
                    logger.info { "circuit_breaker service=$name state=HALF_OPEN probing" }
                } else {
                    logger.warn { "circuit_breaker service=$name state=OPEN fast-failing" }
                    throw KeycloakCircuitBreakerOpenException(name)
                }
            }
            else -> {}
        }

        // In HALF_OPEN state only one probe is allowed at a time; all other callers fast-fail.
        if (state.get() == State.HALF_OPEN && !probeInFlight.compareAndSet(false, true)) {
            logger.warn { "circuit_breaker service=$name state=HALF_OPEN probe already in-flight — fast-failing" }
            throw KeycloakCircuitBreakerOpenException(name)
        }

        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: KeycloakCircuitBreakerOpenException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            if (isInfrastructureFailure(e)) onFailure(e)
            else probeInFlight.set(false)
            throw e
        }
    }

    private fun onSuccess() {
        probeInFlight.set(false)
        val previous = state.getAndSet(State.CLOSED)
        failureCount.set(0)
        if (previous != State.CLOSED) logger.info { "circuit_breaker service=$name state=CLOSED recovered" }
    }

    private fun onFailure(cause: Exception) {
        probeInFlight.set(false)
        lastFailureTime.set(System.currentTimeMillis())
        val count = failureCount.incrementAndGet()
        logger.warn { "circuit_breaker service=$name failures=$count reason=${cause.message}" }
        if (count >= failureThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN) ||
                state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                logger.error { "circuit_breaker service=$name state=OPEN threshold reached" }
            }
        }
    }
}

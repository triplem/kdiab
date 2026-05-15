package org.javafreedom.kdiab.users.infrastructure.keycloak

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

class KeycloakCircuitBreakerOpenException(val service: String) :
    RuntimeException("Circuit breaker OPEN for '$service' — Keycloak Admin API unavailable")

class CircuitBreaker(
    val name: String,
    private val failureThreshold: Int = 5,
    private val resetTimeoutMs: Long = 30_000L,
) {
    private enum class State { CLOSED, OPEN, HALF_OPEN }

    private val state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0L)

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

        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: KeycloakCircuitBreakerOpenException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            onFailure(e)
            throw e
        }
    }

    private fun onSuccess() {
        val previous = state.getAndSet(State.CLOSED)
        failureCount.set(0)
        if (previous != State.CLOSED) logger.info { "circuit_breaker service=$name state=CLOSED recovered" }
    }

    private fun onFailure(cause: Exception) {
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

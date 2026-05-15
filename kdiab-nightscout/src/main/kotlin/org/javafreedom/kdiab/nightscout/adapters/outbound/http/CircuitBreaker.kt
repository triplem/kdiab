package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

private const val DEFAULT_FAILURE_THRESHOLD = 5
private const val DEFAULT_RESET_TIMEOUT_MS = 30_000L

class CircuitBreakerOpenException(val service: String) :
    RuntimeException("Circuit breaker OPEN for service '$service' — upstream is unavailable")

class CircuitBreaker(
    val name: String,
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val resetTimeoutMs: Long = DEFAULT_RESET_TIMEOUT_MS,
) {
    private enum class State { CLOSED, OPEN, HALF_OPEN }

    private val state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0L)

    val isOpen: Boolean get() = state.get() == State.OPEN

    suspend fun <T> execute(block: suspend () -> T): T {
        when (state.get()) {
            State.OPEN -> {
                val elapsed = System.currentTimeMillis() - lastFailureTime.get()
                if (elapsed >= resetTimeoutMs) {
                    state.compareAndSet(State.OPEN, State.HALF_OPEN)
                    logger.info { "circuit_breaker service=$name state=HALF_OPEN probing upstream" }
                } else {
                    logger.warn { "circuit_breaker service=$name state=OPEN fast-failing request" }
                    throw CircuitBreakerOpenException(name)
                }
            }
            else -> {}
        }

        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: CircuitBreakerOpenException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
            throw e
        }
    }

    private fun onSuccess() {
        val previous = state.getAndSet(State.CLOSED)
        failureCount.set(0)
        if (previous != State.CLOSED) {
            logger.info { "circuit_breaker service=$name state=CLOSED upstream recovered" }
        }
    }

    private fun onFailure(cause: Exception) {
        lastFailureTime.set(System.currentTimeMillis())
        val count = failureCount.incrementAndGet()
        logger.warn { "circuit_breaker service=$name failures=$count threshold=$failureThreshold reason=${cause.message}" }
        if (count >= failureThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN) ||
                state.compareAndSet(State.HALF_OPEN, State.OPEN)
            ) {
                logger.error { "circuit_breaker service=$name state=OPEN threshold reached — blocking upstream calls" }
            }
        }
    }
}

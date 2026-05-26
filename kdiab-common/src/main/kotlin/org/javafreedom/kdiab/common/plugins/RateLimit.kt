@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.common.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import org.javafreedom.kdiab.common.domain.exception.RateLimitExceededException
import java.util.concurrent.ConcurrentHashMap

private const val DEFAULT_RATE_LIMIT_REQUESTS = 300
private const val DEFAULT_RATE_LIMIT_WINDOW_SECONDS = 60L

private val EXEMPT_PATHS = setOf("/healthz", "/readyz", "/metrics", "/swagger", "/openapi.json")

internal fun resolveRateLimitConfig(env: (String) -> String? = System::getenv): Pair<Int, Long> {
    val requests = env("RATE_LIMIT_REQUESTS")?.toIntOrNull() ?: DEFAULT_RATE_LIMIT_REQUESTS
    val windowSeconds = env("RATE_LIMIT_WINDOW_SECONDS")?.toLongOrNull() ?: DEFAULT_RATE_LIMIT_WINDOW_SECONDS
    return requests to windowSeconds
}

private fun isExemptPath(path: String): Boolean =
    EXEMPT_PATHS.any { path == it || path.startsWith("$it/") }

private fun pruneOldEntries(deque: ArrayDeque<Long>, windowStart: Long) {
    while (deque.isNotEmpty() && deque.first() < windowStart) {
        deque.removeFirst()
    }
}

internal fun checkRateLimit(
    timestamps: ConcurrentHashMap<String, ArrayDeque<Long>>,
    userId: String,
    nowMs: Long,
    limit: Int,
    windowSeconds: Long,
): Int {
    val windowMs = windowSeconds * 1_000L
    val windowStart = nowMs - windowMs
    // computeIfAbsent is atomic — guarantees exactly one deque per userId under concurrency
    val deque = timestamps.computeIfAbsent(userId) { ArrayDeque() }
    synchronized(deque) {
        pruneOldEntries(deque, windowStart)
        if (deque.size >= limit) {
            val retryAfterSeconds = ((deque.first() + windowMs - nowMs) / 1_000L) + 1L
            throw RateLimitExceededException(
                retryAfterSeconds = retryAfterSeconds.coerceAtLeast(1L),
                limit = limit,
                userId = userId,
            )
        }
        deque.addLast(nowMs)
        val remaining = limit - deque.size
        // Evict empty deques to prevent unbounded map growth
        if (deque.isEmpty()) timestamps.remove(userId)
        return remaining
    }
}

val RateLimitPlugin = createApplicationPlugin("RateLimitPlugin") {
    val (limit, windowSeconds) = resolveRateLimitConfig()
    // Instance-scoped map: each plugin install gets its own counter store
    val requestTimestamps: ConcurrentHashMap<String, ArrayDeque<Long>> = ConcurrentHashMap()

    onCall { call ->
        val path = call.request.path()
        if (isExemptPath(path)) return@onCall

        val principal = call.principal<UserPrincipal>() ?: return@onCall
        val userId = principal.userId.toString()
        val remaining = checkRateLimit(requestTimestamps, userId, System.currentTimeMillis(), limit, windowSeconds)

        call.response.headers.append("X-RateLimit-Limit", limit.toString())
        call.response.headers.append("X-RateLimit-Remaining", remaining.toString())
    }
}

fun Application.configureRateLimit() {
    install(RateLimitPlugin)
}

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

private val requestTimestamps: ConcurrentHashMap<String, ArrayDeque<Long>> = ConcurrentHashMap()

internal fun resolveRateLimitConfig(): Pair<Int, Long> {
    val requests = System.getenv("RATE_LIMIT_REQUESTS")?.toIntOrNull() ?: DEFAULT_RATE_LIMIT_REQUESTS
    val windowSeconds = System.getenv("RATE_LIMIT_WINDOW_SECONDS")?.toLongOrNull()
        ?: DEFAULT_RATE_LIMIT_WINDOW_SECONDS
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
    userId: String,
    nowMs: Long,
    limit: Int,
    windowSeconds: Long,
): Int {
    val windowMs = windowSeconds * 1_000L
    val windowStart = nowMs - windowMs
    val deque = requestTimestamps.getOrPut(userId) { ArrayDeque() }
    synchronized(deque) {
        pruneOldEntries(deque, windowStart)
        if (deque.size >= limit) {
            throw RateLimitExceededException(
                retryAfterSeconds = windowSeconds,
                limit = limit,
            )
        }
        deque.addLast(nowMs)
        return limit - deque.size
    }
}

val RateLimitPlugin = createApplicationPlugin("RateLimitPlugin") {
    val (limit, windowSeconds) = resolveRateLimitConfig()

    onCall { call ->
        val path = call.request.path()
        if (isExemptPath(path)) return@onCall

        val principal = call.principal<UserPrincipal>() ?: return@onCall
        val userId = principal.userId.toString()
        val remaining = checkRateLimit(userId, System.currentTimeMillis(), limit, windowSeconds)

        call.response.headers.append("X-RateLimit-Limit", limit.toString())
        call.response.headers.append("X-RateLimit-Remaining", remaining.toString())
    }
}

fun Application.configureRateLimit() {
    install(RateLimitPlugin)
}

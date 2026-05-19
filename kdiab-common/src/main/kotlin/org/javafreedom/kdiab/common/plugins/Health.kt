package org.javafreedom.kdiab.common.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Port: determines whether the service is ready to handle traffic.
 */
fun interface HealthService {
    /**
     * Returns `true` if the service is healthy and ready to serve requests.
     */
    suspend fun isReady(): Boolean
}

/**
 * Default implementation backed by a database connection check.
 *
 * @param checkDb Suspend lambda that should return `true` when the database is reachable.
 *   Any exception thrown by [checkDb] is caught and treated as a failure.
 */
class DefaultHealthService(private val checkDb: suspend () -> Boolean) : HealthService {
    override suspend fun isReady(): Boolean =
        runCatching { checkDb() }.getOrDefault(false)
}

/**
 * Registers the `/healthz` (liveness) and `/readyz` (readiness) endpoints.
 *
 * - `/healthz` always returns **200 OK** — it signals the process is alive.
 * - `/readyz` delegates to [healthService] and returns **200 OK** or **503 Service Unavailable**.
 *
 * Call this inside your [Application.module] routing block:
 * ```kotlin
 * configureHealth(DefaultHealthService {
 *     withContext(Dispatchers.IO) { transaction { exec("SELECT 1") }; true }
 * })
 * ```
 */
fun Application.configureHealth(healthService: HealthService) {
    routing {
        get("/healthz") {
            call.respond(HttpStatusCode.OK)
        }
        get("/readyz") {
            if (healthService.isReady()) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable)
            }
        }
    }
}

package org.javafreedom.kdiab.common.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

/**
 * Installs the Ktor [CORS] plugin using origins read from `cors.allowedOrigins` in the
 * application config (comma-separated list, e.g. `http://localhost:3000,https://app.example.com`).
 *
 * Falls back to [defaultOrigins] when the config key is absent or blank.
 *
 * Standard headers `Content-Type` and `Authorization` and the methods provided via
 * [allowedMethods] are permitted.  The caller passes the methods so that read-only services
 * (e.g. kdiab-analyze) can omit PUT/DELETE.
 *
 * @param defaultOrigins Fallback list used when `cors.allowedOrigins` is not configured.
 * @param allowedMethods HTTP methods to expose.  Defaults to GET, POST, PUT, DELETE.
 */
fun Application.configureCors(
    defaultOrigins: List<String> = listOf("http://localhost:3000"),
    allowedMethods: List<HttpMethod> = listOf(
        HttpMethod.Get,
        HttpMethod.Post,
        HttpMethod.Put,
        HttpMethod.Delete,
    ),
) {
    val corsOrigins = environment.config.propertyOrNull("cors.allowedOrigins")
        ?.getString()?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
        ?.takeIf { it.isNotEmpty() }
        ?: defaultOrigins

    install(CORS) {
        corsOrigins.forEach { origin ->
            val scheme = if (origin.startsWith("https://")) "https" else "http"
            val host = origin.removePrefix("https://").removePrefix("http://")
            allowHost(host, schemes = listOf(scheme))
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowedMethods.forEach { allowMethod(it) }
    }
}

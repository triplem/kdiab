package org.javafreedom.kdiab.common.plugins

import io.ktor.server.application.*

/**
 * Installs the cross-cutting Ktor plugins that are identical across all kdiab services:
 * tracing, logging, metrics, and security.
 *
 * Service-specific plugins (routing, CORS, DefaultHeaders, ContentNegotiation, StatusPages
 * extra handlers) are intentionally excluded — they differ per service and must be
 * configured in each service's Application.module().
 */
fun Application.configureCommonPlugins() {
    configureTracing()
    configureLogging()
    configureMetrics()
    configureSecurity()
}

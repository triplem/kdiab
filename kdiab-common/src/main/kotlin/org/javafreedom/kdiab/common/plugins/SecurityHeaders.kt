package org.javafreedom.kdiab.common.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*

/**
 * Installs [DefaultHeaders] with the standard kdiab security headers:
 * - `Content-Security-Policy: default-src 'self'; script-src 'self'; object-src 'none'`
 * - `X-Content-Type-Options: nosniff`
 * - `X-Frame-Options: DENY`
 * - `Strict-Transport-Security: max-age=31536000; includeSubDomains`
 *   (only when `server.httpsEnabled=true` in the application config)
 *
 * The CSP and HSTS headers can be suppressed via the respective flags when a service
 * has unusual requirements, but the default should be left as-is for all kdiab services.
 *
 * @param includeCsp Whether to emit the `Content-Security-Policy` header (default `true`).
 */
fun Application.configureSecurityHeaders(includeCsp: Boolean = true) {
    val httpsEnabled = environment.config.propertyOrNull("server.httpsEnabled")
        ?.getString()?.toBoolean() ?: false

    install(DefaultHeaders) {
        if (includeCsp) {
            header(
                "Content-Security-Policy",
                "default-src 'self'; script-src 'self'; object-src 'none'",
            )
        }
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        // HSTS is only meaningful over HTTPS; sending it on plain HTTP confuses intermediaries.
        if (httpsEnabled) {
            header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        }
    }
}

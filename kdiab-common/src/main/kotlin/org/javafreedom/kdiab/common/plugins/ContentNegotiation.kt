package org.javafreedom.kdiab.common.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder

/**
 * Installs Ktor's [ContentNegotiation] plugin with a shared [Json] configuration.
 *
 * @param prettyPrint Whether to pretty-print JSON responses. Defaults to `false`.
 *   Configure via `json.prettyPrint` in `application.conf` (or the `JSON_PRETTY_PRINT` env var).
 * @param extraConfig Optional additional [Json] builder configuration applied after the defaults.
 *   Use this for service-specific needs such as [kotlinx.serialization.json.Json.explicitNulls].
 */
fun Application.configureContentNegotiation(
    prettyPrint: Boolean = false,
    extraConfig: JsonBuilder.() -> Unit = {},
) {
    install(ContentNegotiation) {
        json(
            Json {
                this.prettyPrint = prettyPrint
                ignoreUnknownKeys = true
                extraConfig()
            }
        )
    }
}

/**
 * Reads `json.prettyPrint` from [ApplicationConfig] and calls
 * [configureContentNegotiation] with the resolved flag.
 *
 * Prefer this overload in production [Application.module] functions so the flag is
 * driven by config without boilerplate in every service.
 */
fun Application.configureContentNegotiation(extraConfig: JsonBuilder.() -> Unit = {}) {
    val prettyPrint = environment.config
        .propertyOrNull("json.prettyPrint")
        ?.getString()?.toBoolean() ?: false
    configureContentNegotiation(prettyPrint = prettyPrint, extraConfig = extraConfig)
}

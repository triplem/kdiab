package org.javafreedom.kdiab.calc

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.calc.adapters.inbound.web.calcRoutes
import org.javafreedom.kdiab.calc.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.calc.application.service.DoseCalculationService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.statuspages.*
import org.javafreedom.kdiab.calc.domain.exception.UpstreamException
import org.javafreedom.kdiab.calc.plugins.configureMetrics
import org.javafreedom.kdiab.common.plugins.ErrorResponse
import org.javafreedom.kdiab.common.plugins.configureLogging
import org.javafreedom.kdiab.common.plugins.configureSecurity
import org.javafreedom.kdiab.common.plugins.configureStatusPages

private val logger = KotlinLogging.logger {}

private const val HTTP_SERVER_ERROR_STATUS = 500
private const val HTTP_CONNECT_TIMEOUT_MS_DEFAULT = 5_000L
private const val HTTP_REQUEST_TIMEOUT_MS_DEFAULT = 10_000L
private const val HTTP_SOCKET_TIMEOUT_MS_DEFAULT = 5_000L
private const val HTTP_RETRY_MAX_RETRIES_DEFAULT = 3
private const val HTTP_RETRY_MAX_DELAY_MS_DEFAULT = 8_000L

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("LongMethod")
fun Application.module(
    doseCalculationService: DoseCalculationService? = null,
) {
    configureLogging()
    configureMetrics()
    configureSecurity()
    configureStatusPages {
        exception<UpstreamException> { call, cause ->
            logger.error(cause) { "Upstream service error: ${cause.service}" }
            val status = HttpStatusCode.BadGateway
            call.respond(status, ErrorResponse(status.value, "Upstream service unavailable: ${cause.service}"))
        }
    }

    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    install(ContentNegotiation) { json(json) }

    val corsOrigins = environment.config.propertyOrNull("cors.allowedOrigins")
        ?.getString()?.split(",")?.map { it.trim() }
        ?: listOf("http://localhost:3000")
    install(CORS) {
        corsOrigins.forEach { origin ->
            val scheme = if (origin.startsWith("https://")) "https" else "http"
            val host = origin.removePrefix("https://").removePrefix("http://")
            allowHost(host, schemes = listOf(scheme))
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
    }
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
    }

    val resolvedService: DoseCalculationService

    if (doseCalculationService != null) {
        resolvedService = doseCalculationService
    } else {
        val connectTimeoutMs = environment.config.propertyOrNull("http.connectTimeoutMs")
            ?.getString()?.toLong() ?: HTTP_CONNECT_TIMEOUT_MS_DEFAULT
        val requestTimeoutMs = environment.config.propertyOrNull("http.requestTimeoutMs")
            ?.getString()?.toLong() ?: HTTP_REQUEST_TIMEOUT_MS_DEFAULT
        val socketTimeoutMs = environment.config.propertyOrNull("http.socketTimeoutMs")
            ?.getString()?.toLong() ?: HTTP_SOCKET_TIMEOUT_MS_DEFAULT
        val retryMaxRetries = environment.config.propertyOrNull("http.retryMaxRetries")
            ?.getString()?.toInt() ?: HTTP_RETRY_MAX_RETRIES_DEFAULT
        val retryMaxDelayMs = environment.config.propertyOrNull("http.retryMaxDelayMs")
            ?.getString()?.toLong() ?: HTTP_RETRY_MAX_DELAY_MS_DEFAULT

        val httpClient = HttpClient(CIO) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            install(io.ktor.client.plugins.HttpTimeout) {
                connectTimeoutMillis = connectTimeoutMs
                requestTimeoutMillis = requestTimeoutMs
                socketTimeoutMillis = socketTimeoutMs
            }
            install(io.ktor.client.plugins.HttpRequestRetry) {
                maxRetries = retryMaxRetries
                retryIf { _, response -> response.status.value >= HTTP_SERVER_ERROR_STATUS }
                retryOnExceptionIf { _, cause ->
                    cause is java.net.SocketTimeoutException ||
                        cause is io.ktor.client.plugins.HttpRequestTimeoutException
                }
                exponentialDelay(base = 2.0, maxDelayMs = retryMaxDelayMs)
            }
        }
        monitor.subscribe(ApplicationStopping) { httpClient.close() }

        val profilesUrl = environment.config.property("upstream.profilesUrl").getString()
        val profilesClient = ProfilesClient(httpClient.engine, profilesUrl)
        resolvedService = DoseCalculationService(profilesClient)
    }

    val swaggerEnabled = environment.config.propertyOrNull("swagger.enabled")?.getString()?.toBoolean() ?: false

    routing {
        get("/") { call.respondText("kdiab-calc is running!") }
        get("/healthz") { call.respond(HttpStatusCode.OK) }
        get("/readyz") { call.respond(HttpStatusCode.OK) }

        route("/api/v1") {
            calcRoutes(resolvedService)
        }

        if (swaggerEnabled) {
            swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
        }
    }
}

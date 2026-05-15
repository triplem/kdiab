package org.javafreedom.kdiab.nightscout

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.statuspages.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.common.plugins.ErrorResponse
import org.javafreedom.kdiab.common.plugins.configureLogging
import org.javafreedom.kdiab.common.plugins.configureSecurity
import org.javafreedom.kdiab.common.plugins.configureStatusPages
import org.javafreedom.kdiab.common.plugins.configureTracing
import org.javafreedom.kdiab.nightscout.adapters.inbound.web.nightscoutRoutes
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.CircuitBreakerOpenException
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.nightscout.application.service.NightscoutService
import org.javafreedom.kdiab.nightscout.domain.exception.UpstreamException
import org.javafreedom.kdiab.nightscout.domain.model.NightscoutStatus
import org.javafreedom.kdiab.nightscout.plugins.configureMetrics

private val logger = KotlinLogging.logger {}

private const val HTTP_CONNECT_TIMEOUT_MS_DEFAULT = 5_000L
private const val HTTP_REQUEST_TIMEOUT_MS_DEFAULT = 30_000L
private const val HTTP_SOCKET_TIMEOUT_MS_DEFAULT = 5_000L

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module(nightscoutService: NightscoutService? = null) {
    configureTracing()
    configureLogging()
    configureMetrics()
    configureSecurity()
    configureStatusPages {
        exception<CircuitBreakerOpenException> { call, cause ->
            logger.warn { "circuit_breaker service=${cause.service} state=OPEN returning 503" }
            val status = HttpStatusCode.ServiceUnavailable
            call.respond(status, ErrorResponse(status.value, "Service temporarily unavailable: ${cause.service}"))
        }
        exception<UpstreamException> { call, cause ->
            logger.error(cause) { "Upstream service error: ${cause.service}" }
            val status = HttpStatusCode.BadGateway
            call.respond(status, ErrorResponse(status.value, "Upstream service unavailable: ${cause.service}"))
        }
    }

    val json = Json { ignoreUnknownKeys = true }
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
        allowMethod(HttpMethod.Get)
    }
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
    }

    val resolvedService: NightscoutService = nightscoutService ?: run {
        val connectTimeoutMs = environment.config.propertyOrNull("http.connectTimeoutMs")
            ?.getString()?.toLong() ?: HTTP_CONNECT_TIMEOUT_MS_DEFAULT
        val requestTimeoutMs = environment.config.propertyOrNull("http.requestTimeoutMs")
            ?.getString()?.toLong() ?: HTTP_REQUEST_TIMEOUT_MS_DEFAULT
        val socketTimeoutMs = environment.config.propertyOrNull("http.socketTimeoutMs")
            ?.getString()?.toLong() ?: HTTP_SOCKET_TIMEOUT_MS_DEFAULT

        val httpClient = HttpClient(CIO) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            install(io.ktor.client.plugins.HttpTimeout) {
                connectTimeoutMillis = connectTimeoutMs
                requestTimeoutMillis = requestTimeoutMs
                socketTimeoutMillis = socketTimeoutMs
            }
        }
        monitor.subscribe(ApplicationStopping) { httpClient.close() }

        val measuresUrl = environment.config.property("upstream.measuresUrl").getString()
        val treatmentsUrl = environment.config.property("upstream.treatmentsUrl").getString()

        NightscoutService(
            measuresClient = MeasuresClient(httpClient.engine, measuresUrl),
            treatmentsClient = TreatmentsClient(httpClient.engine, treatmentsUrl),
        )
    }

    routing {
        get("/healthz") { call.respond(HttpStatusCode.OK) }
        get("/readyz") { call.respond(HttpStatusCode.OK) }

        get("/api/v1/status.json") {
            val nowMs = System.currentTimeMillis()
            val nowStr = java.time.Instant.ofEpochMilli(nowMs).toString()
            call.respond(
                NightscoutStatus(
                    status = "ok",
                    apiEnabled = true,
                    serverTime = nowStr,
                    serverTimeEpoch = nowMs,
                )
            )
        }

        nightscoutRoutes(resolvedService)
    }
}

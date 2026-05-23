package org.javafreedom.kdiab.nightscout

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.get
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.statuspages.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.common.plugins.ErrorResponse
import org.javafreedom.kdiab.common.plugins.HTTP_CONNECT_TIMEOUT_MS_DEFAULT
import org.javafreedom.kdiab.common.plugins.HTTP_SOCKET_TIMEOUT_MS_DEFAULT
import org.javafreedom.kdiab.common.plugins.HealthService
import org.javafreedom.kdiab.common.plugins.configureHealth
import org.javafreedom.kdiab.common.plugins.configureLogging
import org.javafreedom.kdiab.common.plugins.configureCors
import org.javafreedom.kdiab.common.plugins.configureSecurityHeaders
import org.javafreedom.kdiab.common.plugins.configureSecurity
import org.javafreedom.kdiab.common.plugins.configureStatusPages
import org.javafreedom.kdiab.common.plugins.configureTracing
import org.javafreedom.kdiab.nightscout.adapters.inbound.web.nightscoutRoutes
import org.javafreedom.kdiab.common.plugins.CircuitBreakerOpenException
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.nightscout.application.service.NightscoutService
import org.javafreedom.kdiab.nightscout.domain.exception.UpstreamException
import org.javafreedom.kdiab.nightscout.domain.model.NightscoutStatus
import org.javafreedom.kdiab.common.plugins.configureMetrics

private val logger = KotlinLogging.logger {}

// Nightscout bridges AAPS, xDrip+, and Juggluco which may be on slow mobile connections.
// The upstream common default of 10 s is too tight for these clients; keep 30 s here.
private const val HTTP_REQUEST_TIMEOUT_MS = 30_000L

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val json = Json { ignoreUnknownKeys = true }

    var healthClient: HttpClient? = null
    var upstreamHealthUrls: List<String> = emptyList()

    // Install DI with production bindings only if not already installed by tests.
    // Tests install DI with mock overrides before calling module().
    if (pluginOrNull(DI) == null) {
        val connectTimeoutMs = environment.config.propertyOrNull("http.connectTimeoutMs")
            ?.getString()?.toLong() ?: HTTP_CONNECT_TIMEOUT_MS_DEFAULT
        val requestTimeoutMs = environment.config.propertyOrNull("http.requestTimeoutMs")
            ?.getString()?.toLong() ?: HTTP_REQUEST_TIMEOUT_MS
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

        healthClient = httpClient
        upstreamHealthUrls = listOf("$measuresUrl/healthz", "$treatmentsUrl/healthz")

        install(DI) { }
        dependencies {
            provide<NightscoutService> {
                NightscoutService(
                    measuresClient = MeasuresClient(httpClient.engine, measuresUrl),
                    treatmentsClient = TreatmentsClient(httpClient.engine, treatmentsUrl),
                )
            }
        }
    }

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

    install(ContentNegotiation) { json(json) }

    // Nightscout clients (AAPS, xDrip+, Juggluco) use GET/POST; restrict to GET at browser level.
    configureCors(allowedMethods = listOf(HttpMethod.Get))
    configureSecurityHeaders(includeCsp = false)

    val capturedHealthClient = healthClient
    val capturedUrls = upstreamHealthUrls
    configureHealth(HealthService {
        if (capturedHealthClient == null || capturedUrls.isEmpty()) {
            true
        } else {
            capturedUrls.all { url ->
                runCatching { capturedHealthClient.get(url).status.isSuccess() }.getOrDefault(false)
            }
        }
    })

    val nightscoutService: NightscoutService by dependencies

    routing {

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

        nightscoutRoutes(nightscoutService)
    }
}

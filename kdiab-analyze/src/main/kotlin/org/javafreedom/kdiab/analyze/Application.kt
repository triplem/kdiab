package org.javafreedom.kdiab.analyze

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.analyze.adapters.inbound.web.bffRoutes
import org.javafreedom.kdiab.analyze.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.analyze.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.analyze.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.analyze.application.service.AnalyticsOperation
import org.javafreedom.kdiab.analyze.application.service.AnalyticsService
import org.javafreedom.kdiab.analyze.application.service.DeviceUsageOperation
import org.javafreedom.kdiab.analyze.application.service.DeviceUsageService
import org.javafreedom.kdiab.analyze.application.service.ProfilesOperation
import org.javafreedom.kdiab.analyze.application.service.ProfilesService
import org.javafreedom.kdiab.analyze.application.service.TimelineOperation
import org.javafreedom.kdiab.analyze.application.service.TimelineService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.statuspages.*
import org.javafreedom.kdiab.analyze.adapters.outbound.http.CircuitBreakerOpenException
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException
import org.javafreedom.kdiab.analyze.plugins.configureMetrics
import org.javafreedom.kdiab.common.plugins.ErrorResponse
import org.javafreedom.kdiab.common.plugins.configureLogging
import org.javafreedom.kdiab.common.plugins.configureSecurity
import org.javafreedom.kdiab.common.plugins.configureStatusPages
import org.javafreedom.kdiab.common.plugins.configureTracing

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
    timelineService: TimelineOperation? = null,
    analyticsService: AnalyticsOperation? = null,
    profilesService: ProfilesOperation? = null,
    deviceUsageService: DeviceUsageOperation? = null,
    treatmentsClient: TreatmentsClient? = null,
) {
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

    val prettyPrint = environment.config.propertyOrNull("json.prettyPrint")
        ?.getString()?.toBoolean() ?: false
    val json = Json {
        this.prettyPrint = prettyPrint
        ignoreUnknownKeys = true
    }

    install(Resources)
    install(ContentNegotiation) { json(json) }

    val corsOrigins = environment.config.propertyOrNull("cors.allowedOrigins")
        ?.getString()?.split(",")?.map { it.trim() }
        ?: listOf("http://localhost:3003")
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
        header("Content-Security-Policy", "default-src 'self'; script-src 'self'; object-src 'none'")
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
    }

    val resolvedTimelineService: TimelineOperation
    val resolvedAnalyticsService: AnalyticsOperation
    val resolvedProfilesService: ProfilesOperation
    val resolvedDeviceUsageService: DeviceUsageOperation
    var resolvedTreatmentsClient: TreatmentsClient? = treatmentsClient
    var healthClient: HttpClient? = null
    var upstreamHealthUrls: List<String> = emptyList()

    if (allServicesProvided(timelineService, analyticsService, profilesService, deviceUsageService)) {
        resolvedTimelineService = requireNotNull(timelineService)
        resolvedAnalyticsService = requireNotNull(analyticsService)
        resolvedProfilesService = requireNotNull(profilesService)
        resolvedDeviceUsageService = requireNotNull(deviceUsageService)
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
                retryIf { request, response ->
                    request.method == io.ktor.http.HttpMethod.Get &&
                        response.status.value >= HTTP_SERVER_ERROR_STATUS
                }
                retryOnExceptionIf { _, cause ->
                    cause is java.net.SocketTimeoutException ||
                        cause is io.ktor.client.plugins.HttpRequestTimeoutException ||
                        cause is java.net.ConnectException
                }
                exponentialDelay(base = 2.0, maxDelayMs = retryMaxDelayMs)
            }
        }
        monitor.subscribe(ApplicationStopping) { httpClient.close() }
        val measuresUrl = environment.config.property("upstream.measuresUrl").getString()
        val profilesUrl = environment.config.property("upstream.profilesUrl").getString()
        val treatmentsUrl = environment.config.property("upstream.treatmentsUrl").getString()

        healthClient = httpClient
        upstreamHealthUrls = listOf("$measuresUrl/healthz", "$profilesUrl/healthz", "$treatmentsUrl/healthz")

        val measuresClient = MeasuresClient(httpClient.engine, measuresUrl)
        val realProfilesClient = ProfilesClient(httpClient.engine, profilesUrl)
        val realTreatmentsClient = TreatmentsClient(httpClient.engine, treatmentsUrl)

        resolvedTimelineService = TimelineService(measuresClient, realTreatmentsClient)
        resolvedAnalyticsService = AnalyticsService(measuresClient, realProfilesClient)
        resolvedProfilesService = ProfilesService(realProfilesClient)
        resolvedDeviceUsageService = DeviceUsageService(realTreatmentsClient)
        resolvedTreatmentsClient = realTreatmentsClient
    }

    val swaggerEnabled = environment.config.propertyOrNull("swagger.enabled")?.getString()?.toBoolean() ?: false

    routing {
        get("/") { call.respondText("kdiab BFF is running!") }
        get("/healthz") { call.respond(HttpStatusCode.OK) }
        get("/readyz") {
            val client = healthClient
            if (client == null || upstreamHealthUrls.isEmpty()) {
                call.respond(HttpStatusCode.OK)
            } else {
                val allReady = upstreamHealthUrls.all { url ->
                    runCatching { client.get(url).status.isSuccess() }
                        .getOrDefault(false)
                }
                if (allReady) call.respond(HttpStatusCode.OK)
                else call.respond(HttpStatusCode.ServiceUnavailable)
            }
        }

        route("/api/v1") {
            bffRoutes(
                resolvedTimelineService,
                resolvedAnalyticsService,
                resolvedProfilesService,
                resolvedDeviceUsageService,
                resolvedTreatmentsClient,
            )
        }

        if (swaggerEnabled) {
            swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
        }
    }
}

private fun allServicesProvided(
    timelineService: TimelineOperation?,
    analyticsService: AnalyticsOperation?,
    profilesService: ProfilesOperation?,
    deviceUsageService: DeviceUsageOperation?,
): Boolean = listOf(timelineService, analyticsService, profilesService, deviceUsageService)
    .all { it != null }

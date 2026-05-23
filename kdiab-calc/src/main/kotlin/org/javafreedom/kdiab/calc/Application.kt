package org.javafreedom.kdiab.calc

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.get
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.calc.adapters.inbound.web.calcRoutes
import org.javafreedom.kdiab.calc.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.calc.application.service.DoseCalculationService
import org.javafreedom.kdiab.calc.domain.repository.ProfilesPort
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.statuspages.*
import org.javafreedom.kdiab.calc.domain.exception.UpstreamException
import org.javafreedom.kdiab.common.plugins.ErrorResponse
import org.javafreedom.kdiab.common.plugins.HTTP_CONNECT_TIMEOUT_MS_DEFAULT
import org.javafreedom.kdiab.common.plugins.HTTP_REQUEST_TIMEOUT_MS_DEFAULT
import org.javafreedom.kdiab.common.plugins.HTTP_RETRY_MAX_DELAY_MS_DEFAULT
import org.javafreedom.kdiab.common.plugins.HTTP_RETRY_MAX_RETRIES_DEFAULT
import org.javafreedom.kdiab.common.plugins.HTTP_SERVER_ERROR_STATUS
import org.javafreedom.kdiab.common.plugins.HTTP_SOCKET_TIMEOUT_MS_DEFAULT
import org.javafreedom.kdiab.common.plugins.HealthService
import org.javafreedom.kdiab.common.plugins.configureCommonPlugins
import org.javafreedom.kdiab.common.plugins.configureContentNegotiation
import org.javafreedom.kdiab.common.plugins.configureCors
import org.javafreedom.kdiab.common.plugins.configureHealth
import org.javafreedom.kdiab.common.plugins.configureSecurityHeaders
import org.javafreedom.kdiab.common.plugins.configureStatusPages

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

@Suppress("LongMethod")
fun Application.module() {
    // Build the shared Json instance for the HTTP client.
    val prettyPrint = environment.config.propertyOrNull("json.prettyPrint")
        ?.getString()?.toBoolean() ?: false
    val json = Json {
        this.prettyPrint = prettyPrint
        ignoreUnknownKeys = true
    }

    var healthClient: HttpClient? = null
    var upstreamHealthUrls: List<String> = emptyList()

    // Install DI with production bindings only if not already installed by tests.
    // Tests install DI with mock overrides before calling module().
    if (pluginOrNull(DI) == null) {
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

        healthClient = httpClient
        upstreamHealthUrls = listOf("$profilesUrl/healthz")

        install(DI) { }
        dependencies {
            provide<ProfilesPort> { ProfilesClient(httpClient.engine, profilesUrl) }
            provide<DoseCalculationService> {
                DoseCalculationService(resolve<ProfilesPort>())
            }
        }
    }

    configureCommonPlugins()
    configureStatusPages {
        exception<UpstreamException> { call, cause ->
            logger.error(cause) { "Upstream service error: ${cause.service}" }
            val status = HttpStatusCode.BadGateway
            call.respond(status, ErrorResponse(status.value, "Upstream service unavailable: ${cause.service}"))
        }
    }

    configureContentNegotiation()

    install(Resources)

    // calc supports GET (health/status) and POST (dose calculation).
    configureCors(allowedMethods = listOf(HttpMethod.Get, HttpMethod.Post))
    configureSecurityHeaders()

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

    val swaggerEnabled = environment.config.propertyOrNull("swagger.enabled")?.getString()?.toBoolean() ?: false

    val doseCalculationService: DoseCalculationService by dependencies

    routing {
        get("/") { call.respondText("kdiab-calc is running!") }

        route("/api/v1") {
            calcRoutes(doseCalculationService)
        }

        if (swaggerEnabled) {
            swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
        }
    }
}

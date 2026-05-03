package org.javafreedom.kdiab.analyze

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
import org.javafreedom.kdiab.analyze.adapters.inbound.web.bffRoutes
import org.javafreedom.kdiab.analyze.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.analyze.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.analyze.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.analyze.application.service.AnalyticsService
import org.javafreedom.kdiab.analyze.application.service.ProfilesService
import org.javafreedom.kdiab.analyze.application.service.TimelineService
import org.javafreedom.kdiab.analyze.plugins.configureLogging
import org.javafreedom.kdiab.analyze.plugins.configureMetrics
import org.javafreedom.kdiab.analyze.plugins.configureSecurity
import org.javafreedom.kdiab.analyze.plugins.configureStatusPages

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("LongMethod")
fun Application.module(
    timelineService: TimelineService? = null,
    analyticsService: AnalyticsService? = null,
    profilesService: ProfilesService? = null,
) {
    configureLogging()
    configureMetrics()
    configureSecurity()
    configureStatusPages()

    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

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
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
    }

    val resolvedTimelineService: TimelineService
    val resolvedAnalyticsService: AnalyticsService
    val resolvedProfilesService: ProfilesService

    if (timelineService != null && analyticsService != null && profilesService != null) {
        resolvedTimelineService = timelineService
        resolvedAnalyticsService = analyticsService
        resolvedProfilesService = profilesService
    } else {
        val httpClient = HttpClient(CIO) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(json) }
        }
        val measuresUrl = environment.config.property("upstream.measuresUrl").getString()
        val profilesUrl = environment.config.property("upstream.profilesUrl").getString()
        val treatmentsUrl = environment.config.property("upstream.treatmentsUrl").getString()

        val measuresClient = MeasuresClient(httpClient, measuresUrl)
        val profilesClient = ProfilesClient(httpClient, profilesUrl)
        val treatmentsClient = TreatmentsClient(httpClient, treatmentsUrl)

        resolvedTimelineService = TimelineService(measuresClient, treatmentsClient)
        resolvedAnalyticsService = AnalyticsService(measuresClient)
        resolvedProfilesService = ProfilesService(profilesClient)
    }

    val swaggerEnabled = environment.config.propertyOrNull("swagger.enabled")?.getString()?.toBoolean() ?: false

    routing {
        get("/") { call.respondText("kdiab BFF is running!") }
        get("/healthz") { call.respond(HttpStatusCode.OK) }

        bffRoutes(resolvedTimelineService, resolvedAnalyticsService, resolvedProfilesService)

        if (swaggerEnabled) {
            swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
        }
    }
}

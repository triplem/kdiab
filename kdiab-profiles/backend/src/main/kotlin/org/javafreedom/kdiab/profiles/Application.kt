package org.javafreedom.kdiab.profiles

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.profiles.adapters.inbound.web.profileRoutes
import org.javafreedom.kdiab.profiles.adapters.inbound.web.insulinRoutes
import org.javafreedom.kdiab.profiles.application.service.InsulinService
import org.javafreedom.kdiab.profiles.application.service.ProfileService
import org.javafreedom.kdiab.profiles.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.profiles.infrastructure.persistence.ExposedProfileRepository
import org.javafreedom.kdiab.profiles.infrastructure.persistence.ExposedInsulinRepository
import org.javafreedom.kdiab.profiles.plugins.configureLogging
import org.javafreedom.kdiab.profiles.plugins.configureMetrics
import org.javafreedom.kdiab.profiles.plugins.configureSecurity
import org.javafreedom.kdiab.profiles.plugins.configureStatusPages
import io.ktor.server.plugins.swagger.*
import io.ktor.server.resources.Resources

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module(
        profileService: ProfileService = ProfileService(ExposedProfileRepository()),
        insulinService: InsulinService = InsulinService(ExposedInsulinRepository()),
        initDatabase: Boolean = true
) {
    configureLogging()
    configureMetrics()
    configureSecurity()
    configureStatusPages()
    install(ContentNegotiation) {
        json(
                Json {
                    prettyPrint = false
                    ignoreUnknownKeys = true
                }
        )
    }
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
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
    }
    install(Resources)

    // Initialize Database if requested
    if (initDatabase) {
        DatabaseFactory.init(environment.config)
    }

    routing {
        get("/") { call.respondText("T1D Profile Service is running!") }
        get("/healthz") { call.respond(io.ktor.http.HttpStatusCode.OK) }

        route("/api/v1") {
            profileRoutes(profileService)
        }
        insulinRoutes(insulinService)

        swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
    }
}

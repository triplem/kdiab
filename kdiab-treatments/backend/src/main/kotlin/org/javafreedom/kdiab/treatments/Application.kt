package org.javafreedom.kdiab.treatments

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.treatments.adapters.inbound.web.treatmentRoutes
import org.javafreedom.kdiab.treatments.application.service.TreatmentService
import org.javafreedom.kdiab.treatments.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.treatments.infrastructure.persistence.ExposedTreatmentRepository
import org.javafreedom.kdiab.treatments.plugins.configureLogging
import org.javafreedom.kdiab.treatments.plugins.configureSecurity
import org.javafreedom.kdiab.treatments.plugins.configureStatusPages

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module(
    treatmentService: TreatmentService = TreatmentService(ExposedTreatmentRepository()),
    initDatabase: Boolean = true
) {
    configureLogging()
    configureSecurity()
    configureStatusPages()
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
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = false
                ignoreUnknownKeys = true
            }
        )
    }
    install(Resources)

    if (initDatabase) {
        DatabaseFactory.init(environment.config)
    }

    routing {
        get("/") { call.respondText("T1D Treatments Service is running!") }
        get("/healthz") { call.respond(io.ktor.http.HttpStatusCode.OK) }

        route("/api/v1") {
            treatmentRoutes(treatmentService)
        }

        swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
    }
}

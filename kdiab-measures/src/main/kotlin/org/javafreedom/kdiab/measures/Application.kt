package org.javafreedom.kdiab.measures

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
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.measures.adapters.inbound.web.auditRoutes
import org.javafreedom.kdiab.measures.adapters.inbound.web.measureRoutes
import org.javafreedom.kdiab.measures.application.service.MeasureService
import org.javafreedom.kdiab.measures.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.measures.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.measures.infrastructure.persistence.ExposedAuditLogRepository
import org.javafreedom.kdiab.measures.infrastructure.persistence.ExposedMeasureRepository
import org.javafreedom.kdiab.measures.plugins.configureLogging
import org.javafreedom.kdiab.measures.plugins.configureMetrics
import org.javafreedom.kdiab.measures.plugins.configureSecurity
import org.javafreedom.kdiab.measures.plugins.configureStatusPages

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module(
    measureService: MeasureService = MeasureService(ExposedMeasureRepository()),
    auditLogRepository: AuditLogRepository = ExposedAuditLogRepository(),
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
    install(Resources)
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

    if (initDatabase) {
        DatabaseFactory.init(environment.config)
    }

    val swaggerEnabled = environment.config.propertyOrNull("swagger.enabled")?.getString()?.toBoolean() ?: false

    routing {
        get("/") { call.respondText("T1D Measures Service is running!") }
        get("/healthz") { call.respond(io.ktor.http.HttpStatusCode.OK) }
        get("/readyz") {
            val ready = runCatching {
                transaction { exec("SELECT 1") }
            }.isSuccess
            if (ready) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.ServiceUnavailable)
        }

        route("/api/v1") {
            measureRoutes(measureService, auditLogRepository)
            auditRoutes(auditLogRepository)
        }

        if (swaggerEnabled) {
            swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
        }
    }
}

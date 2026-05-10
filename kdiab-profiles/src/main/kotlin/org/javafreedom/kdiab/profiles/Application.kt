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
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.profiles.adapters.inbound.web.auditRoutes
import org.javafreedom.kdiab.profiles.adapters.inbound.web.profileRoutes
import org.javafreedom.kdiab.profiles.adapters.inbound.web.insulinRoutes
import org.javafreedom.kdiab.profiles.application.service.InsulinService
import org.javafreedom.kdiab.profiles.application.service.ProfileService
import org.javafreedom.kdiab.profiles.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.profiles.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.profiles.infrastructure.persistence.ExposedAuditLogRepository
import org.javafreedom.kdiab.profiles.infrastructure.persistence.ExposedProfileRepository
import org.javafreedom.kdiab.profiles.infrastructure.persistence.ExposedInsulinRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.resources.Resources
import org.javafreedom.kdiab.common.plugins.ErrorResponse
import org.javafreedom.kdiab.common.plugins.configureLogging
import org.javafreedom.kdiab.common.plugins.configureSecurity
import org.javafreedom.kdiab.common.plugins.configureStatusPages
import org.javafreedom.kdiab.profiles.plugins.configureMetrics
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module(
        profileService: ProfileService = ProfileService(ExposedProfileRepository()),
        insulinService: InsulinService = InsulinService(ExposedInsulinRepository()),
        auditLogRepository: AuditLogRepository = ExposedAuditLogRepository(),
        initDatabase: Boolean = true
) {
    configureLogging()
    configureMetrics()
    configureSecurity()
    configureStatusPages {
        exception<ExposedSQLException> { call, cause ->
            val sqlState = cause.cause?.let { (it as? java.sql.SQLException)?.sqlState }
            if (sqlState == "23505") {
                logger.warn(cause) { "Unique constraint violation" }
                call.respond(HttpStatusCode.Conflict,
                    ErrorResponse(HttpStatusCode.Conflict.value, cause.message ?: "Conflict"))
            } else {
                logger.error(cause) { "Database error (SQL state: $sqlState)" }
                call.respond(HttpStatusCode.InternalServerError,
                    ErrorResponse(HttpStatusCode.InternalServerError.value, "Internal Server Error"))
            }
        }
    }
    val prettyPrint = environment.config.propertyOrNull("json.prettyPrint")
        ?.getString()?.toBoolean() ?: false
    install(ContentNegotiation) {
        json(
                Json {
                    this.prettyPrint = prettyPrint
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

    // Initialize Database if requested
    if (initDatabase) {
        DatabaseFactory.init(environment.config)
    }

    val swaggerEnabled = environment.config.propertyOrNull("swagger.enabled")?.getString()?.toBoolean() ?: false

    routing {
        get("/") { call.respondText("T1D Profile Service is running!") }
        get("/healthz") { call.respond(io.ktor.http.HttpStatusCode.OK) }
        get("/readyz") {
            val ready = runCatching {
                transaction { exec("SELECT 1") }
            }.isSuccess
            if (ready) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.ServiceUnavailable)
        }

        route("/api/v1") {
            profileRoutes(profileService, auditLogRepository)
            auditRoutes(auditLogRepository)
        }
        insulinRoutes(insulinService)

        if (swaggerEnabled) {
            swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
        }
    }
}

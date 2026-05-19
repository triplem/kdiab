package org.javafreedom.kdiab.profiles

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.javafreedom.kdiab.profiles.adapters.inbound.web.auditRoutes
import org.javafreedom.kdiab.profiles.adapters.inbound.web.insulinRoutes
import org.javafreedom.kdiab.profiles.adapters.inbound.web.profileRoutes
import org.javafreedom.kdiab.profiles.application.service.InsulinService
import org.javafreedom.kdiab.profiles.application.service.ProfileService
import org.javafreedom.kdiab.profiles.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.profiles.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.profiles.infrastructure.persistence.ExposedAuditLogRepository
import org.javafreedom.kdiab.profiles.infrastructure.persistence.ExposedInsulinRepository
import org.javafreedom.kdiab.profiles.infrastructure.persistence.ExposedProfileRepository
import org.javafreedom.kdiab.common.plugins.DefaultHealthService
import org.javafreedom.kdiab.common.plugins.configureCommonPlugins
import org.javafreedom.kdiab.common.plugins.configureContentNegotiation
import org.javafreedom.kdiab.common.plugins.configureHealth
import org.javafreedom.kdiab.common.plugins.configureStatusPages

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module(
    profileService: ProfileService = ProfileService(ExposedProfileRepository()),
    insulinService: InsulinService = InsulinService(ExposedInsulinRepository()),
    auditLogRepository: AuditLogRepository = ExposedAuditLogRepository(),
    initDatabase: Boolean = true
) {
    configureCommonPlugins()
    configureStatusPages()
    configureContentNegotiation()
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
        header("Content-Security-Policy", "default-src 'self'; script-src 'self'; object-src 'none'")
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
    }

    // Initialize Database if requested
    if (initDatabase) {
        DatabaseFactory.init(environment.config)
    }

    configureHealth(DefaultHealthService {
        withContext(Dispatchers.IO) {
            transaction { exec("SELECT 1") }
            true
        }
    })

    val swaggerEnabled = environment.config.propertyOrNull("swagger.enabled")?.getString()?.toBoolean() ?: false

    routing {
        get("/") { call.respondText("T1D Profile Service is running!") }

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

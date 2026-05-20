package org.javafreedom.kdiab.treatments

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.javafreedom.kdiab.treatments.adapters.inbound.web.deviceAgeRoutes
import org.javafreedom.kdiab.treatments.adapters.inbound.web.deviceStatusRoutes
import org.javafreedom.kdiab.treatments.adapters.inbound.web.treatmentRoutes
import org.javafreedom.kdiab.treatments.application.service.DeviceStatusService
import org.javafreedom.kdiab.treatments.application.service.TreatmentService
import org.javafreedom.kdiab.common.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.common.plugins.auditRoutes
import org.javafreedom.kdiab.treatments.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.treatments.infrastructure.persistence.ExposedAuditLogRepository
import org.javafreedom.kdiab.treatments.infrastructure.persistence.ExposedDeviceStatusRepository
import org.javafreedom.kdiab.treatments.infrastructure.persistence.ExposedTreatmentRepository
import org.javafreedom.kdiab.common.plugins.DefaultHealthService
import org.javafreedom.kdiab.common.plugins.configureCommonPlugins
import org.javafreedom.kdiab.common.plugins.configureContentNegotiation
import org.javafreedom.kdiab.common.plugins.configureHealth
import org.javafreedom.kdiab.common.plugins.configureStatusPages

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val initDatabase = environment.config.propertyOrNull("app.initDatabase")?.getString()?.toBoolean() ?: true

    if (pluginOrNull(DI) == null) {
        install(DI) { }
        dependencies {
            provide<TreatmentService> { TreatmentService(ExposedTreatmentRepository()) }
            provide<DeviceStatusService> { DeviceStatusService(ExposedDeviceStatusRepository()) }
            provide<AuditLogRepository> { ExposedAuditLogRepository() }
        }
    }

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

    val treatmentService: TreatmentService by dependencies
    val deviceStatusService: DeviceStatusService by dependencies
    val auditLogRepository: AuditLogRepository by dependencies

    routing {
        get("/") { call.respondText("T1D Treatments Service is running!") }

        route("/api/v1") {
            treatmentRoutes(treatmentService, deviceStatusService, auditLogRepository)
            deviceStatusRoutes(deviceStatusService)
            deviceAgeRoutes(treatmentService)
            auditRoutes(auditLogRepository)
        }

        if (swaggerEnabled) {
            swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
        }
    }
}

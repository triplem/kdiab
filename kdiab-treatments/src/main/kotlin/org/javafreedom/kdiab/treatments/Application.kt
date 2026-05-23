package org.javafreedom.kdiab.treatments

import io.ktor.server.application.*
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
import org.javafreedom.kdiab.common.infrastructure.persistence.ExposedAuditLogRepository
import org.javafreedom.kdiab.treatments.infrastructure.persistence.ExposedDeviceStatusRepository
import org.javafreedom.kdiab.treatments.infrastructure.persistence.ExposedTreatmentRepository
import org.javafreedom.kdiab.common.plugins.DefaultHealthService
import org.javafreedom.kdiab.common.plugins.configureCommonPlugins
import org.javafreedom.kdiab.common.plugins.configureContentNegotiation
import org.javafreedom.kdiab.common.plugins.configureCors
import org.javafreedom.kdiab.common.plugins.configureHealth
import org.javafreedom.kdiab.common.plugins.configureSecurityHeaders
import org.javafreedom.kdiab.common.plugins.configureStatusPages

fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

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
    configureCors()
    configureSecurityHeaders()

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

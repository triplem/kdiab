package org.javafreedom.kdiab.measures

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.javafreedom.kdiab.measures.adapters.inbound.web.measureRoutes
import org.javafreedom.kdiab.measures.application.service.MeasureService
import org.javafreedom.kdiab.common.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.common.plugins.auditRoutes
import org.javafreedom.kdiab.measures.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.measures.infrastructure.persistence.ExposedAuditLogRepository
import org.javafreedom.kdiab.measures.infrastructure.persistence.ExposedMeasureRepository
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
            provide<MeasureService> { MeasureService(ExposedMeasureRepository()) }
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

    val measureService: MeasureService by dependencies
    val auditLogRepository: AuditLogRepository by dependencies

    routing {
        get("/") { call.respondText("T1D Measures Service is running!") }

        route("/api/v1") {
            measureRoutes(measureService, auditLogRepository)
            auditRoutes(auditLogRepository)
        }

        if (swaggerEnabled) {
            swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
        }
    }
}

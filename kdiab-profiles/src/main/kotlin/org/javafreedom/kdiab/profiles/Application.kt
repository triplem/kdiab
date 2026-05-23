package org.javafreedom.kdiab.profiles

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.javafreedom.kdiab.profiles.adapters.inbound.web.insulinRoutes
import org.javafreedom.kdiab.profiles.adapters.inbound.web.profileRoutes
import org.javafreedom.kdiab.profiles.application.service.InsulinService
import org.javafreedom.kdiab.profiles.application.service.ProfileService
import org.javafreedom.kdiab.common.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.common.plugins.auditRoutes
import org.javafreedom.kdiab.profiles.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.profiles.infrastructure.persistence.ExposedAuditLogRepository
import org.javafreedom.kdiab.profiles.infrastructure.persistence.ExposedInsulinRepository
import org.javafreedom.kdiab.profiles.infrastructure.persistence.ExposedProfileRepository
import org.javafreedom.kdiab.common.plugins.DefaultHealthService
import org.javafreedom.kdiab.common.plugins.configureCommonPlugins
import org.javafreedom.kdiab.common.plugins.configureContentNegotiation
import org.javafreedom.kdiab.common.plugins.configureCors
import org.javafreedom.kdiab.common.plugins.configureHealth
import org.javafreedom.kdiab.common.plugins.configureSecurityHeaders
import org.javafreedom.kdiab.common.plugins.configureStatusPages

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val initDatabase = environment.config.propertyOrNull("app.initDatabase")?.getString()?.toBoolean() ?: true

    if (pluginOrNull(DI) == null) {
        install(DI) { }
        dependencies {
            provide<ProfileService> { ProfileService(ExposedProfileRepository()) }
            provide<InsulinService> { InsulinService(ExposedInsulinRepository()) }
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

    val profileService: ProfileService by dependencies
    val insulinService: InsulinService by dependencies
    val auditLogRepository: AuditLogRepository by dependencies

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

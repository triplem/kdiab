package org.javafreedom.kdiab.carbs

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.javafreedom.kdiab.carbs.adapters.inbound.web.foodEntryRoutes
import org.javafreedom.kdiab.carbs.application.service.FoodEntryService
import org.javafreedom.kdiab.carbs.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.carbs.infrastructure.persistence.ExposedFoodEntryRepository
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
    val createSchema = environment.config.propertyOrNull("app.createSchema")?.getString()?.toBoolean() ?: false

    if (pluginOrNull(DI) == null) {
        install(DI) { }
        dependencies {
            provide<FoodEntryService> { FoodEntryService(ExposedFoodEntryRepository()) }
        }
    }

    configureCommonPlugins()
    configureStatusPages()
    configureContentNegotiation()
    install(Resources)
    configureCors()
    configureSecurityHeaders()

    if (initDatabase) {
        DatabaseFactory.init(environment.config, createSchema = createSchema)
    }

    configureHealth(DefaultHealthService {
        withContext(Dispatchers.IO) {
            transaction { exec("SELECT 1") }
            true
        }
    })

    val swaggerEnabled = environment.config.propertyOrNull("swagger.enabled")?.getString()?.toBoolean() ?: false

    val foodEntryService: FoodEntryService by dependencies

    routing {
        get("/") { call.respondText("T1D Carbs Service is running!") }

        route("/api/v1") {
            foodEntryRoutes(foodEntryService)
        }

        if (swaggerEnabled) {
            swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
        }
    }
}

package org.javafreedom.kdiab.carbs

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
import org.javafreedom.kdiab.carbs.adapters.inbound.web.foodEntryRoutes
import org.javafreedom.kdiab.carbs.application.service.FoodEntryService
import org.javafreedom.kdiab.carbs.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.carbs.infrastructure.persistence.ExposedFoodEntryRepository
import org.javafreedom.kdiab.common.plugins.DefaultHealthService
import org.javafreedom.kdiab.common.plugins.configureCommonPlugins
import org.javafreedom.kdiab.common.plugins.configureContentNegotiation
import org.javafreedom.kdiab.common.plugins.configureHealth
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

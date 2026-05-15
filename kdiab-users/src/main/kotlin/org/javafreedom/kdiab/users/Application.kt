@file:Suppress("InjectDispatcher")
package org.javafreedom.kdiab.users

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.javafreedom.kdiab.common.plugins.configureLogging
import org.javafreedom.kdiab.common.plugins.configureSecurity
import org.javafreedom.kdiab.common.plugins.configureStatusPages
import org.javafreedom.kdiab.common.plugins.configureTracing
import org.javafreedom.kdiab.users.adapters.inbound.web.doctorPatientRoutes
import org.javafreedom.kdiab.users.adapters.inbound.web.registrationRoutes
import org.javafreedom.kdiab.users.adapters.inbound.web.userRoutes
import org.javafreedom.kdiab.users.application.service.DoctorPatientService
import org.javafreedom.kdiab.users.application.service.RegistrationService
import org.javafreedom.kdiab.users.application.service.UserService
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakAdminClient
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakCircuitBreakerOpenException
import org.javafreedom.kdiab.common.plugins.ErrorResponse
import org.javafreedom.kdiab.users.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.users.infrastructure.persistence.ExposedDoctorPatientRepository
import org.javafreedom.kdiab.users.infrastructure.persistence.ExposedUserSettingsRepository
import org.javafreedom.kdiab.users.plugins.configureMetrics

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("LongMethod")
fun Application.module(
    keycloakAdminClient: KeycloakAdminClient? = null,
    initDatabase: Boolean = true,
) {
    configureTracing()
    configureLogging()
    configureMetrics()
    configureSecurity()
    configureStatusPages {
        exception<KeycloakCircuitBreakerOpenException> { call, cause ->
            val status = HttpStatusCode.ServiceUnavailable
            val msg = "Keycloak Admin API temporarily unavailable: ${cause.service}"
            call.respond(status, ErrorResponse(status.value, msg))
        }
    }

    val prettyPrint = environment.config.propertyOrNull("json.prettyPrint")
        ?.getString()?.toBoolean() ?: false
    val json = Json { ignoreUnknownKeys = true; this.prettyPrint = prettyPrint }
    install(ContentNegotiation) { json(json) }

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
        allowMethod(HttpMethod.Patch)
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

    val kcAdminCfg = environment.config.config("keycloakAdmin")
    val keycloak = keycloakAdminClient ?: KeycloakAdminClient(
        baseUrl = kcAdminCfg.property("url").getString(),
        realm = kcAdminCfg.property("realm").getString(),
        clientId = kcAdminCfg.property("clientId").getString(),
        clientSecret = kcAdminCfg.property("clientSecret").getString(),
    )
    monitor.subscribe(ApplicationStopping) { keycloak.close() }

    val settingsRepo = ExposedUserSettingsRepository()
    val doctorPatientRepo = ExposedDoctorPatientRepository()

    val userService = UserService(keycloak, settingsRepo, doctorPatientRepo)
    val doctorPatientService = DoctorPatientService(doctorPatientRepo, keycloak)

    val registrationEnabled = environment.config
        .propertyOrNull("registration.enabled")?.getString()?.toBoolean() ?: false
    val requiresApproval = environment.config
        .propertyOrNull("registration.requiresApproval")?.getString()?.toBoolean() ?: false
    val registrationService = RegistrationService(keycloak, settingsRepo, requiresApproval)

    routing {
        get("/healthz") { call.respond(HttpStatusCode.OK) }
        get("/readyz") {
            val ready = withContext(Dispatchers.IO) {
                runCatching { transaction { exec("SELECT 1") } }.isSuccess
            }
            if (ready) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.ServiceUnavailable)
        }

        route("/api/v1") {
            userRoutes(userService)
            doctorPatientRoutes(doctorPatientService)
            if (registrationEnabled) {
                registrationRoutes(registrationService)
            }
        }
    }
}

@file:Suppress("InjectDispatcher")
package org.javafreedom.kdiab.users

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.javafreedom.kdiab.common.plugins.CircuitBreakerOpenException
import org.javafreedom.kdiab.common.plugins.ErrorResponse
import org.javafreedom.kdiab.common.plugins.configureLogging
import org.javafreedom.kdiab.common.plugins.configureMetrics
import org.javafreedom.kdiab.common.plugins.configureRateLimit
import org.javafreedom.kdiab.common.plugins.configureSecurity
import org.javafreedom.kdiab.common.plugins.configureSecurityHeaders
import org.javafreedom.kdiab.common.plugins.configureStatusPages
import org.javafreedom.kdiab.common.plugins.configureTracing
import org.javafreedom.kdiab.users.adapters.inbound.web.apiKeyRoutes
import org.javafreedom.kdiab.users.adapters.inbound.web.doctorPatientRoutes
import org.javafreedom.kdiab.users.adapters.inbound.web.invitationRoutes
import org.javafreedom.kdiab.users.adapters.inbound.web.userRoutes
import org.javafreedom.kdiab.users.application.service.ApiKeyService
import org.javafreedom.kdiab.users.application.service.DoctorPatientService
import org.javafreedom.kdiab.users.application.service.InvitationService
import org.javafreedom.kdiab.users.application.service.UserService
import org.javafreedom.kdiab.users.domain.repository.DoctorInvitationRepository
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakAdminClient
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakIdentityProviderAdapter
import org.javafreedom.kdiab.users.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.users.infrastructure.persistence.ExposedDoctorInvitationsRepository
import org.javafreedom.kdiab.users.infrastructure.persistence.ExposedDoctorPatientRepository
import org.javafreedom.kdiab.users.infrastructure.persistence.ExposedUserSettingsRepository

fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

// Keys that have no safe fallback default and must be provided at startup.
private val REQUIRED_CONFIG_KEYS = listOf(
    "keycloakAdmin.clientSecret",
    "storage.jdbcUrl",
    "storage.username",
    "storage.password",
)

internal fun validateConfig(config: io.ktor.server.config.ApplicationConfig) {
    val missing = REQUIRED_CONFIG_KEYS.filter {
        config.propertyOrNull(it)?.getString().isNullOrBlank()
    }
    check(missing.isEmpty()) {
        "Missing required configuration keys: ${missing.joinToString(", ")}. " +
            "Set these via environment variables or application.conf."
    }
}

@Suppress("LongMethod")
fun Application.module() {
    val initDatabase = environment.config.propertyOrNull("app.initDatabase")?.getString()?.toBoolean() ?: true
    validateConfig(environment.config)

    if (pluginOrNull(DI) == null) {
        val kcAdminCfg = environment.config.config("keycloakAdmin")
        val keycloak = KeycloakAdminClient(
            baseUrl = kcAdminCfg.property("url").getString(),
            realm = kcAdminCfg.property("realm").getString(),
            clientId = kcAdminCfg.property("clientId").getString(),
            clientSecret = kcAdminCfg.property("clientSecret").getString(),
        )
        val keycloakTokenEndpoint = kcAdminCfg.property("tokenEndpoint").getString()
        monitor.subscribe(ApplicationStopping) { keycloak.close() }

        val identityProvider: IdentityProviderPort = KeycloakIdentityProviderAdapter(keycloak)

        val settingsRepo = ExposedUserSettingsRepository()
        val doctorPatientRepo = ExposedDoctorPatientRepository()
        val invitationRepo = ExposedDoctorInvitationsRepository()

        install(DI) { }
        dependencies {
            provide<KeycloakAdminClient> { keycloak }
            provide<IdentityProviderPort> { identityProvider }
            provide<UserSettingsRepository> { settingsRepo }
            provide<DoctorPatientRepository> { doctorPatientRepo }
            provide<DoctorInvitationRepository> { invitationRepo }
            provide<UserService> {
                UserService(identityProvider, settingsRepo, doctorPatientRepo)
            }
            provide<DoctorPatientService> {
                DoctorPatientService(doctorPatientRepo, identityProvider)
            }
            provide<InvitationService> {
                InvitationService(invitationRepo, identityProvider)
            }
            provide<ApiKeyService> {
                ApiKeyService(keycloak, keycloakTokenEndpoint)
            }
        }
    }

    configureTracing()
    configureLogging()
    configureMetrics()
    configureSecurity()
    configureRateLimit()
    configureStatusPages {
        exception<CircuitBreakerOpenException> { call, cause ->
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
        allowOrigins { it in corsOrigins }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }
    configureSecurityHeaders()

    if (initDatabase) {
        DatabaseFactory.init(environment.config)
    }

    val userService: UserService by dependencies
    val doctorPatientService: DoctorPatientService by dependencies
    val invitationService: InvitationService by dependencies
    val apiKeyService: ApiKeyService by dependencies

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
            // invitationRoutes must be registered before other /{userId} routes to avoid
            // Ktor treating "admin" as a userId segment on /users/admin/invitations.
            invitationRoutes(invitationService)
            apiKeyRoutes(apiKeyService)
        }
    }
}

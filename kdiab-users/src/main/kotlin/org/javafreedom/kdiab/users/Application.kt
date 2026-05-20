@file:Suppress("InjectDispatcher")
package org.javafreedom.kdiab.users

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.di.*
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
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakAdminClient
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakIdentityProviderAdapter
import org.javafreedom.kdiab.common.plugins.CircuitBreakerOpenException
import org.javafreedom.kdiab.common.plugins.ErrorResponse
import org.javafreedom.kdiab.users.infrastructure.persistence.DatabaseFactory
import org.javafreedom.kdiab.users.infrastructure.persistence.ExposedDoctorPatientRepository
import org.javafreedom.kdiab.users.infrastructure.persistence.ExposedUserSettingsRepository
import org.javafreedom.kdiab.common.plugins.configureMetrics

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

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
fun Application.module(initDatabase: Boolean = true) {
    validateConfig(environment.config)

    // In Ktor 3.4.x, PluginModuleParametersInjector accesses Application.dependencies to
    // resolve module function parameters (e.g. initDatabase), which auto-installs an empty
    // DI container before module() runs. This makes pluginOrNull(DI) == null return false,
    // skipping production registrations entirely. Use jwt.test to distinguish test vs prod.
    // This also prevents KeycloakAdminClient construction in tests (Keycloak not running).
    val isTestMode = environment.config.propertyOrNull("jwt.test")?.getString()?.toBoolean() == true
    if (!isTestMode) {
        val kcAdminCfg = environment.config.config("keycloakAdmin")
        val keycloak = KeycloakAdminClient(
            baseUrl = kcAdminCfg.property("url").getString(),
            realm = kcAdminCfg.property("realm").getString(),
            clientId = kcAdminCfg.property("clientId").getString(),
            clientSecret = kcAdminCfg.property("clientSecret").getString(),
        )
        monitor.subscribe(ApplicationStopping) { keycloak.close() }

        val identityProvider: IdentityProviderPort = KeycloakIdentityProviderAdapter(keycloak)

        val requiresApproval = environment.config
            .propertyOrNull("registration.requiresApproval")?.getString()?.toBoolean() ?: false

        val settingsRepo = ExposedUserSettingsRepository()
        val doctorPatientRepo = ExposedDoctorPatientRepository()

        if (pluginOrNull(DI) == null) install(DI) { }
        dependencies {
            provide<KeycloakAdminClient> { keycloak }
            provide<IdentityProviderPort> { identityProvider }
            provide<UserSettingsRepository> { settingsRepo }
            provide<DoctorPatientRepository> { doctorPatientRepo }
            provide<UserService> {
                UserService(identityProvider, settingsRepo, doctorPatientRepo)
            }
            provide<DoctorPatientService> {
                DoctorPatientService(doctorPatientRepo, identityProvider)
            }
            provide<RegistrationService> {
                RegistrationService(identityProvider, settingsRepo, requiresApproval)
            }
        }
    }

    configureTracing()
    configureLogging()
    configureMetrics()
    configureSecurity()
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
    val httpsEnabled = environment.config.propertyOrNull("server.httpsEnabled")
        ?.getString()?.toBoolean() ?: false
    install(DefaultHeaders) {
        header("Content-Security-Policy", "default-src 'self'; script-src 'self'; object-src 'none'")
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        // HSTS is only meaningful over HTTPS; sending it on plain HTTP confuses intermediaries.
        if (httpsEnabled) {
            header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        }
    }

    if (initDatabase) {
        DatabaseFactory.init(environment.config)
    }

    val userService: UserService by dependencies
    val doctorPatientService: DoctorPatientService by dependencies
    val registrationService: RegistrationService by dependencies

    val registrationEnabled = environment.config
        .propertyOrNull("registration.enabled")?.getString()?.toBoolean() ?: false

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

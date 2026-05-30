@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.adapters.inbound.web.e2e

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import java.util.Date
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.users.application.service.ApiKeyService
import org.javafreedom.kdiab.users.application.service.DoctorPatientService
import org.javafreedom.kdiab.users.application.service.RegistrationService
import org.javafreedom.kdiab.users.application.service.UserService
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort
import org.javafreedom.kdiab.users.domain.repository.IdentityUserProfile
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository
import org.javafreedom.kdiab.users.module

// Top-level helper: installs mock DI bindings on the Application before module() runs.
// Extracted from the BehaviorSpec lambdas to avoid implicit-receiver ambiguity with Kotest DSL.
private fun Application.installMockDi(
    mockIdentityProvider: IdentityProviderPort,
    mockSettingsRepo: UserSettingsRepository,
    mockDoctorRepo: DoctorPatientRepository,
) {
    install(DI) { }
    dependencies {
        provide<IdentityProviderPort> { mockIdentityProvider }
        provide<UserSettingsRepository> { mockSettingsRepo }
        provide<DoctorPatientRepository> { mockDoctorRepo }
        provide<UserService> { UserService(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo) }
        provide<DoctorPatientService> { DoctorPatientService(mockDoctorRepo, mockIdentityProvider) }
        provide<RegistrationService> { RegistrationService(mockIdentityProvider, mockSettingsRepo, false) }
        provide<ApiKeyService> { mockk(relaxed = true) }
    }
}

class UserSettingsE2ETest :
    BehaviorSpec({
        val jwtDomain = "http://localhost:8081/realms/kdiab"
        val jwtAudience = "users"
        val jwtRealm = "kdiab"
        val jwtSecret = "secret"

        fun generateToken(
            userId: Uuid,
            roles: List<String> = listOf("PATIENT"),
        ): String = JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtDomain)
            .withSubject(userId.toString())
            .withClaim("roles", roles)
            .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
            .sign(Algorithm.HMAC256(jwtSecret))

        fun identityProfile(id: Uuid) = IdentityUserProfile(
            id = id.toString(),
            email = "test@example.com",
            firstName = "Test",
            lastName = "User",
            enabled = true,
        )

        fun buildConfig(): MapApplicationConfig = MapApplicationConfig(
            "jwt.domain" to jwtDomain,
            "jwt.audience" to jwtAudience,
            "jwt.realm" to jwtRealm,
            "jwt.secret" to jwtSecret,
            "jwt.test" to "true",
            "keycloakAdmin.clientSecret" to "test-secret",
            "keycloakAdmin.url" to "http://localhost:8081",
            "keycloakAdmin.realm" to jwtRealm,
            "keycloakAdmin.clientId" to "kdiab-users-service",
            // H2 in-memory DB so the service starts without a real Postgres
            "storage.jdbcUrl" to "jdbc:h2:mem:e2e_users;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "storage.username" to "root",
            "storage.password" to "password",
            "storage.driverClassName" to "org.h2.Driver",
            "storage.maximumPoolSize" to "3",
            "storage.isAutoCommit" to "false",
            "storage.transactionIsolation" to "TRANSACTION_REPEATABLE_READ",
            "app.initDatabase" to "false",
        )

        given("A running Users Service") {

            `when`("I request the health-check endpoint") {
                then("It should return HTTP 200 OK") {
                    val mockIdentityProvider = mockk<IdentityProviderPort>(relaxed = true)
                    val mockSettingsRepo = mockk<UserSettingsRepository>(relaxed = true)
                    val mockDoctorRepo = mockk<DoctorPatientRepository>(relaxed = true)

                    testApplication {
                        environment { config = buildConfig() }
                        application {
                            installMockDi(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo)
                            module()
                        }

                        val response = client.get("/healthz")
                        response.status shouldBe HttpStatusCode.OK
                    }
                }
            }

            `when`("I call GET /api/v1/users/me without a token") {
                then("It should return HTTP 401 Unauthorized") {
                    val mockIdentityProvider = mockk<IdentityProviderPort>(relaxed = true)
                    val mockSettingsRepo = mockk<UserSettingsRepository>(relaxed = true)
                    val mockDoctorRepo = mockk<DoctorPatientRepository>(relaxed = true)

                    testApplication {
                        environment { config = buildConfig() }
                        application {
                            installMockDi(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo)
                            module()
                        }

                        val response = client.get("/api/v1/users/me")
                        response.status shouldBe HttpStatusCode.Unauthorized
                    }
                }
            }

            `when`("I call GET /api/v1/users/me with a valid PATIENT token") {
                then("It should return HTTP 200 and the user's profile") {
                    val userId = Uuid.random()
                    val token = generateToken(userId)

                    val mockIdentityProvider = mockk<IdentityProviderPort>(relaxed = true)
                    val mockSettingsRepo = mockk<UserSettingsRepository>(relaxed = true)
                    val mockDoctorRepo = mockk<DoctorPatientRepository>(relaxed = true)

                    coEvery { mockIdentityProvider.getUserProfile(userId) } returns identityProfile(userId)
                    coEvery { mockIdentityProvider.getUserRoles(userId) } returns emptySet()
                    coEvery { mockSettingsRepo.findByUserId(userId) } returns null
                    coEvery { mockSettingsRepo.save(any()) } answers { firstArg() }

                    testApplication {
                        environment { config = buildConfig() }
                        application {
                            installMockDi(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo)
                            module()
                        }
                        val client = createClient { install(ContentNegotiation) { json() } }

                        val response = client.get("/api/v1/users/me") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                        }
                        response.status shouldBe HttpStatusCode.OK
                        val body = response.bodyAsText()
                        body shouldContain "userId"
                        body shouldContain userId.toString()
                    }
                }
            }

            `when`("I call PATCH /api/v1/users/me/settings without a token") {
                then("It should return HTTP 401 Unauthorized") {
                    val mockIdentityProvider = mockk<IdentityProviderPort>(relaxed = true)
                    val mockSettingsRepo = mockk<UserSettingsRepository>(relaxed = true)
                    val mockDoctorRepo = mockk<DoctorPatientRepository>(relaxed = true)

                    testApplication {
                        environment { config = buildConfig() }
                        application {
                            installMockDi(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo)
                            module()
                        }

                        val response = client.patch("/api/v1/users/me/settings") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"timezone":"Europe/Berlin"}""")
                        }
                        response.status shouldBe HttpStatusCode.Unauthorized
                    }
                }
            }

            `when`("I call PATCH /api/v1/users/me/settings with a valid PATIENT token") {
                then("It should return HTTP 200 and the updated settings") {
                    val userId = Uuid.random()
                    val token = generateToken(userId)

                    val mockIdentityProvider = mockk<IdentityProviderPort>(relaxed = true)
                    val mockSettingsRepo = mockk<UserSettingsRepository>(relaxed = true)
                    val mockDoctorRepo = mockk<DoctorPatientRepository>(relaxed = true)

                    coEvery { mockSettingsRepo.findByUserId(userId) } returns null
                    coEvery { mockSettingsRepo.save(any()) } answers { firstArg() }

                    testApplication {
                        environment { config = buildConfig() }
                        application {
                            installMockDi(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo)
                            module()
                        }
                        val client = createClient { install(ContentNegotiation) { json() } }

                        val response = client.patch("/api/v1/users/me/settings") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                            contentType(ContentType.Application.Json)
                            setBody("""{"locale":{"timezone":"Europe/Berlin"}}""")
                        }
                        response.status shouldBe HttpStatusCode.OK
                        val body = response.bodyAsText()
                        body shouldContain "timezone"
                        body shouldContain "Europe/Berlin"
                    }
                }
            }

            `when`("I call PATCH /api/v1/users/me/settings with an invalid glucoseUnit") {
                then("It should return HTTP 400 Bad Request") {
                    val userId = Uuid.random()
                    val token = generateToken(userId)

                    val mockIdentityProvider = mockk<IdentityProviderPort>(relaxed = true)
                    val mockSettingsRepo = mockk<UserSettingsRepository>(relaxed = true)
                    val mockDoctorRepo = mockk<DoctorPatientRepository>(relaxed = true)

                    coEvery { mockSettingsRepo.findByUserId(userId) } returns null
                    coEvery { mockSettingsRepo.save(any()) } answers { firstArg() }

                    testApplication {
                        environment { config = buildConfig() }
                        application {
                            installMockDi(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo)
                            module()
                        }
                        val client = createClient { install(ContentNegotiation) { json() } }

                        val response = client.patch("/api/v1/users/me/settings") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                            contentType(ContentType.Application.Json)
                            setBody("""{"units":{"glucoseUnit":"invalid_unit"}}""")
                        }
                        response.status shouldBe HttpStatusCode.BadRequest
                    }
                }
            }

            `when`("A PATIENT calls GET /api/v1/users/{otherUserId}") {
                then("It should return HTTP 403 Forbidden") {
                    val sarahId = Uuid.random()
                    val mikeId = Uuid.random()
                    val sarahToken = generateToken(sarahId)

                    val mockIdentityProvider = mockk<IdentityProviderPort>(relaxed = true)
                    val mockSettingsRepo = mockk<UserSettingsRepository>(relaxed = true)
                    val mockDoctorRepo = mockk<DoctorPatientRepository>(relaxed = true)

                    testApplication {
                        environment { config = buildConfig() }
                        application {
                            installMockDi(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo)
                            module()
                        }

                        val response = client.get("/api/v1/users/$mikeId") {
                            header(HttpHeaders.Authorization, "Bearer $sarahToken")
                        }
                        response.status shouldBe HttpStatusCode.Forbidden
                    }
                }
            }

            `when`("An ADMIN calls GET /api/v1/users/{userId}") {
                then("It should return HTTP 200 with the target user's profile") {
                    val adminId = Uuid.random()
                    val patientId = Uuid.random()
                    val adminToken = generateToken(adminId, roles = listOf("ADMIN"))

                    val mockIdentityProvider = mockk<IdentityProviderPort>(relaxed = true)
                    val mockSettingsRepo = mockk<UserSettingsRepository>(relaxed = true)
                    val mockDoctorRepo = mockk<DoctorPatientRepository>(relaxed = true)

                    coEvery { mockIdentityProvider.getUserProfile(patientId) } returns identityProfile(patientId)
                    coEvery { mockIdentityProvider.getUserRoles(patientId) } returns emptySet()
                    coEvery { mockSettingsRepo.findByUserId(patientId) } returns null

                    testApplication {
                        environment { config = buildConfig() }
                        application {
                            installMockDi(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo)
                            module()
                        }
                        val client = createClient { install(ContentNegotiation) { json() } }

                        val response = client.get("/api/v1/users/$patientId") {
                            header(HttpHeaders.Authorization, "Bearer $adminToken")
                        }
                        response.status shouldBe HttpStatusCode.OK
                        val body = response.bodyAsText()
                        body shouldContain patientId.toString()
                    }
                }
            }
        }
    })

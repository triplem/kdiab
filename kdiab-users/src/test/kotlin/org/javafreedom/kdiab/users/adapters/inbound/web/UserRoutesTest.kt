@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.adapters.inbound.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.users.application.service.ApiKeyService
import org.javafreedom.kdiab.users.application.service.DoctorPatientService
import org.javafreedom.kdiab.users.application.service.UserService
import org.javafreedom.kdiab.users.domain.model.ApiKey
import org.javafreedom.kdiab.users.domain.model.ApiKeyCreated
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort
import org.javafreedom.kdiab.users.domain.repository.IdentityUserProfile
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository
import org.javafreedom.kdiab.users.module

// Top-level helper: installs mock DI bindings on the Application before module() runs.
// Extracted to avoid implicit-receiver ambiguity when called inside testApplication lambdas.
private fun Application.installMockDi(
    mockIdentityProvider: IdentityProviderPort,
    mockSettingsRepo: UserSettingsRepository,
    mockDoctorRepo: DoctorPatientRepository,
    mockApiKeyService: ApiKeyService,
) {
    install(DI) { }
    dependencies {
        provide<IdentityProviderPort> { mockIdentityProvider }
        provide<UserSettingsRepository> { mockSettingsRepo }
        provide<DoctorPatientRepository> { mockDoctorRepo }
        provide<UserService> { UserService(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo) }
        provide<DoctorPatientService> { DoctorPatientService(mockDoctorRepo, mockIdentityProvider) }
        provide<ApiKeyService> { mockApiKeyService }
    }
}

class UserRoutesTest {

    // ── Test JWT helpers ──────────────────────────────────────────────────────

    private companion object {
        const val JWT_SECRET = "test-secret-for-unit-tests-only"
        const val AUDIENCE   = "users"
        const val ISSUER     = "http://localhost:8081/realms/kdiab"

        const val SARAH_ID  = "11111111-1111-1111-1111-111111111111"
        const val MIKE_ID   = "22222222-2222-2222-2222-222222222222"
        const val ADMIN_ID  = "55555555-5555-5555-5555-555555555555"

        fun token(
            userId: String,
            roles: List<String>,
        ): String = JWT.create()
            .withSubject(userId)
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("roles", roles)
            .sign(Algorithm.HMAC256(JWT_SECRET))

        val sarahToken get() = token(SARAH_ID, listOf("PATIENT"))
        val mikeToken  get() = token(MIKE_ID,  listOf("PATIENT"))
        val adminToken get() = token(ADMIN_ID, listOf("ADMIN"))

        // Identity provider test doubles
        fun identityProfile(id: String) = IdentityUserProfile(
            id = id, email = "test@example.com",
            firstName = "Test", lastName = "User", enabled = true,
        )
    }

    // ── Test application setup ────────────────────────────────────────────────

    /**
     * Starts a full Ktor test application with mocked dependencies.
     * The [block] receives pre-configured mocks so individual tests can override call expectations.
     */
    private fun routeTest(
        block: suspend ApplicationTestBuilder.(
            identityProvider: IdentityProviderPort,
            settingsRepo: UserSettingsRepository,
            doctorPatientRepo: DoctorPatientRepository,
            apiKeyService: ApiKeyService,
        ) -> Unit
    ) {
        val mockIdentityProvider = mockk<IdentityProviderPort>(relaxed = true)
        val mockSettingsRepo     = mockk<UserSettingsRepository>(relaxed = true)
        val mockDoctorRepo       = mockk<DoctorPatientRepository>(relaxed = true)
        val mockApiKeyService    = mockk<ApiKeyService>(relaxed = true)

        coEvery { mockIdentityProvider.getUserProfile(any()) } answers {
            identityProfile(firstArg<Uuid>().toString())
        }
        coEvery { mockIdentityProvider.getUserRoles(any()) } returns emptySet()
        coEvery { mockIdentityProvider.listUserProfiles(any(), any(), any()) } returns emptyList()
        coEvery { mockIdentityProvider.createUser(any()) } returns Uuid.parse("99999999-9999-9999-9999-999999999999")
        coEvery { mockIdentityProvider.assignRoles(any(), any()) } returns Unit
        coEvery { mockSettingsRepo.findByUserId(any()) } returns null
        coEvery { mockSettingsRepo.save(any()) } answers { firstArg() }
        coEvery { mockApiKeyService.listApiKeys(any()) } returns emptyList()

        testApplication {
            environment {
                config = MapApplicationConfig(
                    "jwt.domain"                 to ISSUER,
                    "jwt.audience"               to AUDIENCE,
                    "jwt.realm"                  to "kdiab",
                    "jwt.test"                   to "true",
                    "jwt.secret"                 to JWT_SECRET,
                    "keycloakAdmin.clientSecret" to "test-secret",
                    "keycloakAdmin.url"          to "http://localhost:8081",
                    "keycloakAdmin.realm"        to "kdiab",
                    "keycloakAdmin.clientId"     to "kdiab-users-service",
                    "storage.jdbcUrl"            to "jdbc:h2:mem:test",
                    "storage.username"           to "root",
                    "storage.password"           to "test",
                    "app.initDatabase"           to "false",
                )
            }
            application {
                installMockDi(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo, mockApiKeyService)
                module()
            }
            block(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo, mockApiKeyService)
        }
    }

    // ── GET /api/v1/users/me ──────────────────────────────────────────────────

    @Test
    fun `GET users-me - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users/me")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET users-me - 200 authenticated patient returns self`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users/me") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `GET users-me - 200 authenticated admin returns self`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users/me") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── PATCH /api/v1/users/me/settings ──────────────────────────────────────

    @Test
    fun `PATCH users-me-settings - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"timezone":"Europe/Berlin"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `PATCH users-me-settings - 200 patient updates own settings`() = routeTest { _, _, _, _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"timezone":"Europe/Berlin"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── GET /api/v1/users ─────────────────────────────────────────────────────

    @Test
    fun `GET users - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET users - 403 patient cannot list users`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `GET users - 200 admin lists users`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── POST /api/v1/users ────────────────────────────────────────────────────

    @Test
    fun `POST users - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.post("/api/v1/users") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"new@example.com","displayName":"New User","password":"pass123","role":"PATIENT"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST users - 403 patient cannot create users`() = routeTest { _, _, _, _ ->
        val resp = client.post("/api/v1/users") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"new@example.com","displayName":"New User","password":"pass123","role":"PATIENT"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `POST users - 201 admin creates user`() = routeTest { _, _, _, _ ->
        val resp = client.post("/api/v1/users") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"new@example.com","displayName":"New User","password":"pass123","role":"PATIENT"}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun `POST users - 400 when role is invalid`() = routeTest { _, _, _, _ ->
        val resp = client.post("/api/v1/users") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"e@e.com","displayName":"Test","password":"pass","role":"INVALID_ROLE"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── GET /api/v1/users/{userId} ────────────────────────────────────────────

    @Test
    fun `GET users-userId - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET users-userId - 200 patient reads own profile`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `GET users-userId - 403 patient reads another user profile`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users/$MIKE_ID") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `GET users-userId - 200 admin reads any user profile`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `GET users-userId - 400 when userId is not a valid UUID`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users/not-a-uuid") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── PATCH /api/v1/users/{userId} ──────────────────────────────────────────

    @Test
    fun `PATCH users-userId - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.patch("/api/v1/users/$SARAH_ID") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"displayName":"Updated Name"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `PATCH users-userId - 403 patient cannot update users`() = routeTest { _, _, _, _ ->
        val resp = client.patch("/api/v1/users/$SARAH_ID") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"displayName":"Updated Name"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `PATCH users-userId - 200 admin updates user`() = routeTest { _, _, _, _ ->
        val resp = client.patch("/api/v1/users/$SARAH_ID") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"displayName":"Updated Name"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `PATCH users-userId - 400 when role is invalid`() = routeTest { _, _, _, _ ->
        val resp = client.patch("/api/v1/users/$SARAH_ID") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"role":"INVALID_ROLE"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── DELETE /api/v1/users/{userId} ─────────────────────────────────────────

    @Test
    fun `DELETE users-userId - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.delete("/api/v1/users/$SARAH_ID")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `DELETE users-userId - 403 patient cannot delete users`() = routeTest { _, _, _, _ ->
        val resp = client.delete("/api/v1/users/$SARAH_ID") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `DELETE users-userId - 204 admin deletes user`() = routeTest { _, _, _, _ ->
        val resp = client.delete("/api/v1/users/$SARAH_ID") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)
    }

    // ── POST /api/v1/users/me/api-keys ────────────────────────────────────────

    @Test
    fun `POST api-keys - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.post("/api/v1/users/me/api-keys") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"My Device"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST api-keys - 201 patient creates key`() = routeTest { _, _, _, apiKeyService ->
        val stubClientCredential = "stub-oauth2-client-credential"
        val createdKey = ApiKeyCreated(
            apiKey = ApiKey(
                id = "client-uuid-1",
                clientId = "device-$SARAH_ID-abcdefgh",
                name = "My Device",
                expiresAt = null,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            ),
            secret = stubClientCredential,
            tokenEndpoint = "http://localhost:8081/realms/kdiab/protocol/openid-connect/token",
        )
        coEvery { apiKeyService.createApiKey(any(), "My Device", any()) } returns createdKey

        val resp = client.post("/api/v1/users/me/api-keys") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"My Device"}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun `POST api-keys - 400 when name is blank`() = routeTest { _, _, _, _ ->
        val resp = client.post("/api/v1/users/me/api-keys") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `POST api-keys - 400 when expiry is invalid`() = routeTest { _, _, _, _ ->
        val resp = client.post("/api/v1/users/me/api-keys") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"My Device","expiry":"FOREVER"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── GET /api/v1/users/me/api-keys ─────────────────────────────────────────

    @Test
    fun `GET api-keys - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users/me/api-keys")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET api-keys - 200 returns key list`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users/me/api-keys") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── DELETE /api/v1/users/me/api-keys/{keyId} ──────────────────────────────

    @Test
    fun `DELETE api-keys-keyId - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.delete("/api/v1/users/me/api-keys/some-key-id")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `DELETE api-keys-keyId - 204 revokes key`() = routeTest { _, _, _, _ ->
        val resp = client.delete("/api/v1/users/me/api-keys/some-key-id") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)
    }

    @Test
    fun `DELETE api-keys-keyId - 404 when key not found`() = routeTest { _, _, _, apiKeyService ->
        coEvery { apiKeyService.revokeApiKey(any(), "missing-key-id") } throws
            ResourceNotFoundException("API key missing-key-id not found")

        val resp = client.delete("/api/v1/users/me/api-keys/missing-key-id") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }
}

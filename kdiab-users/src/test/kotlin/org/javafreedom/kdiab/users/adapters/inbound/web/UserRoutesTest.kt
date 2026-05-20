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
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.users.application.service.DoctorPatientService
import org.javafreedom.kdiab.users.application.service.RegistrationService
import org.javafreedom.kdiab.users.application.service.UserService
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
) {
    install(DI) { }
    dependencies {
        provide<IdentityProviderPort> { mockIdentityProvider }
        provide<UserSettingsRepository> { mockSettingsRepo }
        provide<DoctorPatientRepository> { mockDoctorRepo }
        provide<UserService> { UserService(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo) }
        provide<DoctorPatientService> { DoctorPatientService(mockDoctorRepo, mockIdentityProvider) }
        provide<RegistrationService> { RegistrationService(mockIdentityProvider, mockSettingsRepo, false) }
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
     * Starts a full Ktor test application with mocked identity provider and repository dependencies.
     * The [block] receives a pre-configured [IdentityProviderPort] mock so individual tests
     * can override specific call expectations.
     */
    private fun routeTest(
        block: suspend ApplicationTestBuilder.(
            identityProvider: IdentityProviderPort,
            settingsRepo: UserSettingsRepository,
            doctorPatientRepo: DoctorPatientRepository,
        ) -> Unit
    ) {
        val mockIdentityProvider = mockk<IdentityProviderPort>(relaxed = true)
        val mockSettingsRepo     = mockk<UserSettingsRepository>(relaxed = true)
        val mockDoctorRepo       = mockk<DoctorPatientRepository>(relaxed = true)

        // Default stub: getUserProfile returns a valid profile for any UUID
        coEvery { mockIdentityProvider.getUserProfile(any()) } answers {
            identityProfile(firstArg<Uuid>().toString())
        }
        // Default stub: getUserRoles returns empty set (PATIENT role comes from JWT claim)
        coEvery { mockIdentityProvider.getUserRoles(any()) } returns emptySet()
        // Default stub: listUserProfiles returns empty list
        coEvery { mockIdentityProvider.listUserProfiles(any(), any(), any()) } returns emptyList()
        // Default stub: createUser returns a new UUID
        coEvery { mockIdentityProvider.createUser(any()) } returns Uuid.parse("99999999-9999-9999-9999-999999999999")
        // Default stub: assignRoles is a no-op
        coEvery { mockIdentityProvider.assignRoles(any(), any()) } returns Unit
        // Default stub: settingsRepo.findByUserId returns null (service will create default)
        coEvery { mockSettingsRepo.findByUserId(any()) } returns null
        coEvery { mockSettingsRepo.save(any()) } answers { firstArg() }

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
                )
            }
            application {
                installMockDi(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo)
                module(initDatabase = false)
            }
            block(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo)
        }
    }

    // ── GET /api/v1/users/me ──────────────────────────────────────────────────

    @Test
    fun `GET users-me - 401 without auth token`() = routeTest { _, _, _ ->
        val resp = client.get("/api/v1/users/me")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET users-me - 200 authenticated patient returns self`() = routeTest { _, _, _ ->
        val resp = client.get("/api/v1/users/me") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `GET users-me - 200 authenticated admin returns self`() = routeTest { _, _, _ ->
        val resp = client.get("/api/v1/users/me") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── PATCH /api/v1/users/me/settings ──────────────────────────────────────

    @Test
    fun `PATCH users-me-settings - 401 without auth token`() = routeTest { _, _, _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"timezone":"Europe/Berlin"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `PATCH users-me-settings - 200 patient updates own settings`() = routeTest { _, _, _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"timezone":"Europe/Berlin"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── GET /api/v1/users ─────────────────────────────────────────────────────

    @Test
    fun `GET users - 401 without auth token`() = routeTest { _, _, _ ->
        val resp = client.get("/api/v1/users")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET users - 403 patient cannot list users`() = routeTest { _, _, _ ->
        val resp = client.get("/api/v1/users") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `GET users - 200 admin lists users`() = routeTest { _, _, _ ->
        val resp = client.get("/api/v1/users") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── POST /api/v1/users ────────────────────────────────────────────────────

    @Test
    fun `POST users - 401 without auth token`() = routeTest { _, _, _ ->
        val resp = client.post("/api/v1/users") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"new@example.com","displayName":"New User","password":"pass123","role":"PATIENT"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST users - 403 patient cannot create users`() = routeTest { _, _, _ ->
        val resp = client.post("/api/v1/users") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"new@example.com","displayName":"New User","password":"pass123","role":"PATIENT"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `POST users - 201 admin creates user`() = routeTest { _, _, _ ->
        val resp = client.post("/api/v1/users") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"new@example.com","displayName":"New User","password":"pass123","role":"PATIENT"}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun `POST users - 400 when role is invalid`() = routeTest { _, _, _ ->
        val resp = client.post("/api/v1/users") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"email":"e@e.com","displayName":"Test","password":"pass","role":"INVALID_ROLE"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── GET /api/v1/users/{userId} ────────────────────────────────────────────

    @Test
    fun `GET users-userId - 401 without auth token`() = routeTest { _, _, _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET users-userId - 200 patient reads own profile`() = routeTest { _, _, _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `GET users-userId - 403 patient reads another user profile`() = routeTest { _, _, _ ->
        val resp = client.get("/api/v1/users/$MIKE_ID") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `GET users-userId - 200 admin reads any user profile`() = routeTest { _, _, _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `GET users-userId - 400 when userId is not a valid UUID`() = routeTest { _, _, _ ->
        val resp = client.get("/api/v1/users/not-a-uuid") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── PATCH /api/v1/users/{userId} ──────────────────────────────────────────

    @Test
    fun `PATCH users-userId - 401 without auth token`() = routeTest { _, _, _ ->
        val resp = client.patch("/api/v1/users/$SARAH_ID") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"displayName":"Updated Name"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `PATCH users-userId - 403 patient cannot update users`() = routeTest { _, _, _ ->
        val resp = client.patch("/api/v1/users/$SARAH_ID") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"displayName":"Updated Name"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `PATCH users-userId - 200 admin updates user`() = routeTest { _, _, _ ->
        val resp = client.patch("/api/v1/users/$SARAH_ID") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"displayName":"Updated Name"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `PATCH users-userId - 400 when role is invalid`() = routeTest { _, _, _ ->
        val resp = client.patch("/api/v1/users/$SARAH_ID") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"role":"INVALID_ROLE"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── DELETE /api/v1/users/{userId} ─────────────────────────────────────────

    @Test
    fun `DELETE users-userId - 401 without auth token`() = routeTest { _, _, _ ->
        val resp = client.delete("/api/v1/users/$SARAH_ID")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `DELETE users-userId - 403 patient cannot delete users`() = routeTest { _, _, _ ->
        val resp = client.delete("/api/v1/users/$SARAH_ID") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `DELETE users-userId - 204 admin deletes user`() = routeTest { _, _, _ ->
        val resp = client.delete("/api/v1/users/$SARAH_ID") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)
    }
}

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
import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.users.application.service.ApiKeyService
import org.javafreedom.kdiab.users.application.service.DoctorPatientService
import org.javafreedom.kdiab.users.application.service.InvitationService
import org.javafreedom.kdiab.users.application.service.UserService
import org.javafreedom.kdiab.users.domain.model.AlarmThresholds
import org.javafreedom.kdiab.users.domain.model.LocalePreferences
import org.javafreedom.kdiab.users.domain.model.UnitPreferences
import org.javafreedom.kdiab.users.domain.model.UserSettings
import org.javafreedom.kdiab.users.domain.repository.DoctorInvitationRepository
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort
import org.javafreedom.kdiab.users.domain.repository.IdentityUserProfile
import org.javafreedom.kdiab.users.domain.repository.UserProfileRepository
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository
import org.javafreedom.kdiab.users.module

// Top-level helper: installs mock DI bindings on the Application before module() runs.
private fun Application.installMockDi(
    mockIdentityProvider: IdentityProviderPort,
    mockSettingsRepo: UserSettingsRepository,
    mockDoctorRepo: DoctorPatientRepository,
    mockApiKeyService: ApiKeyService,
    mockInvitationRepo: DoctorInvitationRepository,
    mockUserProfileRepo: UserProfileRepository = mockk(relaxed = true),
) {
    install(DI) { }
    dependencies {
        provide<IdentityProviderPort> { mockIdentityProvider }
        provide<UserSettingsRepository> { mockSettingsRepo }
        provide<UserProfileRepository> { mockUserProfileRepo }
        provide<DoctorPatientRepository> { mockDoctorRepo }
        provide<DoctorInvitationRepository> { mockInvitationRepo }
        provide<UserService> {
            UserService(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo, mockUserProfileRepo)
        }
        provide<DoctorPatientService> { DoctorPatientService(mockDoctorRepo, mockIdentityProvider) }
        provide<InvitationService> { InvitationService(mockInvitationRepo, mockIdentityProvider, mockDoctorRepo) }
        provide<ApiKeyService> { mockApiKeyService }
    }
}

/**
 * Integration tests for PATCH /api/v1/users/me/settings.
 *
 * These tests wire the real UserService (not a mock), so alarm ordering validation is exercised
 * end-to-end through the HTTP stack: route handler → UserService.updateMySettings →
 * validateAlarmThresholds → StatusPages exception mapping.
 *
 * The IdentityProviderPort and repositories are mocked because they represent external boundaries
 * (Keycloak Admin API, PostgreSQL) that are out of scope for a settings-validation integration test.
 */
class UserSettingsApiTest {

    private companion object {
        // Test-only HMAC256 signing value — matches jwt.test=true mode in application.conf.
        const val JWT_HMAC_SEED = "unit-test-jwt-hmac-seed"
        const val AUDIENCE   = "users"
        const val ISSUER     = "http://localhost:8081/realms/kdiab"

        const val SARAH_ID = "11111111-1111-1111-1111-111111111111"

        fun token(userId: String, roles: List<String>): String = JWT.create()
            .withSubject(userId)
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("roles", roles)
            .sign(Algorithm.HMAC256(JWT_HMAC_SEED))

        val sarahToken get() = token(SARAH_ID, listOf("PATIENT"))

        fun identityProfile(id: String) = IdentityUserProfile(
            id = id, email = "test@example.com",
            firstName = "Test", lastName = "User", enabled = true,
        )

        // Well-ordered alarm settings: urgentHigh(260) > high(200) > low(75) > urgentLow(55)
        fun defaultSettings(userId: Uuid = Uuid.parse(SARAH_ID)): UserSettings {
            val now = Clock.System.now()
            return UserSettings(
                userId = userId,
                locale = LocalePreferences(timezone = "UTC", language = "en", timeFormat = 24),
                units = UnitPreferences(glucoseUnit = "mg/dL", weightUnit = "kg"),
                alarms = AlarmThresholds(urgentHigh = 260, high = 200, low = 75, urgentLow = 55),
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    /**
     * Starts a Ktor test application with:
     * - real UserService wired to mocked repositories
     * - all config values required by validateConfig() stubbed
     * - initDatabase disabled (no Postgres required)
     */
    private fun settingsApiTest(
        settingsInRepo: UserSettings? = defaultSettings(),
        block: suspend ApplicationTestBuilder.(
            settingsRepo: UserSettingsRepository,
        ) -> Unit,
    ) {
        val mockIdentityProvider = mockk<IdentityProviderPort>(relaxed = true)
        val mockSettingsRepo     = mockk<UserSettingsRepository>(relaxed = true)
        val mockDoctorRepo       = mockk<DoctorPatientRepository>(relaxed = true)
        val mockApiKeyService    = mockk<ApiKeyService>(relaxed = true)
        val mockInvitationRepo   = mockk<DoctorInvitationRepository>(relaxed = true)
        val mockUserProfileRepo  = mockk<UserProfileRepository>(relaxed = true)

        coEvery { mockIdentityProvider.getUserProfile(any()) } answers {
            identityProfile(firstArg<Uuid>().toString())
        }
        coEvery { mockIdentityProvider.getUserRoles(any()) } returns emptySet()
        coEvery { mockSettingsRepo.findByUserId(any()) } returns settingsInRepo
        coEvery { mockSettingsRepo.save(any()) } answers { firstArg() }
        coEvery { mockUserProfileRepo.findBirthdayByUserId(any()) } returns null

        testApplication {
            environment {
                config = MapApplicationConfig(
                    "jwt.domain"                 to ISSUER,
                    "jwt.audience"               to AUDIENCE,
                    "jwt.realm"                  to "kdiab",
                    "jwt.test"                   to "true",
                    "jwt.secret"                 to JWT_HMAC_SEED,
                    "keycloakAdmin.clientSecret" to "test-client-credential",
                    "keycloakAdmin.url"          to "http://localhost:8081",
                    "keycloakAdmin.realm"        to "kdiab",
                    "keycloakAdmin.clientId"     to "kdiab-users-service",
                    "keycloakAdmin.tokenEndpoint" to "http://localhost:8081/realms/kdiab/protocol/openid-connect/token",
                    "storage.jdbcUrl"            to "jdbc:h2:mem:settings_api_test",
                    "storage.username"           to "root",
                    "storage.password"           to "h2mem",
                    "app.initDatabase"           to "false",
                )
            }
            application {
                installMockDi(
                    mockIdentityProvider,
                    mockSettingsRepo,
                    mockDoctorRepo,
                    mockApiKeyService,
                    mockInvitationRepo,
                    mockUserProfileRepo,
                )
                module()
            }
            block(mockSettingsRepo)
        }
    }

    // ── Auth checks ───────────────────────────────────────────────────────────

    @Test
    fun `PATCH settings - 401 without auth token`() = settingsApiTest { _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"locale":{"timezone":"Europe/Berlin"}}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    // ── Valid updates ─────────────────────────────────────────────────────────

    @Test
    fun `PATCH settings - 200 valid timezone update succeeds`() = settingsApiTest { _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"locale":{"timezone":"America/New_York"}}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `PATCH settings - 200 valid alarm thresholds accepted (urgentHigh gt high gt low gt urgentLow)`() =
        settingsApiTest { _ ->
            // Provide a base with no existing alarms so the full set comes from the request
            val resp = client.patch("/api/v1/users/me/settings") {
                bearerAuth(sarahToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    """{"alarms":{"urgentHigh":280,"high":220,"low":80,"urgentLow":60}}"""
                )
            }
            assertEquals(HttpStatusCode.OK, resp.status)
        }

    @Test
    fun `PATCH settings - 200 patient updates own glucose unit to mmol-L`() = settingsApiTest { _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"units":{"glucoseUnit":"mmol/L"}}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── Alarm ordering violations ─────────────────────────────────────────────

    @Test
    fun `PATCH settings - 400 when urgentHigh equals high`() = settingsApiTest { _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"alarms":{"urgentHigh":200,"high":200,"low":80,"urgentLow":60}}"""
            )
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `PATCH settings - 400 when urgentHigh is less than high`() = settingsApiTest { _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"alarms":{"urgentHigh":180,"high":200,"low":80,"urgentLow":60}}"""
            )
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `PATCH settings - 400 when high equals low`() = settingsApiTest { _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"alarms":{"urgentHigh":260,"high":80,"low":80,"urgentLow":60}}"""
            )
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `PATCH settings - 400 when high is less than low`() = settingsApiTest { _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"alarms":{"urgentHigh":260,"high":70,"low":80,"urgentLow":60}}"""
            )
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `PATCH settings - 400 when low equals urgentLow`() = settingsApiTest { _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"alarms":{"urgentHigh":260,"high":200,"low":60,"urgentLow":60}}"""
            )
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `PATCH settings - 400 when low is less than urgentLow`() = settingsApiTest { _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"alarms":{"urgentHigh":260,"high":200,"low":55,"urgentLow":60}}"""
            )
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── Alarm range violations ────────────────────────────────────────────────

    @Test
    fun `PATCH settings - 400 when urgentLow is below minimum (40)`() = settingsApiTest { _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"alarms":{"urgentHigh":260,"high":200,"low":80,"urgentLow":35}}"""
            )
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `PATCH settings - 400 when urgentHigh exceeds maximum (400)`() = settingsApiTest { _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"alarms":{"urgentHigh":401,"high":200,"low":80,"urgentLow":60}}"""
            )
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── Invalid enum values ───────────────────────────────────────────────────

    @Test
    fun `PATCH settings - 400 when glucoseUnit is invalid`() = settingsApiTest { _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"units":{"glucoseUnit":"dL/mg"}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `PATCH settings - 400 when weightUnit is invalid`() = settingsApiTest { _ ->
        val resp = client.patch("/api/v1/users/me/settings") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"units":{"weightUnit":"stones"}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── Partial alarm update uses existing thresholds from repo ───────────────

    @Test
    fun `PATCH settings - partial alarm update uses existing thresholds and rejects violation`() =
        settingsApiTest(
            // Existing: urgentHigh=260, high=200, low=75, urgentLow=55
            settingsInRepo = defaultSettings()
        ) { _ ->
            // Patch only urgentHigh to a value that violates urgentHigh > high (200)
            val resp = client.patch("/api/v1/users/me/settings") {
                bearerAuth(sarahToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"alarms":{"urgentHigh":190}}""")
            }
            // 190 < existing high (200) → violation
            assertEquals(HttpStatusCode.BadRequest, resp.status)
        }

    @Test
    fun `PATCH settings - partial alarm update uses existing thresholds and accepts valid change`() =
        settingsApiTest(
            // Existing: urgentHigh=260, high=200, low=75, urgentLow=55
            settingsInRepo = defaultSettings()
        ) { _ ->
            // Patch only urgentHigh to 300 - still satisfies 300 > 200 > 75 > 55
            val resp = client.patch("/api/v1/users/me/settings") {
                bearerAuth(sarahToken)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"alarms":{"urgentHigh":300}}""")
            }
            assertEquals(HttpStatusCode.OK, resp.status)
        }
}

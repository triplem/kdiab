package org.javafreedom.kdiab.users.adapters.inbound.web

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.javafreedom.kdiab.users.application.service.ApiKeyService
import org.javafreedom.kdiab.users.application.service.DoctorPatientService
import org.javafreedom.kdiab.users.application.service.InvitationService
import org.javafreedom.kdiab.users.application.service.UserService
import org.javafreedom.kdiab.users.domain.repository.DoctorInvitationRepository
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort
import org.javafreedom.kdiab.users.domain.repository.UserProfileRepository
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository
import org.javafreedom.kdiab.users.module

private fun Application.installMockDiWithInvitationService(
    mockInvitationService: InvitationService,
) {
    val mockIdentityProvider = mockk<IdentityProviderPort>(relaxed = true)
    val mockSettingsRepo = mockk<UserSettingsRepository>(relaxed = true)
    val mockDoctorRepo = mockk<DoctorPatientRepository>(relaxed = true)
    val mockInvitationRepo = mockk<DoctorInvitationRepository>(relaxed = true)
    val mockUserProfileRepo = mockk<UserProfileRepository>(relaxed = true)
    val mockApiKeyService = mockk<ApiKeyService>(relaxed = true)

    install(DI) { }
    dependencies {
        provide<IdentityProviderPort> { mockIdentityProvider }
        provide<UserSettingsRepository> { mockSettingsRepo }
        provide<DoctorPatientRepository> { mockDoctorRepo }
        provide<DoctorInvitationRepository> { mockInvitationRepo }
        provide<UserProfileRepository> { mockUserProfileRepo }
        provide<UserService> {
            UserService(mockIdentityProvider, mockSettingsRepo, mockDoctorRepo, mockUserProfileRepo)
        }
        provide<DoctorPatientService> { DoctorPatientService(mockDoctorRepo, mockIdentityProvider) }
        provide<InvitationService> { mockInvitationService }
        provide<ApiKeyService> { mockApiKeyService }
    }
}

class InternalRoutesTest {

    private companion object {
        const val JWT_SECRET = "test-secret-for-unit-tests-only"
        const val ISSUER = "http://localhost:8081/realms/kdiab"
        const val INTERNAL_TOKEN = "test-internal-token"
    }

    private fun routeTest(
        internalApiToken: String? = null,
        block: suspend ApplicationTestBuilder.(invitationService: InvitationService) -> Unit,
    ) {
        val mockInvitationService = mockk<InvitationService>(relaxed = true)
        val configEntries = mutableListOf(
            "jwt.domain" to ISSUER,
            "jwt.audience" to "users",
            "jwt.realm" to "kdiab",
            "jwt.test" to "true",
            "jwt.secret" to JWT_SECRET,
            "keycloakAdmin.clientSecret" to "test-secret",
            "keycloakAdmin.url" to "http://localhost:8081",
            "keycloakAdmin.realm" to "kdiab",
            "keycloakAdmin.clientId" to "kdiab-users-service",
            "storage.jdbcUrl" to "jdbc:h2:mem:test",
            "storage.username" to "root",
            "storage.password" to "test",
            "app.initDatabase" to "false",
        )
        if (internalApiToken != null) configEntries += "app.internalApiToken" to internalApiToken

        testApplication {
            environment { config = MapApplicationConfig(*configEntries.toTypedArray()) }
            application {
                installMockDiWithInvitationService(mockInvitationService)
                module()
            }
            block(mockInvitationService)
        }
    }

    @Test
    fun `POST internal invitations expire returns 200 with expired count`() = routeTest { invitationService ->
        coEvery { invitationService.expireOldInvitations(any()) } returns 3
        val response = client.post("/internal/invitations/expire")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"expired\":3") || body.contains("\"expired\": 3"), "body=$body")
    }

    @Test
    fun `POST internal invitations expire returns zero when nothing expired`() =
        routeTest { invitationService ->
            coEvery { invitationService.expireOldInvitations(any()) } returns 0
            val response = client.post("/internal/invitations/expire")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"expired\":0") || body.contains("\"expired\": 0"), "body=$body")
        }

    @Test
    fun `POST internal invitations expire requires no auth token when INTERNAL_API_TOKEN not configured`() =
        routeTest { invitationService ->
            coEvery { invitationService.expireOldInvitations(any()) } returns 0
            val response = client.post("/internal/invitations/expire")
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `POST internal invitations expire returns 200 when correct token header provided`() =
        routeTest(internalApiToken = INTERNAL_TOKEN) { invitationService ->
            coEvery { invitationService.expireOldInvitations(any()) } returns 1
            val response = client.post("/internal/invitations/expire") {
                header("X-Internal-Token", INTERNAL_TOKEN)
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `POST internal invitations expire returns 401 when token configured but header missing`() =
        routeTest(internalApiToken = INTERNAL_TOKEN) { _ ->
            val response = client.post("/internal/invitations/expire")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `POST internal invitations expire returns 401 when wrong token provided`() =
        routeTest(internalApiToken = INTERNAL_TOKEN) { _ ->
            val response = client.post("/internal/invitations/expire") {
                header("X-Internal-Token", "wrong-token")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}

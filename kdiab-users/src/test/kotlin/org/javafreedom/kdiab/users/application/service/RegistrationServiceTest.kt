@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakAdminClient
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakRole
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakUser

class RegistrationServiceTest {

    private val keycloak = mockk<KeycloakAdminClient>()
    private val settingsRepo = mockk<UserSettingsRepository>()
    private val newUserId = Uuid.parse("99999999-9999-9999-9999-999999999999")

    @Test
    fun `register creates user and assigns PATIENT role when approval not required`() = runTest {
        val service = RegistrationService(keycloak, settingsRepo, requiresApproval = false)
        coEvery { keycloak.listUsers(search = "new@example.com", max = 1) } returns emptyList()
        coEvery { keycloak.createUser(any()) } returns newUserId
        coEvery { keycloak.getRealmRole("PATIENT") } returns KeycloakRole("role-id", "PATIENT")
        coEvery { keycloak.assignRoles(any(), any()) } returns Unit
        coEvery { settingsRepo.save(any()) } answers { firstArg() }

        val userId = service.register("new@example.com", "New User", "password123")

        assertEquals(newUserId, userId)
        coVerify(exactly = 1) { keycloak.assignRoles(newUserId, any()) }
    }

    @Test
    fun `register creates user without role when approval required`() = runTest {
        val service = RegistrationService(keycloak, settingsRepo, requiresApproval = true)
        coEvery { keycloak.listUsers(search = "pending@example.com", max = 1) } returns emptyList()
        coEvery { keycloak.createUser(any()) } returns newUserId
        coEvery { settingsRepo.save(any()) } answers { firstArg() }

        service.register("pending@example.com", "Pending User", "password123")

        coVerify(exactly = 0) { keycloak.assignRoles(any(), any()) }
    }

    @Test
    fun `register throws BusinessValidationException when email already exists`() = runTest {
        val service = RegistrationService(keycloak, settingsRepo, requiresApproval = false)
        coEvery { keycloak.listUsers(search = "exists@example.com", max = 1) } returns listOf(
            KeycloakUser(id = "existing-id", email = "exists@example.com")
        )

        assertFailsWith<BusinessValidationException> {
            service.register("exists@example.com", "Existing", "pass")
        }
        coVerify(exactly = 0) { keycloak.createUser(any()) }
    }

    @Test
    fun `register seeds default settings after user creation`() = runTest {
        val service = RegistrationService(keycloak, settingsRepo, requiresApproval = true)
        coEvery { keycloak.listUsers(search = any(), max = 1) } returns emptyList()
        coEvery { keycloak.createUser(any()) } returns newUserId
        coEvery { settingsRepo.save(any()) } answers { firstArg() }

        service.register("seed@example.com", "Seed User", "pass")

        coVerify(exactly = 1) { settingsRepo.save(match { it.userId == newUserId }) }
    }
}

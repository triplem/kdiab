@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakAdminClient
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakRole

class RegistrationServiceTest {

    private val keycloak = mockk<KeycloakAdminClient>()
    private val settingsRepo = mockk<UserSettingsRepository>()
    private val newUserId = Uuid.parse("99999999-9999-9999-9999-999999999999")

    @Test
    fun `register creates user and assigns PATIENT role when approval not required`() = runTest {
        val service = RegistrationService(keycloak, settingsRepo, requiresApproval = false)
        coEvery { keycloak.createUser(any()) } returns newUserId
        coEvery { keycloak.getRealmRole("PATIENT") } returns KeycloakRole("role-id", "PATIENT")
        coEvery { keycloak.assignRoles(any(), any()) } returns Unit
        coEvery { settingsRepo.save(any()) } answers { firstArg() }

        val userId = service.register("new@example.com", "New User", "password123")

        assertNotNull(userId)
        coVerify(exactly = 1) { keycloak.assignRoles(newUserId, any()) }
    }

    @Test
    fun `register creates user without role when approval required`() = runTest {
        val service = RegistrationService(keycloak, settingsRepo, requiresApproval = true)
        coEvery { keycloak.createUser(any()) } returns newUserId
        coEvery { settingsRepo.save(any()) } answers { firstArg() }

        service.register("pending@example.com", "Pending User", "password123")

        coVerify(exactly = 0) { keycloak.assignRoles(any(), any()) }
    }

    @Test
    fun `register silently succeeds when email already registered to prevent user enumeration`() = runTest {
        val service = RegistrationService(keycloak, settingsRepo, requiresApproval = false)
        coEvery { keycloak.createUser(any()) } throws ConflictException("Email already registered in identity provider")

        // Must not throw — caller cannot distinguish new registration from duplicate
        val userId = service.register("exists@example.com", "Existing", "pass")

        assertNotNull(userId)
        coVerify(exactly = 0) { settingsRepo.save(any()) }
        coVerify(exactly = 0) { keycloak.assignRoles(any(), any()) }
    }

    @Test
    fun `register seeds default settings after user creation`() = runTest {
        val service = RegistrationService(keycloak, settingsRepo, requiresApproval = true)
        coEvery { keycloak.createUser(any()) } returns newUserId
        coEvery { settingsRepo.save(any()) } answers { firstArg() }

        service.register("seed@example.com", "Seed User", "pass")

        coVerify(exactly = 1) { settingsRepo.save(match { it.userId == newUserId }) }
    }
}

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
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository

class RegistrationServiceTest {

    private val identityProvider = mockk<IdentityProviderPort>()
    private val settingsRepo = mockk<UserSettingsRepository>()
    private val newUserId = Uuid.parse("99999999-9999-9999-9999-999999999999")

    @Test
    fun `register creates user and assigns PATIENT role when approval not required`() = runTest {
        val service = RegistrationService(identityProvider, settingsRepo, requiresApproval = false)
        coEvery { identityProvider.createUser(any()) } returns newUserId
        coEvery { identityProvider.assignRoles(any(), any()) } returns Unit
        coEvery { settingsRepo.save(any()) } answers { firstArg() }

        val userId = service.register("new@example.com", "New User", "password123")

        assertNotNull(userId)
        coVerify(exactly = 1) { identityProvider.assignRoles(newUserId, setOf(Role.PATIENT)) }
    }

    @Test
    fun `register creates user without role when approval required`() = runTest {
        val service = RegistrationService(identityProvider, settingsRepo, requiresApproval = true)
        coEvery { identityProvider.createUser(any()) } returns newUserId
        coEvery { settingsRepo.save(any()) } answers { firstArg() }

        service.register("pending@example.com", "Pending User", "password123")

        coVerify(exactly = 0) { identityProvider.assignRoles(any(), any()) }
    }

    @Test
    fun `register silently succeeds when email already registered to prevent user enumeration`() = runTest {
        val service = RegistrationService(identityProvider, settingsRepo, requiresApproval = false)
        coEvery { identityProvider.createUser(any()) } throws ConflictException("Email already registered in identity provider")

        // Must not throw — caller cannot distinguish new registration from duplicate
        val userId = service.register("exists@example.com", "Existing", "pass")

        assertNotNull(userId)
        coVerify(exactly = 0) { settingsRepo.save(any()) }
        coVerify(exactly = 0) { identityProvider.assignRoles(any(), any()) }
    }

    @Test
    fun `register seeds default settings after user creation`() = runTest {
        val service = RegistrationService(identityProvider, settingsRepo, requiresApproval = true)
        coEvery { identityProvider.createUser(any()) } returns newUserId
        coEvery { settingsRepo.save(any()) } answers { firstArg() }

        service.register("seed@example.com", "Seed User", "pass")

        coVerify(exactly = 1) { settingsRepo.save(match { it.userId == newUserId }) }
    }
}

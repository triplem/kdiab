@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.domain.model.UserSettings
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakAdminClient
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakRole
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakUser

class UserServiceTest {

    private val keycloak = mockk<KeycloakAdminClient>()
    private val settingsRepo = mockk<UserSettingsRepository>()
    private val doctorPatientRepo = mockk<DoctorPatientRepository>()
    private val service = UserService(keycloak, settingsRepo, doctorPatientRepo)

    private val adminId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val now = Clock.System.now()

    private fun adminPrincipal() = UserPrincipal(adminId, setOf(Role.ADMIN), emptySet())
    private fun patientPrincipal(id: Uuid = userId) = UserPrincipal(id, setOf(Role.PATIENT), emptySet())

    private fun kcUser(id: Uuid = userId) = KeycloakUser(
        id = id.toString(), email = "test@example.com",
        firstName = "Test", lastName = "User", enabled = true,
    )

    private fun settings(id: Uuid = userId) = UserSettings(
        userId = id, createdAt = now, updatedAt = now,
    )

    @Test
    fun `getMe returns user with settings from repo`() = runTest {
        coEvery { keycloak.getUser(userId) } returns kcUser()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        val principal = patientPrincipal()
        val user = service.getMe(principal)
        assertEquals(userId, user.userId)
        assertEquals("test@example.com", user.email)
    }

    @Test
    fun `getMe seeds default settings when none exist`() = runTest {
        coEvery { keycloak.getUser(userId) } returns kcUser()
        coEvery { settingsRepo.findByUserId(userId) } returns null
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        service.getMe(patientPrincipal())
        coVerify(exactly = 1) { settingsRepo.save(any()) }
    }

    @Test
    fun `updateMySettings patches DB settings`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(timezone = "Europe/Berlin")
        val result = service.updateMySettings(principal, patch)
        assertEquals("Europe/Berlin", result.timezone)
    }

    @Test
    fun `updateMySettings persists glucoseUnit to DB without Keycloak write-back`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(glucoseUnit = "mmol/L")
        val result = service.updateMySettings(principal, patch)
        assertEquals("mmol/L", result.glucoseUnit)
        coVerify(exactly = 0) { keycloak.updateUserAttributes(any(), any()) }
    }

    @Test
    fun `updateMySettings persists weightUnit to DB without Keycloak write-back`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(weightUnit = "lbs")
        val result = service.updateMySettings(principal, patch)
        assertEquals("lbs", result.weightUnit)
        coVerify(exactly = 0) { keycloak.updateUserAttributes(any(), any()) }
    }

    @Test
    fun `listUsers throws AuthorizationException for non-admin`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.listUsers(patientPrincipal(), null, 0, 20)
        }
    }

    @Test
    fun `listUsers calls keycloak for admin`() = runTest {
        coEvery { keycloak.listUsers(any(), any(), any()) } returns listOf(kcUser())
        coEvery { keycloak.getUserRoles(userId) } returns emptyList()
        coEvery { settingsRepo.findByUserId(any()) } returns null
        val results = service.listUsers(adminPrincipal(), null, 0, 20)
        assertEquals(1, results.size)
    }

    @Test
    fun `createUser throws AuthorizationException for non-admin`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.createUser(patientPrincipal(), "e@e.com", "Name", "pass", Role.PATIENT)
        }
    }

    @Test
    fun `createUser creates keycloak user and seeds settings`() = runTest {
        val newId = Uuid.parse("22222222-2222-2222-2222-222222222222")
        coEvery { keycloak.createUser(any()) } returns newId
        coEvery { keycloak.getRealmRole(any()) } returns KeycloakRole("role-id", "PATIENT")
        coEvery { keycloak.assignRoles(any(), any()) } returns Unit
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val user = service.createUser(adminPrincipal(), "new@example.com", "New User", "pass", Role.PATIENT)
        assertEquals(newId, user.userId)
        coVerify(exactly = 1) { settingsRepo.save(any()) }
    }

    @Test
    fun `deleteUser throws AuthorizationException for non-admin`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.deleteUser(patientPrincipal(), userId)
        }
    }

    @Test
    fun `deleteUser removes user from keycloak and DB`() = runTest {
        coEvery { keycloak.deleteUser(userId) } returns Unit
        coEvery { settingsRepo.delete(userId) } returns Unit
        coEvery { doctorPatientRepo.deleteByUserId(userId) } returns Unit
        service.deleteUser(adminPrincipal(), userId)
        coVerify(exactly = 1) { keycloak.deleteUser(userId) }
        coVerify(exactly = 1) { settingsRepo.delete(userId) }
        coVerify(exactly = 1) { doctorPatientRepo.deleteByUserId(userId) }
    }

    @Test
    fun `getUser allows self access`() = runTest {
        coEvery { keycloak.getUser(userId) } returns kcUser()
        coEvery { keycloak.getUserRoles(userId) } returns listOf(KeycloakRole("r-id", "PATIENT"))
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        val user = service.getUser(patientPrincipal(), userId)
        assertEquals(userId, user.userId)
    }

    @Test
    fun `getUser denies access to other user for patient`() = runTest {
        val otherId = Uuid.parse("33333333-3333-3333-3333-333333333333")
        assertFailsWith<AuthorizationException> {
            service.getUser(patientPrincipal(), otherId)
        }
    }

    // --- Alarm threshold validation tests (Issue #759) ---

    @Test
    fun `updateMySettings accepts valid alarm order`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(
            alarmUrgentHigh = 260, alarmHigh = 200, alarmLow = 75, alarmUrgentLow = 55,
        )
        val result = service.updateMySettings(principal, patch)
        assertEquals(260, result.alarmUrgentHigh)
        assertEquals(200, result.alarmHigh)
        assertEquals(75, result.alarmLow)
        assertEquals(55, result.alarmUrgentLow)
    }

    @Test
    fun `updateMySettings throws when urgentLow greater than low`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(
            alarmUrgentHigh = 260, alarmHigh = 200, alarmLow = 70, alarmUrgentLow = 90,
        )
        assertFailsWith<BusinessValidationException> {
            service.updateMySettings(principal, patch)
        }
    }

    @Test
    fun `updateMySettings throws when high greater than urgentHigh`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(
            alarmUrgentHigh = 200, alarmHigh = 260, alarmLow = 75, alarmUrgentLow = 55,
        )
        assertFailsWith<BusinessValidationException> {
            service.updateMySettings(principal, patch)
        }
    }

    @Test
    fun `updateMySettings throws when urgentHigh exceeds clinical maximum`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(
            alarmUrgentHigh = 450, alarmHigh = 200, alarmLow = 75, alarmUrgentLow = 55,
        )
        assertFailsWith<BusinessValidationException> {
            service.updateMySettings(principal, patch)
        }
    }

    @Test
    fun `updateMySettings throws when urgentLow below clinical minimum`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(
            alarmUrgentHigh = 260, alarmHigh = 200, alarmLow = 75, alarmUrgentLow = 30,
        )
        assertFailsWith<BusinessValidationException> {
            service.updateMySettings(principal, patch)
        }
    }
}

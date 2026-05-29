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
import kotlinx.datetime.LocalDate
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.domain.model.UserSettings
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort
import org.javafreedom.kdiab.users.domain.repository.IdentityUserProfile
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository

class UserServiceTest {

    private val identityProvider = mockk<IdentityProviderPort>()
    private val settingsRepo = mockk<UserSettingsRepository>()
    private val doctorPatientRepo = mockk<DoctorPatientRepository>()
    private val service = UserService(identityProvider, settingsRepo, doctorPatientRepo)

    private val adminId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val now = Clock.System.now()

    private fun adminPrincipal() = UserPrincipal(adminId, setOf(Role.ADMIN), emptySet())
    private fun patientPrincipal(id: Uuid = userId) = UserPrincipal(id, setOf(Role.PATIENT), emptySet())

    private fun identityProfile(id: Uuid = userId) = IdentityUserProfile(
        id = id.toString(), email = "test@example.com",
        firstName = "Test", lastName = "User", enabled = true,
    )

    private fun settings(id: Uuid = userId) = UserSettings(
        userId = id, createdAt = now, updatedAt = now,
    )

    @Test
    fun `getMe returns user with settings from repo`() = runTest {
        coEvery { identityProvider.getUserProfile(userId) } returns identityProfile()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        val principal = patientPrincipal()
        val user = service.getMe(principal)
        assertEquals(userId, user.userId)
        assertEquals("test@example.com", user.email)
    }

    @Test
    fun `getMe seeds default settings when none exist`() = runTest {
        coEvery { identityProvider.getUserProfile(userId) } returns identityProfile()
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
    fun `updateMySettings persists glucoseUnit to DB without identity provider write-back`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(glucoseUnit = "mmol/L")
        val result = service.updateMySettings(principal, patch)
        assertEquals("mmol/L", result.glucoseUnit)
        coVerify(exactly = 0) { identityProvider.updateUserAttributes(any(), any()) }
    }

    @Test
    fun `updateMySettings persists weightUnit to DB without identity provider write-back`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(weightUnit = "lbs")
        val result = service.updateMySettings(principal, patch)
        assertEquals("lbs", result.weightUnit)
        coVerify(exactly = 0) { identityProvider.updateUserAttributes(any(), any()) }
    }

    @Test
    fun `listUsers throws AuthorizationException for non-admin`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.listUsers(patientPrincipal(), null, 0, 20)
        }
    }

    @Test
    fun `listUsers calls identity provider for admin`() = runTest {
        coEvery { identityProvider.listUserProfiles(any(), any(), any()) } returns listOf(identityProfile())
        coEvery { identityProvider.getUserRoles(userId) } returns emptySet()
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
    fun `createUser creates identity provider user and seeds settings`() = runTest {
        val newId = Uuid.parse("22222222-2222-2222-2222-222222222222")
        coEvery { identityProvider.createUser(any()) } returns newId
        coEvery { identityProvider.assignRoles(any(), any()) } returns Unit
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
    fun `deleteUser removes user from identity provider and DB`() = runTest {
        coEvery { identityProvider.deleteUser(userId) } returns Unit
        coEvery { settingsRepo.delete(userId) } returns Unit
        coEvery { doctorPatientRepo.deleteByUserId(userId) } returns Unit
        service.deleteUser(adminPrincipal(), userId)
        coVerify(exactly = 1) { identityProvider.deleteUser(userId) }
        coVerify(exactly = 1) { settingsRepo.delete(userId) }
        coVerify(exactly = 1) { doctorPatientRepo.deleteByUserId(userId) }
    }

    @Test
    fun `getUser allows self access`() = runTest {
        coEvery { identityProvider.getUserProfile(userId) } returns identityProfile()
        coEvery { identityProvider.getUserRoles(userId) } returns setOf(Role.PATIENT)
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

    // --- birthday and diabetesSince tests (Issue #1116) ---

    @Test
    fun `updateMySettings persists birthday and diabetesSince`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(
            birthday = LocalDate(1990, 5, 15),
            diabetesSince = 2010,
        )
        val result = service.updateMySettings(principal, patch)
        assertEquals(LocalDate(1990, 5, 15), result.birthday)
        assertEquals(2010, result.diabetesSince)
    }

    @Test
    fun `updateMySettings accepts diabetesSince at boundary year 1900`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(diabetesSince = 1900)
        val result = service.updateMySettings(principal, patch)
        assertEquals(1900, result.diabetesSince)
    }

    @Test
    fun `updateMySettings throws when diabetesSince is before 1900`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        val patch = SettingsPatch(diabetesSince = 1899)
        assertFailsWith<BusinessValidationException> {
            service.updateMySettings(principal, patch)
        }
    }

    @Test
    fun `updateMySettings throws when diabetesSince is in the future`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        // Use a year well beyond any plausible current year
        val patch = SettingsPatch(diabetesSince = 9999)
        assertFailsWith<BusinessValidationException> {
            service.updateMySettings(principal, patch)
        }
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.domain.model.AlarmThresholds
import org.javafreedom.kdiab.users.domain.model.UserSettings
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort
import org.javafreedom.kdiab.users.domain.repository.IdentityUserProfile
import org.javafreedom.kdiab.users.domain.repository.UserProfileRepository
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository

class UserServiceTest {

    private val identityProvider = mockk<IdentityProviderPort>()
    private val settingsRepo = mockk<UserSettingsRepository>()
    private val doctorPatientRepo = mockk<DoctorPatientRepository>()
    private val userProfileRepo = mockk<UserProfileRepository>()
    private val service = UserService(identityProvider, settingsRepo, doctorPatientRepo, userProfileRepo)

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
        coEvery { userProfileRepo.findBirthdayByUserId(userId) } returns null
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
        coEvery { userProfileRepo.findBirthdayByUserId(userId) } returns null
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
        assertEquals("Europe/Berlin", result.locale.timezone)
    }

    @Test
    fun `updateMySettings persists glucoseUnit to DB without identity provider write-back`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(glucoseUnit = "mmol/L")
        val result = service.updateMySettings(principal, patch)
        assertEquals("mmol/L", result.units.glucoseUnit)
        coVerify(exactly = 0) { identityProvider.updateUserAttributes(any(), any()) }
    }

    @Test
    fun `updateMySettings persists weightUnit to DB without identity provider write-back`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(weightUnit = "lbs")
        val result = service.updateMySettings(principal, patch)
        assertEquals("lbs", result.units.weightUnit)
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
        coEvery { userProfileRepo.findBirthdaysByUserIds(any()) } returns emptyMap()
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
        coEvery { userProfileRepo.delete(userId) } returns Unit
        coEvery { doctorPatientRepo.deleteByUserId(userId) } returns Unit
        service.deleteUser(adminPrincipal(), userId)
        coVerify(exactly = 1) { identityProvider.deleteUser(userId) }
        coVerify(exactly = 1) { settingsRepo.delete(userId) }
        coVerify(exactly = 1) { userProfileRepo.delete(userId) }
        coVerify(exactly = 1) { doctorPatientRepo.deleteByUserId(userId) }
    }

    @Test
    fun `getUser allows self access`() = runTest {
        coEvery { identityProvider.getUserProfile(userId) } returns identityProfile()
        coEvery { identityProvider.getUserRoles(userId) } returns setOf(Role.PATIENT)
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { userProfileRepo.findBirthdayByUserId(userId) } returns null
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
        assertEquals(260, result.alarms?.urgentHigh)
        assertEquals(200, result.alarms?.high)
        assertEquals(75, result.alarms?.low)
        assertEquals(55, result.alarms?.urgentLow)
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

    // --- birthday tests (Issue #1169) ---

    @Test
    fun `updateMyProfile saves birthday via userProfileRepo`() = runTest {
        val principal = patientPrincipal()
        val birthday = LocalDate(1990, 5, 15)
        coEvery { userProfileRepo.saveBirthday(userId, birthday) } returns Unit
        service.updateMyProfile(principal, birthday)
        coVerify(exactly = 1) { userProfileRepo.saveBirthday(userId, birthday) }
    }

    @Test
    fun `updateMyProfile clears birthday when null`() = runTest {
        val principal = patientPrincipal()
        coEvery { userProfileRepo.saveBirthday(userId, null) } returns Unit
        service.updateMyProfile(principal, null)
        coVerify(exactly = 1) { userProfileRepo.saveBirthday(userId, null) }
    }

    // --- diabetesSince tests (Issue #1116) ---

    @Test
    fun `updateMySettings accepts diabetesSince at boundary year 1900`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(diabetesSince = 1900)
        val result = service.updateMySettings(principal, patch)
        assertEquals(1900, result.diabetes.diabetesSince)
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

    // --- updateUser tests ---

    @Test
    fun `updateUser throws AuthorizationException for non-admin`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.updateUser(patientPrincipal(), userId, null, null)
        }
    }

    @Test
    fun `updateUser with no changes returns current user`() = runTest {
        val principal = adminPrincipal()
        coEvery { identityProvider.getUserProfile(userId) } returns identityProfile()
        coEvery { identityProvider.getUserRoles(userId) } returns setOf(Role.PATIENT)
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { userProfileRepo.findBirthdayByUserId(userId) } returns null
        val user = service.updateUser(principal, userId, displayName = null, role = null)
        assertEquals(userId, user.userId)
        coVerify(exactly = 2) { identityProvider.getUserProfile(userId) }
    }

    @Test
    fun `updateUser with displayName updates identity provider`() = runTest {
        val principal = adminPrincipal()
        coEvery { identityProvider.getUserProfile(userId) } returns identityProfile()
        coEvery { identityProvider.updateUser(userId, any()) } returns Unit
        coEvery { identityProvider.getUserRoles(userId) } returns setOf(Role.PATIENT)
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { userProfileRepo.findBirthdayByUserId(userId) } returns null
        val user = service.updateUser(principal, userId, displayName = "New Name", role = null)
        assertEquals(userId, user.userId)
        coVerify(exactly = 1) { identityProvider.updateUser(userId, any()) }
    }

    @Test
    fun `updateUser with role removes old roles and assigns new one`() = runTest {
        val principal = adminPrincipal()
        coEvery { identityProvider.getUserProfile(userId) } returns identityProfile()
        coEvery { identityProvider.getUserRoles(userId) } returns setOf(Role.PATIENT)
        coEvery { identityProvider.removeRoles(userId, any()) } returns Unit
        coEvery { identityProvider.assignRoles(userId, any()) } returns Unit
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { userProfileRepo.findBirthdayByUserId(userId) } returns null
        service.updateUser(principal, userId, displayName = null, role = Role.DOCTOR)
        coVerify(exactly = 1) { identityProvider.removeRoles(userId, setOf(Role.PATIENT)) }
        coVerify(exactly = 1) { identityProvider.assignRoles(userId, setOf(Role.DOCTOR)) }
    }

    @Test
    fun `updateUser with role when user has no existing roles skips removeRoles`() = runTest {
        val principal = adminPrincipal()
        coEvery { identityProvider.getUserProfile(userId) } returns identityProfile()
        coEvery { identityProvider.getUserRoles(userId) } returns emptySet()
        coEvery { identityProvider.assignRoles(userId, any()) } returns Unit
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { userProfileRepo.findBirthdayByUserId(userId) } returns null
        service.updateUser(principal, userId, displayName = null, role = Role.PATIENT)
        coVerify(exactly = 0) { identityProvider.removeRoles(any(), any()) }
        coVerify(exactly = 1) { identityProvider.assignRoles(userId, setOf(Role.PATIENT)) }
    }

    @Test
    fun `updateUser with displayName and role performs both updates`() = runTest {
        val principal = adminPrincipal()
        coEvery { identityProvider.getUserProfile(userId) } returns identityProfile()
        coEvery { identityProvider.updateUser(userId, any()) } returns Unit
        coEvery { identityProvider.getUserRoles(userId) } returns setOf(Role.PATIENT)
        coEvery { identityProvider.removeRoles(userId, any()) } returns Unit
        coEvery { identityProvider.assignRoles(userId, any()) } returns Unit
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { userProfileRepo.findBirthdayByUserId(userId) } returns null
        service.updateUser(principal, userId, displayName = "Dr. House", role = Role.DOCTOR)
        coVerify(exactly = 1) { identityProvider.updateUser(userId, any()) }
        coVerify(exactly = 1) { identityProvider.assignRoles(userId, setOf(Role.DOCTOR)) }
    }

    // --- createUser rollback test ---

    @Test
    fun `createUser rolls back identity provider user when settings save fails`() = runTest {
        val newId = Uuid.parse("22222222-2222-2222-2222-222222222222")
        coEvery { identityProvider.createUser(any()) } returns newId
        coEvery { identityProvider.assignRoles(any(), any()) } returns Unit
        coEvery { settingsRepo.save(any()) } throws RuntimeException("DB unavailable")
        coEvery { identityProvider.deleteUser(newId) } returns Unit
        assertFailsWith<RuntimeException> {
            service.createUser(adminPrincipal(), "new@example.com", "New User", "pass", Role.PATIENT)
        }
        coVerify(exactly = 1) { identityProvider.deleteUser(newId) }
    }

    @Test
    fun `createUser propagates exception when rollback also fails`() = runTest {
        val newId = Uuid.parse("22222222-2222-2222-2222-222222222222")
        coEvery { identityProvider.createUser(any()) } returns newId
        coEvery { identityProvider.assignRoles(any(), any()) } returns Unit
        coEvery { settingsRepo.save(any()) } throws RuntimeException("DB unavailable")
        coEvery { identityProvider.deleteUser(newId) } throws RuntimeException("Keycloak down")
        // The original DB exception must still propagate
        assertFailsWith<RuntimeException> {
            service.createUser(adminPrincipal(), "new@example.com", "New User", "pass", Role.PATIENT)
        }
    }

    // --- listUsers edge cases ---

    @Test
    fun `listUsers skips profiles with null id`() = runTest {
        val profileWithNullId = IdentityUserProfile(id = null, email = "no-id@example.com")
        coEvery { identityProvider.listUserProfiles(any(), any(), any()) } returns listOf(profileWithNullId)
        coEvery { userProfileRepo.findBirthdaysByUserIds(any()) } returns emptyMap()
        val results = service.listUsers(adminPrincipal(), null, 0, 20)
        assertEquals(0, results.size)
    }

    @Test
    fun `listUsers skips profiles with unparseable UUID id`() = runTest {
        val profileWithBadId = IdentityUserProfile(id = "not-a-uuid", email = "bad@example.com")
        coEvery { identityProvider.listUserProfiles(any(), any(), any()) } returns listOf(profileWithBadId)
        coEvery { userProfileRepo.findBirthdaysByUserIds(any()) } returns emptyMap()
        val results = service.listUsers(adminPrincipal(), null, 0, 20)
        assertEquals(0, results.size)
    }

    @Test
    fun `listUsers passes search parameter to identity provider`() = runTest {
        coEvery { identityProvider.listUserProfiles("alice", 0, 10) } returns listOf(identityProfile())
        coEvery { identityProvider.getUserRoles(userId) } returns emptySet()
        coEvery { settingsRepo.findByUserId(any()) } returns null
        coEvery { userProfileRepo.findBirthdaysByUserIds(any()) } returns emptyMap()
        val results = service.listUsers(adminPrincipal(), "alice", 0, 10)
        assertEquals(1, results.size)
        coVerify(exactly = 1) { identityProvider.listUserProfiles("alice", 0, 10) }
    }

    @Test
    fun `listUsers batch-loads birthdays in a single call`() = runTest {
        val userId2 = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val birthday = LocalDate(1990, 5, 15)
        coEvery { identityProvider.listUserProfiles(any(), any(), any()) } returns
            listOf(identityProfile(userId), identityProfile(userId2))
        coEvery { identityProvider.getUserRoles(any()) } returns emptySet()
        coEvery { settingsRepo.findByUserId(any()) } returns null
        coEvery { userProfileRepo.findBirthdaysByUserIds(setOf(userId, userId2)) } returns
            mapOf(userId to birthday, userId2 to null)
        val results = service.listUsers(adminPrincipal(), null, 0, 20)
        assertEquals(2, results.size)
        assertEquals(birthday, results.first { it.userId == userId }.birthday)
        coVerify(exactly = 1) { userProfileRepo.findBirthdaysByUserIds(any()) }
        coVerify(exactly = 0) { userProfileRepo.findBirthdayByUserId(any()) }
    }

    // --- toDomain displayName derivation edge cases ---

    @Test
    fun `getMe uses username as displayName when firstName and lastName are blank`() = runTest {
        val profileBlankNames = IdentityUserProfile(
            id = userId.toString(),
            email = "test@example.com",
            firstName = "",
            lastName = "",
            username = "myusername",
            enabled = true,
        )
        coEvery { identityProvider.getUserProfile(userId) } returns profileBlankNames
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { userProfileRepo.findBirthdayByUserId(userId) } returns null
        val user = service.getMe(patientPrincipal())
        assertEquals("myusername", user.displayName)
    }

    @Test
    fun `getMe uses email as displayName when firstName lastName and username are all blank`() = runTest {
        val profileAllBlank = IdentityUserProfile(
            id = userId.toString(),
            email = "fallback@example.com",
            firstName = null,
            lastName = null,
            username = null,
            enabled = true,
        )
        coEvery { identityProvider.getUserProfile(userId) } returns profileAllBlank
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { userProfileRepo.findBirthdayByUserId(userId) } returns null
        val user = service.getMe(patientPrincipal())
        assertEquals("fallback@example.com", user.displayName)
    }

    @Test
    fun `getMe uses only firstName as displayName when lastName is null`() = runTest {
        val profileFirstOnly = IdentityUserProfile(
            id = userId.toString(),
            email = "test@example.com",
            firstName = "OnlyFirst",
            lastName = null,
            enabled = true,
        )
        coEvery { identityProvider.getUserProfile(userId) } returns profileFirstOnly
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { userProfileRepo.findBirthdayByUserId(userId) } returns null
        val user = service.getMe(patientPrincipal())
        assertEquals("OnlyFirst", user.displayName)
    }

    // --- updateMySettings additional branches ---

    @Test
    fun `updateMySettings seeds default settings when none exist`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns null
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(timezone = "America/New_York")
        val result = service.updateMySettings(principal, patch)
        assertEquals("America/New_York", result.locale.timezone)
    }

    @Test
    fun `updateMySettings throws for invalid glucose unit`() = runTest {
        val principal = patientPrincipal()
        val patch = SettingsPatch(glucoseUnit = "invalid-unit")
        assertFailsWith<BusinessValidationException> {
            service.updateMySettings(principal, patch)
        }
    }

    @Test
    fun `updateMySettings throws for invalid weight unit`() = runTest {
        val principal = patientPrincipal()
        val patch = SettingsPatch(weightUnit = "stone")
        assertFailsWith<BusinessValidationException> {
            service.updateMySettings(principal, patch)
        }
    }

    @Test
    fun `updateMySettings merges only urgentHigh with existing alarm thresholds`() = runTest {
        val principal = patientPrincipal()
        val existingWithAlarms = settings().copy(
            alarms = AlarmThresholds(
                urgentHigh = 260, high = 180, low = 70, urgentLow = 55,
            )
        )
        coEvery { settingsRepo.findByUserId(userId) } returns existingWithAlarms
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        // Only updating urgentHigh — other three come from existing
        val patch = SettingsPatch(alarmUrgentHigh = 300)
        val result = service.updateMySettings(principal, patch)
        assertEquals(300, result.alarms?.urgentHigh)
        assertEquals(180, result.alarms?.high)
        assertEquals(70, result.alarms?.low)
        assertEquals(55, result.alarms?.urgentLow)
    }

    @Test
    fun `updateMySettings with no alarm fields preserves existing alarms`() = runTest {
        val principal = patientPrincipal()
        val existingWithAlarms = settings().copy(
            alarms = AlarmThresholds(
                urgentHigh = 260, high = 180, low = 70, urgentLow = 55,
            )
        )
        coEvery { settingsRepo.findByUserId(userId) } returns existingWithAlarms
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(timezone = "UTC")
        val result = service.updateMySettings(principal, patch)
        assertEquals(260, result.alarms?.urgentHigh)
    }

    @Test
    fun `updateMySettings with partial alarms on null existing returns null alarms when missing thresholds`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings() // alarms = null
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        // Provide only one alarm field — fromNullable returns null when fewer than 4 provided
        val patch = SettingsPatch(alarmUrgentHigh = 300)
        val result = service.updateMySettings(principal, patch)
        assertNull(result.alarms)
    }

    @Test
    fun `updateMySettings persists carbAbsorptionRateGPerHour`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(carbAbsorptionRateGPerHour = 30.0)
        val result = service.updateMySettings(principal, patch)
        assertEquals(30.0, result.diabetes.carbAbsorptionRateGPerHour)
    }

    @Test
    fun `updateMySettings persists sensorDurationHours`() = runTest {
        val principal = patientPrincipal()
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { settingsRepo.save(any()) } answers { firstArg() }
        val patch = SettingsPatch(sensorDurationHours = 168)
        val result = service.updateMySettings(principal, patch)
        assertEquals(168, result.diabetes.sensorDurationHours)
    }

    // --- getUser additional test ---

    @Test
    fun `getUser returns user for admin accessing any user`() = runTest {
        val principal = adminPrincipal()
        coEvery { identityProvider.getUserProfile(userId) } returns identityProfile()
        coEvery { identityProvider.getUserRoles(userId) } returns setOf(Role.PATIENT)
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { userProfileRepo.findBirthdayByUserId(userId) } returns null
        val user = service.getUser(principal, userId)
        assertEquals(userId, user.userId)
    }

    @Test
    fun `getMe uses empty string as displayName when firstName lastName username and email are all null`() = runTest {
        val profileAllNull = IdentityUserProfile(
            id = userId.toString(),
            email = null,
            firstName = null,
            lastName = null,
            username = null,
            enabled = true,
        )
        coEvery { identityProvider.getUserProfile(userId) } returns profileAllNull
        coEvery { settingsRepo.findByUserId(userId) } returns settings()
        coEvery { userProfileRepo.findBirthdayByUserId(userId) } returns null
        val user = service.getMe(patientPrincipal())
        assertEquals("", user.displayName)
    }
}

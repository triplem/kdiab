@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.domain.model.InvitationStatus
import org.javafreedom.kdiab.users.domain.repository.DoctorInvitationRepository
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort

class InvitationServiceTest {

    private val invitationRepo = mockk<DoctorInvitationRepository>()
    private val identityProvider = mockk<IdentityProviderPort>()
    private val service = InvitationService(invitationRepo, identityProvider)

    private val adminId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val doctorId = Uuid.parse("dddddddd-dddd-dddd-dddd-dddddddddddd")
    private val patientId = Uuid.parse("b0b0b0b0-b0b0-4b00-ab00-b0b0b0b0b0b0")

    private val patientEmail = "patient@example.com"

    private fun adminPrincipal() = UserPrincipal(adminId, setOf(Role.ADMIN), emptySet())
    private fun doctorPrincipal() = UserPrincipal(doctorId, setOf(Role.DOCTOR), emptySet())
    private fun patientPrincipal() = UserPrincipal(patientId, setOf(Role.PATIENT), emptySet())

    @Test
    fun `sendInvitation happy path creates PENDING invitation`() = runTest {
        coEvery { identityProvider.getUserRoles(doctorId) } returns setOf(Role.DOCTOR)
        coEvery { identityProvider.findUserByIdentifier(patientEmail) } returns patientId
        coEvery { identityProvider.getUserRoles(patientId) } returns setOf(Role.PATIENT)
        coEvery { invitationRepo.existsPendingByDoctorAndPatient(doctorId, patientId) } returns false
        coEvery { invitationRepo.save(any()) } answers { firstArg() }

        val result = service.sendInvitation(doctorPrincipal(), doctorId, patientEmail, null)

        assertEquals(doctorId, result.doctorId)
        assertEquals(patientId, result.patientId)
        assertEquals(patientEmail, result.patientIdentifier)
        assertEquals(InvitationStatus.PENDING, result.status)
        assertNotNull(result.expiresAt)
        coVerify(exactly = 1) { invitationRepo.save(any()) }
    }

    @Test
    fun `sendInvitation admin can send on behalf of any doctor`() = runTest {
        coEvery { identityProvider.getUserRoles(doctorId) } returns setOf(Role.DOCTOR)
        coEvery { identityProvider.findUserByIdentifier(patientEmail) } returns patientId
        coEvery { identityProvider.getUserRoles(patientId) } returns setOf(Role.PATIENT)
        coEvery { invitationRepo.existsPendingByDoctorAndPatient(doctorId, patientId) } returns false
        coEvery { invitationRepo.save(any()) } answers { firstArg() }

        val result = service.sendInvitation(adminPrincipal(), doctorId, patientEmail, "Hello!")

        assertEquals(doctorId, result.doctorId)
        assertEquals("Hello!", result.message)
    }

    @Test
    fun `sendInvitation sets expiresAt 7 days after createdAt`() = runTest {
        coEvery { identityProvider.getUserRoles(doctorId) } returns setOf(Role.DOCTOR)
        coEvery { identityProvider.findUserByIdentifier(patientEmail) } returns patientId
        coEvery { identityProvider.getUserRoles(patientId) } returns setOf(Role.PATIENT)
        coEvery { invitationRepo.existsPendingByDoctorAndPatient(doctorId, patientId) } returns false
        coEvery { invitationRepo.save(any()) } answers { firstArg() }

        val result = service.sendInvitation(doctorPrincipal(), doctorId, patientEmail, null)

        val ttlSeconds = (result.expiresAt - result.createdAt).inWholeSeconds
        // Allow a small margin for test execution time
        val sevenDaysSeconds = 7L * 24 * 60 * 60
        assertEquals(sevenDaysSeconds, ttlSeconds)
    }

    @Test
    fun `sendInvitation throws ConflictException on duplicate pending invitation`() = runTest {
        coEvery { identityProvider.getUserRoles(doctorId) } returns setOf(Role.DOCTOR)
        coEvery { identityProvider.findUserByIdentifier(patientEmail) } returns patientId
        coEvery { identityProvider.getUserRoles(patientId) } returns setOf(Role.PATIENT)
        coEvery { invitationRepo.existsPendingByDoctorAndPatient(doctorId, patientId) } returns true

        assertFailsWith<ConflictException> {
            service.sendInvitation(doctorPrincipal(), doctorId, patientEmail, null)
        }
        coVerify(exactly = 0) { invitationRepo.save(any()) }
    }

    @Test
    fun `sendInvitation throws BusinessValidationException when patient identifier not found`() = runTest {
        coEvery { identityProvider.getUserRoles(doctorId) } returns setOf(Role.DOCTOR)
        coEvery { identityProvider.findUserByIdentifier(patientEmail) } returns null

        assertFailsWith<BusinessValidationException> {
            service.sendInvitation(doctorPrincipal(), doctorId, patientEmail, null)
        }
        coVerify(exactly = 0) { invitationRepo.save(any()) }
    }

    @Test
    fun `sendInvitation throws BusinessValidationException when resolved user lacks PATIENT role`() = runTest {
        coEvery { identityProvider.getUserRoles(doctorId) } returns setOf(Role.DOCTOR)
        coEvery { identityProvider.findUserByIdentifier(patientEmail) } returns patientId
        coEvery { identityProvider.getUserRoles(patientId) } returns setOf(Role.DOCTOR)

        assertFailsWith<BusinessValidationException> {
            service.sendInvitation(doctorPrincipal(), doctorId, patientEmail, null)
        }
        coVerify(exactly = 0) { invitationRepo.save(any()) }
    }

    @Test
    fun `sendInvitation throws BusinessValidationException when doctorId lacks DOCTOR role`() = runTest {
        coEvery { identityProvider.getUserRoles(doctorId) } returns setOf(Role.PATIENT)

        assertFailsWith<BusinessValidationException> {
            service.sendInvitation(adminPrincipal(), doctorId, patientEmail, null)
        }
        coVerify(exactly = 0) { invitationRepo.save(any()) }
    }

    @Test
    fun `sendInvitation throws AuthorizationException when doctor tries to act on different doctorId`() = runTest {
        val otherDoctorId = Uuid.parse("cccccccc-cccc-cccc-cccc-cccccccccccc")

        assertFailsWith<AuthorizationException> {
            service.sendInvitation(doctorPrincipal(), otherDoctorId, patientEmail, null)
        }
        coVerify(exactly = 0) { identityProvider.getUserRoles(any()) }
    }

    @Test
    fun `sendInvitation throws AuthorizationException for patient principal`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.sendInvitation(patientPrincipal(), doctorId, patientEmail, null)
        }
        coVerify(exactly = 0) { identityProvider.getUserRoles(any()) }
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.domain.model.DoctorInvitation
import org.javafreedom.kdiab.users.domain.model.InvitationAction
import org.javafreedom.kdiab.users.domain.model.InvitationStatus
import org.javafreedom.kdiab.users.domain.repository.DoctorInvitationRepository
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort
import org.javafreedom.kdiab.users.domain.repository.IdentityUserProfile

class InvitationServiceTest {

    private val invitationRepo = mockk<DoctorInvitationRepository>()
    private val identityProvider = mockk<IdentityProviderPort>()
    private val doctorPatientRepo = mockk<DoctorPatientRepository>()
    private val service = InvitationService(invitationRepo, identityProvider, doctorPatientRepo)

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

    // ── listDoctorInvitations ─────────────────────────────────────────────────

    private val doctorProfile = IdentityUserProfile(
        id = doctorId.toString(),
        firstName = "John",
        lastName = "Smith",
    )
    private val patientProfile = IdentityUserProfile(
        id = patientId.toString(),
        firstName = "Jane",
        lastName = "Doe",
    )

    private fun pendingInvitation(): DoctorInvitation {
        val now = Clock.System.now()
        return DoctorInvitation(
            id = Uuid.random(),
            doctorId = doctorId,
            patientIdentifier = patientEmail,
            patientId = patientId,
            status = InvitationStatus.PENDING,
            message = null,
            createdAt = now,
            expiresAt = now + 7.days,
            resolvedAt = null,
        )
    }

    @Test
    fun `listDoctorInvitations returns paginated invitations for own doctorId`() = runTest {
        val invitation = pendingInvitation()
        val statuses = setOf(InvitationStatus.PENDING)
        coEvery { invitationRepo.countByDoctorId(doctorId, statuses) } returns 1L
        coEvery { invitationRepo.findByDoctorId(doctorId, statuses, 20, 0L) } returns listOf(invitation)
        coEvery { identityProvider.getUserProfile(doctorId) } returns doctorProfile
        coEvery { identityProvider.getUserProfile(patientId) } returns patientProfile

        val result = service.listDoctorInvitations(doctorPrincipal(), doctorId, statuses, 0, 20)

        assertEquals(1, result.invitations.size)
        assertEquals(invitation.id, result.invitations.first().id)
        assertEquals("John Smith", result.doctorDisplayName)
        assertEquals("Jane Doe", result.patientDisplayNames[patientId.toString()])
        assertEquals(0, result.page)
        assertEquals(20, result.size)
        assertEquals(1L, result.totalElements)
        assertEquals(1, result.totalPages)
    }

    @Test
    fun `listDoctorInvitations returns empty content when no invitations exist`() = runTest {
        val statuses = setOf(InvitationStatus.PENDING)
        coEvery { invitationRepo.countByDoctorId(doctorId, statuses) } returns 0L
        coEvery { invitationRepo.findByDoctorId(doctorId, statuses, 20, 0L) } returns emptyList()
        coEvery { identityProvider.getUserProfile(doctorId) } returns doctorProfile

        val result = service.listDoctorInvitations(doctorPrincipal(), doctorId, statuses, 0, 20)

        assertTrue(result.invitations.isEmpty())
        assertEquals(0L, result.totalElements)
        assertEquals(0, result.totalPages)
    }

    @Test
    fun `listDoctorInvitations admin can list any doctor invitations`() = runTest {
        val invitation = pendingInvitation()
        val statuses = setOf(InvitationStatus.PENDING)
        coEvery { invitationRepo.countByDoctorId(doctorId, statuses) } returns 1L
        coEvery { invitationRepo.findByDoctorId(doctorId, statuses, 20, 0L) } returns listOf(invitation)
        coEvery { identityProvider.getUserProfile(doctorId) } returns doctorProfile
        coEvery { identityProvider.getUserProfile(patientId) } returns patientProfile

        val result = service.listDoctorInvitations(adminPrincipal(), doctorId, statuses, 0, 20)

        assertEquals(1, result.invitations.size)
    }

    @Test
    fun `listDoctorInvitations throws AuthorizationException when doctor requests different doctorId`() = runTest {
        val otherDoctorId = Uuid.parse("cccccccc-cccc-cccc-cccc-cccccccccccc")

        assertFailsWith<AuthorizationException> {
            service.listDoctorInvitations(doctorPrincipal(), otherDoctorId, setOf(InvitationStatus.PENDING), 0, 20)
        }
        coVerify(exactly = 0) { invitationRepo.countByDoctorId(any(), any()) }
    }

    @Test
    fun `listDoctorInvitations throws AuthorizationException for patient principal`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.listDoctorInvitations(patientPrincipal(), doctorId, setOf(InvitationStatus.PENDING), 0, 20)
        }
        coVerify(exactly = 0) { invitationRepo.countByDoctorId(any(), any()) }
    }

    @Test
    fun `listDoctorInvitations returns null display names when identity provider lookup fails`() = runTest {
        val invitation = pendingInvitation()
        val statuses = setOf(InvitationStatus.PENDING)
        coEvery { invitationRepo.countByDoctorId(doctorId, statuses) } returns 1L
        coEvery { invitationRepo.findByDoctorId(doctorId, statuses, 20, 0L) } returns listOf(invitation)
        coEvery { identityProvider.getUserProfile(any()) } throws RuntimeException("Keycloak unavailable")

        val result = service.listDoctorInvitations(doctorPrincipal(), doctorId, statuses, 0, 20)

        assertNull(result.doctorDisplayName)
        assertTrue(result.patientDisplayNames.isEmpty())
    }

    @Test
    fun `listDoctorInvitations clamps page size to max 100`() = runTest {
        val statuses = setOf(InvitationStatus.PENDING)
        coEvery { invitationRepo.countByDoctorId(doctorId, statuses) } returns 0L
        coEvery { invitationRepo.findByDoctorId(doctorId, statuses, 100, 0L) } returns emptyList()
        coEvery { identityProvider.getUserProfile(doctorId) } returns doctorProfile

        val result = service.listDoctorInvitations(doctorPrincipal(), doctorId, statuses, 0, 999)

        assertEquals(100, result.size)
        coVerify(exactly = 1) { invitationRepo.findByDoctorId(doctorId, statuses, 100, 0L) }
    }

    @Test
    fun `listDoctorInvitations filters by multiple statuses`() = runTest {
        val invitation = pendingInvitation()
        val statuses = setOf(InvitationStatus.PENDING, InvitationStatus.EXPIRED)
        coEvery { invitationRepo.countByDoctorId(doctorId, statuses) } returns 1L
        coEvery { invitationRepo.findByDoctorId(doctorId, statuses, 20, 0L) } returns listOf(invitation)
        coEvery { identityProvider.getUserProfile(doctorId) } returns doctorProfile
        coEvery { identityProvider.getUserProfile(patientId) } returns patientProfile

        val result = service.listDoctorInvitations(doctorPrincipal(), doctorId, statuses, 0, 20)

        assertEquals(1, result.invitations.size)
        coVerify(exactly = 1) { invitationRepo.findByDoctorId(doctorId, statuses, 20, 0L) }
    }

    // ---- listIncomingInvitations ----

    @Test
    fun `listIncomingInvitations returns pending invitations for own patientId`() = runTest {
        val invitation = pendingInvitation()
        coEvery { invitationRepo.findPendingByPatientId(patientId) } returns listOf(invitation)

        val result = service.listIncomingInvitations(patientPrincipal(), patientId)

        assertEquals(1, result.size)
        assertEquals(invitation.id, result[0].id)
        coVerify(exactly = 1) { invitationRepo.findPendingByPatientId(patientId) }
    }

    @Test
    fun `listIncomingInvitations returns empty list when no pending invitations exist`() = runTest {
        coEvery { invitationRepo.findPendingByPatientId(patientId) } returns emptyList()

        val result = service.listIncomingInvitations(patientPrincipal(), patientId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listIncomingInvitations admin can list any patient inbox`() = runTest {
        val invitation = pendingInvitation()
        coEvery { invitationRepo.findPendingByPatientId(patientId) } returns listOf(invitation)

        val result = service.listIncomingInvitations(adminPrincipal(), patientId)

        assertEquals(1, result.size)
    }

    @Test
    fun `listIncomingInvitations throws AuthorizationException when patient requests different patientId`() = runTest {
        val otherPatientId = Uuid.parse("ffffffff-ffff-ffff-ffff-ffffffffffff")

        assertFailsWith<AuthorizationException> {
            service.listIncomingInvitations(patientPrincipal(), otherPatientId)
        }
        coVerify(exactly = 0) { invitationRepo.findPendingByPatientId(any()) }
    }

    @Test
    fun `listIncomingInvitations throws AuthorizationException for doctor principal`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.listIncomingInvitations(doctorPrincipal(), patientId)
        }
        coVerify(exactly = 0) { invitationRepo.findPendingByPatientId(any()) }
    }

    // ── respondToInvitation ───────────────────────────────────────────────────

    private val invitationId = Uuid.parse("11111111-2222-3333-4444-555555555555")

    private fun pendingInvitationWithFixedId() = DoctorInvitation(
        id = invitationId,
        doctorId = doctorId,
        patientIdentifier = patientEmail,
        patientId = patientId,
        status = InvitationStatus.PENDING,
        message = null,
        createdAt = Clock.System.now(),
        expiresAt = Clock.System.now(),
        resolvedAt = null,
    )

    @Test
    fun `respondToInvitation ACCEPT happy path creates doctor-patient link and returns ACCEPTED`() = runTest {
        val inv = pendingInvitationWithFixedId()
        coEvery { invitationRepo.findById(invitationId) } returns inv
        coEvery { invitationRepo.updateStatus(invitationId, InvitationStatus.ACCEPTED, any()) } returns true
        coEvery { doctorPatientRepo.save(any()) } answers { firstArg() }
        coEvery { doctorPatientRepo.findAllPatientIdsByDoctorId(doctorId) } returns listOf(patientId)
        coEvery { identityProvider.updateUserAttributes(doctorId, any()) } returns Unit

        val result = service.respondToInvitation(patientPrincipal(), patientId, invitationId, InvitationAction.ACCEPT)

        assertEquals(InvitationStatus.ACCEPTED, result.status)
        assertNotNull(result.resolvedAt)
        coVerify(exactly = 1) { doctorPatientRepo.save(any()) }
        coVerify(exactly = 1) { identityProvider.updateUserAttributes(doctorId, any()) }
    }

    @Test
    fun `respondToInvitation ACCEPT admin can accept on behalf of patient`() = runTest {
        val inv = pendingInvitationWithFixedId()
        coEvery { invitationRepo.findById(invitationId) } returns inv
        coEvery { invitationRepo.updateStatus(invitationId, InvitationStatus.ACCEPTED, any()) } returns true
        coEvery { doctorPatientRepo.save(any()) } answers { firstArg() }
        coEvery { doctorPatientRepo.findAllPatientIdsByDoctorId(doctorId) } returns listOf(patientId)
        coEvery { identityProvider.updateUserAttributes(doctorId, any()) } returns Unit

        val result = service.respondToInvitation(adminPrincipal(), patientId, invitationId, InvitationAction.ACCEPT)

        assertEquals(InvitationStatus.ACCEPTED, result.status)
    }

    @Test
    fun `respondToInvitation DECLINE happy path returns DECLINED without creating relation`() = runTest {
        val inv = pendingInvitationWithFixedId()
        coEvery { invitationRepo.findById(invitationId) } returns inv
        coEvery { invitationRepo.updateStatus(invitationId, InvitationStatus.DECLINED, any()) } returns true

        val result = service.respondToInvitation(patientPrincipal(), patientId, invitationId, InvitationAction.DECLINE)

        assertEquals(InvitationStatus.DECLINED, result.status)
        assertNotNull(result.resolvedAt)
        coVerify(exactly = 0) { doctorPatientRepo.save(any()) }
        coVerify(exactly = 0) { identityProvider.updateUserAttributes(any(), any()) }
    }

    @Test
    fun `respondToInvitation throws ResourceNotFoundException when invitation does not exist`() = runTest {
        coEvery { invitationRepo.findById(invitationId) } returns null

        assertFailsWith<ResourceNotFoundException> {
            service.respondToInvitation(patientPrincipal(), patientId, invitationId, InvitationAction.ACCEPT)
        }
    }

    @Test
    fun `respondToInvitation throws ResourceNotFoundException when invitation belongs to different patient`() = runTest {
        val otherPatientId = Uuid.parse("ffffffff-ffff-ffff-ffff-ffffffffffff")
        val inv = pendingInvitationWithFixedId().copy(patientId = otherPatientId)
        coEvery { invitationRepo.findById(invitationId) } returns inv

        assertFailsWith<ResourceNotFoundException> {
            service.respondToInvitation(adminPrincipal(), patientId, invitationId, InvitationAction.ACCEPT)
        }
    }

    @Test
    fun `respondToInvitation throws ConflictException when invitation is already ACCEPTED`() = runTest {
        val inv = pendingInvitationWithFixedId().copy(status = InvitationStatus.ACCEPTED)
        coEvery { invitationRepo.findById(invitationId) } returns inv

        assertFailsWith<ConflictException> {
            service.respondToInvitation(patientPrincipal(), patientId, invitationId, InvitationAction.DECLINE)
        }
    }

    @Test
    fun `respondToInvitation throws ConflictException when invitation is CANCELLED`() = runTest {
        val inv = pendingInvitationWithFixedId().copy(status = InvitationStatus.CANCELLED)
        coEvery { invitationRepo.findById(invitationId) } returns inv

        assertFailsWith<ConflictException> {
            service.respondToInvitation(patientPrincipal(), patientId, invitationId, InvitationAction.ACCEPT)
        }
    }

    @Test
    fun `respondToInvitation throws ConflictException when invitation is EXPIRED`() = runTest {
        val inv = pendingInvitationWithFixedId().copy(status = InvitationStatus.EXPIRED)
        coEvery { invitationRepo.findById(invitationId) } returns inv

        assertFailsWith<ConflictException> {
            service.respondToInvitation(patientPrincipal(), patientId, invitationId, InvitationAction.ACCEPT)
        }
    }

    @Test
    fun `respondToInvitation throws AuthorizationException when principal is different patient`() = runTest {
        val otherPatientId = Uuid.parse("ffffffff-ffff-ffff-ffff-ffffffffffff")
        val otherPrincipal = UserPrincipal(otherPatientId, setOf(Role.PATIENT), emptySet())

        assertFailsWith<AuthorizationException> {
            service.respondToInvitation(otherPrincipal, patientId, invitationId, InvitationAction.ACCEPT)
        }
        coVerify(exactly = 0) { invitationRepo.findById(any()) }
    }

    @Test
    fun `respondToInvitation rolls back invitation to PENDING when KC sync fails on ACCEPT`() = runTest {
        val inv = pendingInvitationWithFixedId()
        coEvery { invitationRepo.findById(invitationId) } returns inv
        coEvery { invitationRepo.updateStatus(invitationId, InvitationStatus.ACCEPTED, any()) } returns true
        coEvery { invitationRepo.updateStatus(invitationId, InvitationStatus.PENDING, null) } returns true
        coEvery { doctorPatientRepo.save(any()) } answers { firstArg() }
        coEvery { doctorPatientRepo.findAllPatientIdsByDoctorId(doctorId) } returns listOf(patientId)
        coEvery { doctorPatientRepo.delete(doctorId, patientId) } returns true
        coEvery { identityProvider.updateUserAttributes(doctorId, any()) } throws RuntimeException("KC unavailable")

        assertFailsWith<RuntimeException> {
            service.respondToInvitation(patientPrincipal(), patientId, invitationId, InvitationAction.ACCEPT)
        }

        coVerify(exactly = 1) { doctorPatientRepo.delete(doctorId, patientId) }
        coVerify(exactly = 1) { invitationRepo.updateStatus(invitationId, InvitationStatus.PENDING, null) }
    }

    // ---- expireOldInvitations ----

    @Test
    fun `expireOldInvitations delegates to repo and returns count`() = runTest {
        val cutoff = Clock.System.now()
        coEvery { invitationRepo.expireBefore(cutoff) } returns 5

        val result = service.expireOldInvitations(cutoff)

        assertEquals(5, result)
        coVerify(exactly = 1) { invitationRepo.expireBefore(cutoff) }
    }

    @Test
    fun `expireOldInvitations returns zero when no invitations expired`() = runTest {
        val cutoff = Clock.System.now()
        coEvery { invitationRepo.expireBefore(cutoff) } returns 0

        val result = service.expireOldInvitations(cutoff)

        assertEquals(0, result)
    }

    @Test
    fun `expireOldInvitations uses Clock dot System dot now as default cutoff`() = runTest {
        coEvery { invitationRepo.expireBefore(any()) } returns 0

        service.expireOldInvitations()

        coVerify(exactly = 1) { invitationRepo.expireBefore(any()) }
    }

    // ---- cancelInvitation ----

    private val cancelInvitationId = Uuid.parse("11111111-1111-1111-1111-111111111111")

    private fun doctorOwnedInvitation(status: InvitationStatus = InvitationStatus.PENDING): DoctorInvitation {
        val now = Clock.System.now()
        return DoctorInvitation(
            id = cancelInvitationId,
            doctorId = doctorId,
            patientIdentifier = patientEmail,
            patientId = patientId,
            status = status,
            message = null,
            createdAt = now,
            expiresAt = now + 7.days,
            resolvedAt = null,
        )
    }

    @Test
    fun `cancelInvitation happy path updates status to CANCELLED`() = runTest {
        coEvery { invitationRepo.findById(cancelInvitationId) } returns doctorOwnedInvitation()
        coEvery { invitationRepo.updateStatus(cancelInvitationId, InvitationStatus.CANCELLED, any()) } returns true

        service.cancelInvitation(doctorPrincipal(), doctorId, cancelInvitationId)

        coVerify(exactly = 1) {
            invitationRepo.updateStatus(cancelInvitationId, InvitationStatus.CANCELLED, any())
        }
    }

    @Test
    fun `cancelInvitation admin can cancel any doctor invitation`() = runTest {
        coEvery { invitationRepo.findById(cancelInvitationId) } returns doctorOwnedInvitation()
        coEvery { invitationRepo.updateStatus(cancelInvitationId, InvitationStatus.CANCELLED, any()) } returns true

        service.cancelInvitation(adminPrincipal(), doctorId, cancelInvitationId)

        coVerify(exactly = 1) {
            invitationRepo.updateStatus(cancelInvitationId, InvitationStatus.CANCELLED, any())
        }
    }

    @Test
    fun `cancelInvitation throws ResourceNotFoundException when invitation does not exist`() = runTest {
        coEvery { invitationRepo.findById(cancelInvitationId) } returns null

        assertFailsWith<ResourceNotFoundException> {
            service.cancelInvitation(doctorPrincipal(), doctorId, cancelInvitationId)
        }
        coVerify(exactly = 0) { invitationRepo.updateStatus(any(), any(), any()) }
    }

    @Test
    fun `cancelInvitation throws ResourceNotFoundException when invitation belongs to different doctor`() = runTest {
        val otherDoctorId = Uuid.parse("cccccccc-cccc-cccc-cccc-cccccccccccc")
        val otherDoctorPrincipal = UserPrincipal(otherDoctorId, setOf(Role.DOCTOR), emptySet())
        coEvery { invitationRepo.findById(cancelInvitationId) } returns doctorOwnedInvitation()

        assertFailsWith<ResourceNotFoundException> {
            service.cancelInvitation(otherDoctorPrincipal, otherDoctorId, cancelInvitationId)
        }
        coVerify(exactly = 0) { invitationRepo.updateStatus(any(), any(), any()) }
    }

    @Test
    fun `cancelInvitation throws ConflictException when invitation is ACCEPTED`() = runTest {
        coEvery { invitationRepo.findById(cancelInvitationId) } returns doctorOwnedInvitation(InvitationStatus.ACCEPTED)

        assertFailsWith<ConflictException> {
            service.cancelInvitation(doctorPrincipal(), doctorId, cancelInvitationId)
        }
        coVerify(exactly = 0) { invitationRepo.updateStatus(any(), any(), any()) }
    }

    @Test
    fun `cancelInvitation throws ConflictException when invitation is already CANCELLED`() = runTest {
        coEvery {
            invitationRepo.findById(cancelInvitationId)
        } returns doctorOwnedInvitation(InvitationStatus.CANCELLED)

        assertFailsWith<ConflictException> {
            service.cancelInvitation(doctorPrincipal(), doctorId, cancelInvitationId)
        }
        coVerify(exactly = 0) { invitationRepo.updateStatus(any(), any(), any()) }
    }

    @Test
    fun `cancelInvitation throws ConflictException when invitation is EXPIRED`() = runTest {
        coEvery { invitationRepo.findById(cancelInvitationId) } returns doctorOwnedInvitation(InvitationStatus.EXPIRED)

        assertFailsWith<ConflictException> {
            service.cancelInvitation(doctorPrincipal(), doctorId, cancelInvitationId)
        }
        coVerify(exactly = 0) { invitationRepo.updateStatus(any(), any(), any()) }
    }

    @Test
    fun `cancelInvitation throws AuthorizationException when doctor acts on different doctorId`() = runTest {
        val otherDoctorId = Uuid.parse("cccccccc-cccc-cccc-cccc-cccccccccccc")

        assertFailsWith<AuthorizationException> {
            service.cancelInvitation(doctorPrincipal(), otherDoctorId, cancelInvitationId)
        }
        coVerify(exactly = 0) { invitationRepo.findById(any()) }
    }

    @Test
    fun `cancelInvitation throws AuthorizationException for patient principal`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.cancelInvitation(patientPrincipal(), doctorId, cancelInvitationId)
        }
        coVerify(exactly = 0) { invitationRepo.findById(any()) }
    }

    // ---- listAllInvitations ----

    @Test
    fun `listAllInvitations returns paginated result for admin`() = runTest {
        val invitation = doctorOwnedInvitation()
        coEvery { invitationRepo.findAll(null, any(), any()) } returns listOf(invitation)
        coEvery { invitationRepo.countAll(null) } returns 1L
        coEvery { identityProvider.getUserProfile(patientId) } throws
            ResourceNotFoundException("not found")

        val result = service.listAllInvitations(adminPrincipal(), null, 0, 20)

        assertEquals(1, result.invitations.size)
        assertEquals(1L, result.totalElements)
        assertEquals(1, result.totalPages)
        assertEquals(0, result.page)
        assertNull(result.doctorDisplayName)
    }

    @Test
    fun `listAllInvitations filters by status when provided`() = runTest {
        val invitation = doctorOwnedInvitation()
        coEvery { invitationRepo.findAll(InvitationStatus.PENDING, any(), any()) } returns listOf(invitation)
        coEvery { invitationRepo.countAll(InvitationStatus.PENDING) } returns 1L
        coEvery { identityProvider.getUserProfile(patientId) } throws
            ResourceNotFoundException("not found")

        val result = service.listAllInvitations(adminPrincipal(), InvitationStatus.PENDING, 0, 20)

        assertEquals(InvitationStatus.PENDING, result.invitations.first().status)
        coVerify(exactly = 1) { invitationRepo.findAll(InvitationStatus.PENDING, any(), any()) }
    }

    @Test
    fun `listAllInvitations clamps page size to MAX_PAGE_SIZE`() = runTest {
        coEvery { invitationRepo.findAll(null, 100, 0L) } returns emptyList()
        coEvery { invitationRepo.countAll(null) } returns 0L

        val result = service.listAllInvitations(adminPrincipal(), null, 0, 999)

        assertEquals(100, result.size)
        coVerify(exactly = 1) { invitationRepo.findAll(null, 100, 0L) }
    }

    @Test
    fun `listAllInvitations returns totalPages 0 when no invitations`() = runTest {
        coEvery { invitationRepo.findAll(null, any(), any()) } returns emptyList()
        coEvery { invitationRepo.countAll(null) } returns 0L

        val result = service.listAllInvitations(adminPrincipal(), null, 0, 20)

        assertEquals(0, result.totalPages)
    }

    @Test
    fun `listAllInvitations throws AuthorizationException for doctor principal`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.listAllInvitations(doctorPrincipal(), null, 0, 20)
        }
        coVerify(exactly = 0) { invitationRepo.findAll(any(), any(), any()) }
    }

    @Test
    fun `listAllInvitations throws AuthorizationException for patient principal`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.listAllInvitations(patientPrincipal(), null, 0, 20)
        }
        coVerify(exactly = 0) { invitationRepo.findAll(any(), any(), any()) }
    }

    // ---- adminCancelInvitation ----

    @Test
    fun `adminCancelInvitation cancels any pending invitation`() = runTest {
        coEvery { invitationRepo.findById(cancelInvitationId) } returns doctorOwnedInvitation()
        coEvery { invitationRepo.updateStatus(cancelInvitationId, InvitationStatus.CANCELLED, any()) } returns true

        service.adminCancelInvitation(adminPrincipal(), cancelInvitationId)

        coVerify(exactly = 1) {
            invitationRepo.updateStatus(cancelInvitationId, InvitationStatus.CANCELLED, any())
        }
    }

    @Test
    fun `adminCancelInvitation can cancel any doctor's invitation`() = runTest {
        val otherDoctorInvitation = doctorOwnedInvitation()
        coEvery { invitationRepo.findById(cancelInvitationId) } returns otherDoctorInvitation
        coEvery { invitationRepo.updateStatus(cancelInvitationId, InvitationStatus.CANCELLED, any()) } returns true

        service.adminCancelInvitation(adminPrincipal(), cancelInvitationId)

        coVerify(exactly = 1) {
            invitationRepo.updateStatus(cancelInvitationId, InvitationStatus.CANCELLED, any())
        }
    }

    @Test
    fun `adminCancelInvitation throws ResourceNotFoundException when invitation not found`() = runTest {
        coEvery { invitationRepo.findById(cancelInvitationId) } returns null

        assertFailsWith<ResourceNotFoundException> {
            service.adminCancelInvitation(adminPrincipal(), cancelInvitationId)
        }
        coVerify(exactly = 0) { invitationRepo.updateStatus(any(), any(), any()) }
    }

    @Test
    fun `adminCancelInvitation throws ConflictException when invitation is ACCEPTED`() = runTest {
        coEvery {
            invitationRepo.findById(cancelInvitationId)
        } returns doctorOwnedInvitation(InvitationStatus.ACCEPTED)

        assertFailsWith<ConflictException> {
            service.adminCancelInvitation(adminPrincipal(), cancelInvitationId)
        }
        coVerify(exactly = 0) { invitationRepo.updateStatus(any(), any(), any()) }
    }

    @Test
    fun `adminCancelInvitation throws ConflictException when invitation is already CANCELLED`() = runTest {
        coEvery {
            invitationRepo.findById(cancelInvitationId)
        } returns doctorOwnedInvitation(InvitationStatus.CANCELLED)

        assertFailsWith<ConflictException> {
            service.adminCancelInvitation(adminPrincipal(), cancelInvitationId)
        }
        coVerify(exactly = 0) { invitationRepo.updateStatus(any(), any(), any()) }
    }

    @Test
    fun `adminCancelInvitation throws ConflictException when invitation is EXPIRED`() = runTest {
        coEvery {
            invitationRepo.findById(cancelInvitationId)
        } returns doctorOwnedInvitation(InvitationStatus.EXPIRED)

        assertFailsWith<ConflictException> {
            service.adminCancelInvitation(adminPrincipal(), cancelInvitationId)
        }
        coVerify(exactly = 0) { invitationRepo.updateStatus(any(), any(), any()) }
    }

    @Test
    fun `adminCancelInvitation throws AuthorizationException for doctor principal`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.adminCancelInvitation(doctorPrincipal(), cancelInvitationId)
        }
        coVerify(exactly = 0) { invitationRepo.findById(any()) }
    }

    @Test
    fun `adminCancelInvitation throws AuthorizationException for patient principal`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.adminCancelInvitation(patientPrincipal(), cancelInvitationId)
        }
        coVerify(exactly = 0) { invitationRepo.findById(any()) }
    }
}

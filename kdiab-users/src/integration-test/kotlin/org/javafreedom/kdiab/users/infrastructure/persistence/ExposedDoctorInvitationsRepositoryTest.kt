@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.infrastructure.persistence

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import org.javafreedom.kdiab.users.domain.model.DoctorInvitation
import org.javafreedom.kdiab.users.domain.model.InvitationStatus
import org.jetbrains.exposed.v1.jdbc.Database

class ExposedDoctorInvitationsRepositoryTest {

    companion object {
        val db: Database = LiquibaseTestHelper.setup("users_doctor_invitations_test")
    }

    private val repository = ExposedDoctorInvitationsRepository()

    private val doctorId = Uuid.parse("dddddddd-dddd-dddd-dddd-dddddddddddd")
    private val patientId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val anotherPatientId = Uuid.parse("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    private val createdAt = Instant.parse("2026-01-01T10:00:00Z")
    private val expiresAt = Instant.parse("2026-01-08T10:00:00Z")

    @BeforeTest
    fun setUp() = LiquibaseTestHelper.cleanData(db)

    private fun invitation(
        id: Uuid = Uuid.random(),
        doctor: Uuid = doctorId,
        identifier: String = "patient@example.com",
        patient: Uuid? = patientId,
        status: InvitationStatus = InvitationStatus.PENDING,
    ) = DoctorInvitation(
        id = id,
        doctorId = doctor,
        patientIdentifier = identifier,
        patientId = patient,
        status = status,
        message = null,
        createdAt = createdAt,
        expiresAt = expiresAt,
        resolvedAt = null,
    )

    @Test
    fun `save and findById returns the saved invitation`() = runBlocking {
        val inv = invitation()
        repository.save(inv)
        val found = repository.findById(inv.id)
        assertNotNull(found)
        assertEquals(inv.id, found.id)
        assertEquals(inv.doctorId, found.doctorId)
        assertEquals(inv.patientIdentifier, found.patientIdentifier)
        assertEquals(inv.patientId, found.patientId)
        assertEquals(InvitationStatus.PENDING, found.status)
        assertNull(found.resolvedAt)
    }

    @Test
    fun `findById returns null when not found`() = runBlocking {
        val found = repository.findById(Uuid.random())
        assertNull(found)
    }

    @Test
    fun `save preserves optional message field`() = runBlocking {
        val inv = invitation().copy(message = "Please join my patient list")
        repository.save(inv)
        val found = repository.findById(inv.id)
        assertNotNull(found)
        assertEquals("Please join my patient list", found.message)
    }

    @Test
    fun `save with null patientId stores nullable patient`() = runBlocking {
        val inv = invitation(patient = null)
        repository.save(inv)
        val found = repository.findById(inv.id)
        assertNotNull(found)
        assertNull(found.patientId)
    }

    @Test
    fun `findByDoctorId returns all invitations for a doctor`() = runBlocking {
        repository.save(invitation(id = Uuid.random(), identifier = "first@example.com"))
        repository.save(invitation(id = Uuid.random(), identifier = "second@example.com"))
        val results = repository.findByDoctorId(doctorId)
        assertEquals(2, results.size)
    }

    @Test
    fun `findByDoctorId filters by status`() = runBlocking {
        repository.save(invitation(id = Uuid.random(), status = InvitationStatus.PENDING))
        repository.save(invitation(id = Uuid.random(), patient = anotherPatientId, status = InvitationStatus.ACCEPTED))
        val pending = repository.findByDoctorId(doctorId, statuses = setOf(InvitationStatus.PENDING))
        assertEquals(1, pending.size)
        assertEquals(InvitationStatus.PENDING, pending[0].status)
    }

    @Test
    fun `findByDoctorId returns empty list when no invitations`() = runBlocking {
        val results = repository.findByDoctorId(Uuid.random())
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findByDoctorId respects pagination`() = runBlocking {
        repository.save(
            invitation(id = Uuid.random(), identifier = "first@example.com")
                .copy(createdAt = Instant.parse("2026-01-01T09:00:00Z"))
        )
        repository.save(
            invitation(id = Uuid.random(), patient = anotherPatientId, identifier = "second@example.com")
                .copy(createdAt = Instant.parse("2026-01-01T10:00:00Z"))
        )
        val page0 = repository.findByDoctorId(doctorId, limit = 1, offset = 0)
        val page1 = repository.findByDoctorId(doctorId, limit = 1, offset = 1)
        assertEquals(1, page0.size)
        assertEquals(1, page1.size)
        assertTrue(page0[0].patientIdentifier != page1[0].patientIdentifier)
    }

    @Test
    fun `findPendingByPatientId returns pending invitations for a patient`() = runBlocking {
        repository.save(invitation())
        val results = repository.findPendingByPatientId(patientId)
        assertEquals(1, results.size)
        assertEquals(InvitationStatus.PENDING, results[0].status)
    }

    @Test
    fun `findPendingByPatientId excludes non-pending invitations`() = runBlocking {
        val inv = invitation()
        repository.save(inv)
        repository.updateStatus(inv.id, InvitationStatus.ACCEPTED, Instant.parse("2026-01-02T10:00:00Z"))
        val results = repository.findPendingByPatientId(patientId)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `updateStatus returns true and changes status`() = runBlocking {
        val inv = invitation()
        repository.save(inv)
        val resolvedAt = Instant.parse("2026-01-02T10:00:00Z")
        val updated = repository.updateStatus(inv.id, InvitationStatus.ACCEPTED, resolvedAt)
        assertTrue(updated)
        val found = repository.findById(inv.id)
        assertNotNull(found)
        assertEquals(InvitationStatus.ACCEPTED, found.status)
        assertEquals(resolvedAt, found.resolvedAt)
    }

    @Test
    fun `updateStatus returns false when invitation not found`() = runBlocking {
        val updated = repository.updateStatus(Uuid.random(), InvitationStatus.CANCELLED, null)
        assertFalse(updated)
    }

    @Test
    fun `expireBefore updates all expired pending invitations`() = runBlocking {
        val pastExpiry = Instant.parse("2026-01-01T08:00:00Z")
        val futureExpiry = Instant.parse("2026-12-31T00:00:00Z")
        val expiredInv = invitation(id = Uuid.random())
            .copy(expiresAt = pastExpiry)
        val activeInv = invitation(id = Uuid.random(), patient = anotherPatientId)
            .copy(expiresAt = futureExpiry)
        repository.save(expiredInv)
        repository.save(activeInv)

        val count = repository.expireBefore(Instant.parse("2026-01-01T09:00:00Z"))
        assertEquals(1, count)

        val found = repository.findById(expiredInv.id)
        assertNotNull(found)
        assertEquals(InvitationStatus.EXPIRED, found.status)

        val active = repository.findById(activeInv.id)
        assertNotNull(active)
        assertEquals(InvitationStatus.PENDING, active.status)
    }

    @Test
    fun `expireBefore does not expire already non-pending invitations`() = runBlocking {
        val pastExpiry = Instant.parse("2026-01-01T08:00:00Z")
        val inv = invitation()
            .copy(status = InvitationStatus.ACCEPTED, expiresAt = pastExpiry)
        repository.save(inv)

        val count = repository.expireBefore(Instant.parse("2026-01-01T09:00:00Z"))
        assertEquals(0, count)
    }

    @Test
    fun `existsPendingByDoctorAndPatient returns true when pending exists`() = runBlocking {
        repository.save(invitation())
        val exists = repository.existsPendingByDoctorAndPatient(doctorId, patientId)
        assertTrue(exists)
    }

    @Test
    fun `existsPendingByDoctorAndPatient returns false when no pending`() = runBlocking {
        val exists = repository.existsPendingByDoctorAndPatient(doctorId, patientId)
        assertFalse(exists)
    }

    @Test
    fun `existsPendingByDoctorAndPatient returns false after status update`() = runBlocking {
        val inv = invitation()
        repository.save(inv)
        repository.updateStatus(inv.id, InvitationStatus.ACCEPTED, Instant.parse("2026-01-02T10:00:00Z"))
        val exists = repository.existsPendingByDoctorAndPatient(doctorId, patientId)
        assertFalse(exists)
    }

    @Test
    fun `existsPendingByDoctorAndPatient detects duplicate pending before inserting`() = runBlocking {
        // The application layer calls existsPendingByDoctorAndPatient before inserting to
        // prevent duplicates. The DB-level unique partial index enforces this on PostgreSQL;
        // H2 does not support partial indexes so this guard is tested at the service layer.
        repository.save(invitation(id = Uuid.random()))
        val hasDuplicate = repository.existsPendingByDoctorAndPatient(doctorId, patientId)
        assertTrue(hasDuplicate, "Expected to detect existing PENDING invitation for same doctor/patient")
    }
}

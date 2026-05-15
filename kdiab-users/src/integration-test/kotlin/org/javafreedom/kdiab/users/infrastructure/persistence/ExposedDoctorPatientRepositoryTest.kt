@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.infrastructure.persistence

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.users.domain.model.DoctorPatientRelation
import org.jetbrains.exposed.v1.jdbc.Database

class ExposedDoctorPatientRepositoryTest {

    companion object {
        val db: Database = LiquibaseTestHelper.setup("users_doctor_patient_test")
    }

    private val repository = ExposedDoctorPatientRepository()
    private val doctorId = Uuid.parse("dddddddd-dddd-dddd-dddd-dddddddddddd")
    private val patientA = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val patientB = Uuid.parse("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    @BeforeTest
    fun setUp() = LiquibaseTestHelper.cleanData(db)

    private fun relation(doctor: Uuid = doctorId, patient: Uuid = patientA) = DoctorPatientRelation(
        doctorId = doctor,
        patientId = patient,
        createdAt = Instant.parse("2024-06-01T00:00:00Z"),
    )

    @Test
    fun `save and findByDoctorId returns relation`() = runBlocking {
        repository.save(relation())
        val found = repository.findByDoctorId(doctorId)
        assertEquals(1, found.size)
        assertEquals(patientA, found[0].patientId)
    }

    @Test
    fun `findByDoctorId returns empty list when no relations`() = runBlocking {
        assertTrue(repository.findByDoctorId(Uuid.random()).isEmpty())
    }

    @Test
    fun `save throws ConflictException on duplicate`() = runBlocking {
        repository.save(relation())
        assertFailsWith<ConflictException> {
            repository.save(relation())
        }
    }

    @Test
    fun `delete returns true when relation exists`() = runBlocking {
        repository.save(relation())
        assertTrue(repository.delete(doctorId, patientA))
    }

    @Test
    fun `delete returns false when relation does not exist`() = runBlocking {
        assertFalse(repository.delete(doctorId, patientA))
    }

    @Test
    fun `findByDoctorId respects pagination`() = runBlocking {
        repository.save(relation(patient = patientA).copy(createdAt = Instant.parse("2024-06-01T00:00:00Z")))
        repository.save(relation(patient = patientB).copy(createdAt = Instant.parse("2024-06-02T00:00:00Z")))
        val page0 = repository.findByDoctorId(doctorId, limit = 1, offset = 0)
        val page1 = repository.findByDoctorId(doctorId, limit = 1, offset = 1)
        assertEquals(1, page0.size)
        assertEquals(1, page1.size)
        assertTrue(page0[0].patientId != page1[0].patientId)
    }

    @Test
    fun `findByDoctorId is ordered by createdAt ascending`() = runBlocking {
        repository.save(relation(patient = patientA).copy(createdAt = Instant.parse("2024-06-02T00:00:00Z")))
        repository.save(relation(patient = patientB).copy(createdAt = Instant.parse("2024-06-01T00:00:00Z")))
        val results = repository.findByDoctorId(doctorId)
        assertEquals(patientB, results[0].patientId)
        assertEquals(patientA, results[1].patientId)
    }

    @Test
    fun `deleteByUserId removes all relations for doctor`() = runBlocking {
        repository.save(relation(patient = patientA))
        repository.save(relation(patient = patientB))
        repository.deleteByUserId(doctorId)
        assertTrue(repository.findByDoctorId(doctorId).isEmpty())
    }

    @Test
    fun `findAllPatientIdsByDoctorId returns patient UUIDs`() = runBlocking {
        repository.save(relation(patient = patientA))
        repository.save(relation(patient = patientB))
        val ids = repository.findAllPatientIdsByDoctorId(doctorId)
        assertEquals(2, ids.size)
        assertTrue(ids.contains(patientA))
        assertTrue(ids.contains(patientB))
    }
}

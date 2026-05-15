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
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.domain.model.DoctorPatientRelation
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakAdminClient
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakRole

class DoctorPatientServiceTest {

    private val repo = mockk<DoctorPatientRepository>()
    private val keycloak = mockk<KeycloakAdminClient>()
    private val service = DoctorPatientService(repo, keycloak)

    private val adminId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val doctorId = Uuid.parse("dddddddd-dddd-dddd-dddd-dddddddddddd")
    private val patientId = Uuid.parse("b0b0b0b0-b0b0-4b00-ab00-b0b0b0b0b0b0")

    private fun adminPrincipal() = UserPrincipal(adminId, setOf(Role.ADMIN), emptySet())
    private fun doctorPrincipal() = UserPrincipal(doctorId, setOf(Role.DOCTOR), emptySet())
    private fun patientPrincipal() = UserPrincipal(patientId, setOf(Role.PATIENT), emptySet())

    @Test
    fun `listPatients allows admin`() = runTest {
        coEvery { repo.findByDoctorId(doctorId, any(), any()) } returns emptyList()
        val result = service.listPatients(adminPrincipal(), doctorId)
        assertEquals(0, result.size)
    }

    @Test
    fun `listPatients allows the doctor themselves`() = runTest {
        coEvery { repo.findByDoctorId(doctorId, any(), any()) } returns emptyList()
        val result = service.listPatients(doctorPrincipal(), doctorId)
        assertEquals(0, result.size)
    }

    @Test
    fun `listPatients denies patient`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.listPatients(patientPrincipal(), doctorId)
        }
    }

    @Test
    fun `assignPatient requires admin`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.assignPatient(doctorPrincipal(), doctorId, patientId)
        }
    }

    @Test
    fun `assignPatient saves relation and syncs Keycloak`() = runTest {
        coEvery { keycloak.getUserRoles(doctorId) } returns listOf(KeycloakRole("d", "DOCTOR"))
        coEvery { keycloak.getUserRoles(patientId) } returns listOf(KeycloakRole("p", "PATIENT"))
        coEvery { repo.save(any()) } answers { firstArg() }
        coEvery { repo.findAllPatientIdsByDoctorId(doctorId) } returns listOf(patientId)
        coEvery { keycloak.updateUserAttributes(any(), any()) } returns Unit

        val relation = service.assignPatient(adminPrincipal(), doctorId, patientId)

        assertEquals(doctorId, relation.doctorId)
        assertEquals(patientId, relation.patientId)
        coVerify(exactly = 1) { keycloak.updateUserAttributes(doctorId, mapOf("allowed_patients" to listOf(patientId.toString()))) }
    }

    @Test
    fun `assignPatient throws BusinessValidationException when doctor lacks DOCTOR role`() = runTest {
        coEvery { keycloak.getUserRoles(doctorId) } returns listOf(KeycloakRole("p", "PATIENT"))
        coEvery { keycloak.getUserRoles(patientId) } returns listOf(KeycloakRole("p", "PATIENT"))
        assertFailsWith<BusinessValidationException> {
            service.assignPatient(adminPrincipal(), doctorId, patientId)
        }
    }

    @Test
    fun `assignPatient throws BusinessValidationException when patient lacks PATIENT role`() = runTest {
        coEvery { keycloak.getUserRoles(doctorId) } returns listOf(KeycloakRole("d", "DOCTOR"))
        coEvery { keycloak.getUserRoles(patientId) } returns listOf(KeycloakRole("d", "DOCTOR"))
        assertFailsWith<BusinessValidationException> {
            service.assignPatient(adminPrincipal(), doctorId, patientId)
        }
    }

    @Test
    fun `assignPatient rolls back DB row when KC sync fails`() = runTest {
        coEvery { keycloak.getUserRoles(doctorId) } returns listOf(KeycloakRole("d", "DOCTOR"))
        coEvery { keycloak.getUserRoles(patientId) } returns listOf(KeycloakRole("p", "PATIENT"))
        coEvery { repo.save(any()) } answers { firstArg() }
        coEvery { repo.findAllPatientIdsByDoctorId(doctorId) } returns listOf(patientId)
        coEvery { keycloak.updateUserAttributes(any(), any()) } throws RuntimeException("KC unavailable")
        coEvery { repo.delete(doctorId, patientId) } returns true

        assertFailsWith<RuntimeException> {
            service.assignPatient(adminPrincipal(), doctorId, patientId)
        }
        coVerify(exactly = 1) { repo.delete(doctorId, patientId) }
    }

    @Test
    fun `removePatient requires admin`() = runTest {
        assertFailsWith<AuthorizationException> {
            service.removePatient(doctorPrincipal(), doctorId, patientId)
        }
    }

    @Test
    fun `removePatient deletes relation and syncs Keycloak`() = runTest {
        coEvery { repo.delete(doctorId, patientId) } returns true
        coEvery { repo.findAllPatientIdsByDoctorId(doctorId) } returns emptyList()
        coEvery { keycloak.updateUserAttributes(any(), any()) } returns Unit

        service.removePatient(adminPrincipal(), doctorId, patientId)

        coVerify(exactly = 1) { repo.delete(doctorId, patientId) }
        coVerify(exactly = 1) { keycloak.updateUserAttributes(doctorId, mapOf("allowed_patients" to emptyList())) }
    }

    @Test
    fun `removePatient throws ResourceNotFoundException when relation does not exist`() = runTest {
        coEvery { repo.delete(doctorId, patientId) } returns false
        assertFailsWith<ResourceNotFoundException> {
            service.removePatient(adminPrincipal(), doctorId, patientId)
        }
    }
}

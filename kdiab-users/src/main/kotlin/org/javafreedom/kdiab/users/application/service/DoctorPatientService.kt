@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.infrastructure.keycloak.toKeycloakName
import org.javafreedom.kdiab.users.domain.model.DoctorPatientRelation
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakAdminClient

private const val MAX_PAGE_SIZE = 100

private val logger = KotlinLogging.logger {}

class DoctorPatientService(
    private val repo: DoctorPatientRepository,
    private val keycloak: KeycloakAdminClient,
) {
    suspend fun listPatients(
        principal: UserPrincipal,
        doctorId: Uuid,
        page: Int = 0,
        size: Int = 20,
    ): List<DoctorPatientRelation> {
        if (!principal.isAdmin() && principal.userId != doctorId) {
            throw AuthorizationException("Access denied")
        }
        return repo.findByDoctorId(doctorId, limit = size.coerceAtMost(MAX_PAGE_SIZE), offset = (page * size).toLong())
    }

    suspend fun assignPatient(principal: UserPrincipal, doctorId: Uuid, patientId: Uuid): DoctorPatientRelation {
        requireAdmin(principal)
        validateRoles(doctorId, patientId)
        val relation = DoctorPatientRelation(
            doctorId = doctorId,
            patientId = patientId,
            createdAt = Clock.System.now(),
        )
        repo.save(relation)
        try {
            syncAllowedPatients(doctorId)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error(e) { "doctor_patient_assign kc_sync_failed rolling_back doctor=$doctorId patient=$patientId" }
            runCatching { repo.delete(doctorId, patientId) }.onFailure { re ->
                logger.error(re) { "doctor_patient_assign rollback_failed doctor=$doctorId patient=$patientId" }
            }
            throw e
        }
        logger.info { "doctor_patient_assign admin=${principal.userId} doctor=$doctorId patient=$patientId" }
        return relation
    }

    suspend fun removePatient(principal: UserPrincipal, doctorId: Uuid, patientId: Uuid) {
        requireAdmin(principal)
        val deleted = repo.delete(doctorId, patientId)
        if (!deleted) throw ResourceNotFoundException("Doctor-patient relation not found")
        syncAllowedPatients(doctorId)
        logger.info { "doctor_patient_remove admin=${principal.userId} doctor=$doctorId patient=$patientId" }
    }

    private suspend fun validateRoles(doctorId: Uuid, patientId: Uuid) {
        val doctorRoles = keycloak.getUserRoles(doctorId).map { it.name }.toSet()
        if (Role.DOCTOR.toKeycloakName() !in doctorRoles) {
            throw BusinessValidationException("User $doctorId does not have the DOCTOR role")
        }
        val patientRoles = keycloak.getUserRoles(patientId).map { it.name }.toSet()
        if (Role.PATIENT.toKeycloakName() !in patientRoles) {
            throw BusinessValidationException("User $patientId does not have the PATIENT role")
        }
    }

    private suspend fun syncAllowedPatients(doctorId: Uuid) {
        val patientIds = repo.findAllPatientIdsByDoctorId(doctorId).map { it.toString() }
        keycloak.updateUserAttributes(doctorId, mapOf("allowed_patients" to patientIds))
    }

    private fun requireAdmin(principal: UserPrincipal) {
        if (!principal.isAdmin()) throw AuthorizationException("Admin role required")
    }
}

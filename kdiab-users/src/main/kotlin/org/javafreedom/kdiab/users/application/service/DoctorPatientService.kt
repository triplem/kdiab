@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.domain.model.DoctorPatientRelation
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakAdminClient

private val logger = KotlinLogging.logger {}

class DoctorPatientService(
    private val repo: DoctorPatientRepository,
    private val keycloak: KeycloakAdminClient,
) {
    suspend fun listPatients(principal: UserPrincipal, doctorId: Uuid): List<DoctorPatientRelation> {
        if (!principal.isAdmin() && principal.userId != doctorId) {
            throw AuthorizationException("Access denied")
        }
        return repo.findByDoctorId(doctorId)
    }

    suspend fun assignPatient(principal: UserPrincipal, doctorId: Uuid, patientId: Uuid): DoctorPatientRelation {
        requireAdmin(principal)
        val relation = DoctorPatientRelation(
            doctorId = doctorId,
            patientId = patientId,
            createdAt = Clock.System.now(),
        )
        repo.save(relation)
        syncAllowedPatients(doctorId)
        logger.info { "doctor_patient_assign admin=${principal.userId} doctor=$doctorId patient=$patientId" }
        return relation
    }

    suspend fun removePatient(principal: UserPrincipal, doctorId: Uuid, patientId: Uuid) {
        requireAdmin(principal)
        repo.delete(doctorId, patientId)
        syncAllowedPatients(doctorId)
        logger.info { "doctor_patient_remove admin=${principal.userId} doctor=$doctorId patient=$patientId" }
    }

    private suspend fun syncAllowedPatients(doctorId: Uuid) {
        val patientIds = repo.findAllPatientIdsByDoctorId(doctorId).map { it.toString() }
        keycloak.updateUserAttributes(doctorId, mapOf("allowed_patients" to patientIds))
    }

    private fun requireAdmin(principal: UserPrincipal) {
        if (!principal.isAdmin()) throw AuthorizationException("Admin role required")
    }
}

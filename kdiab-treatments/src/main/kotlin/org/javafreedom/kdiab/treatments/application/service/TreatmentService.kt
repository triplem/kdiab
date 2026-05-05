@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.application.service

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.treatments.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.treatments.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.treatments.domain.model.Treatment
import org.javafreedom.kdiab.treatments.domain.model.TreatmentType
import org.javafreedom.kdiab.treatments.domain.repository.TreatmentRepository

class TreatmentService(
    private val treatmentRepository: TreatmentRepository
) {
    suspend fun addTreatment(treatment: Treatment): Treatment {
        val now = Clock.System.now()
        if (treatment.treatedAt < now - 3650.days) {
            throw BusinessValidationException("treatedAt cannot be more than 10 years in the past")
        }
        if (treatment.treatedAt > now + 1.days) {
            throw BusinessValidationException("treatedAt cannot be more than 1 day in the future")
        }
        return treatmentRepository.save(treatment)
    }

    suspend fun getTreatments(userId: Uuid, from: Instant? = null, to: Instant? = null): List<Treatment> =
        treatmentRepository.findByUserId(userId, from, to)

    suspend fun getTreatmentsByType(userId: Uuid, type: TreatmentType): List<Treatment> =
        treatmentRepository.findByUserIdAndType(userId, type)

    suspend fun deleteTreatments(ids: List<Uuid>, userId: Uuid) {
        if (ids.isEmpty()) throw ResourceNotFoundException("No treatment IDs provided")
        treatmentRepository.deleteAll(ids, userId)
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@file:Suppress("LongParameterList")
package org.javafreedom.kdiab.treatments.application.service

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.treatments.domain.model.PagedTreatments
import org.javafreedom.kdiab.treatments.domain.model.Treatment
import org.javafreedom.kdiab.treatments.domain.model.TreatmentStatus
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

    suspend fun updateTreatment(
        treatmentId: Uuid,
        userId: Uuid,
        treatedAt: Instant,
        data: JsonObject,
        notes: String?,
    ): Treatment = treatmentRepository.update(treatmentId, userId, treatedAt, data, notes)

    suspend fun getTreatments(
        userId: Uuid,
        from: Instant? = null,
        to: Instant? = null,
        status: TreatmentStatus = TreatmentStatus.ACTIVE,
        page: Int = 0,
        size: Int = 50,
    ): PagedTreatments {
        val items = treatmentRepository.findByUserId(userId, from, to, status, page, size)
        val totalCount = treatmentRepository.countByUserId(userId, from, to, status)
        return PagedTreatments(items = items, page = page, size = size, totalCount = totalCount)
    }

    suspend fun getTreatmentsByType(
        userId: Uuid,
        type: TreatmentType,
        from: Instant? = null,
        to: Instant? = null,
        status: TreatmentStatus = TreatmentStatus.ACTIVE,
    ): List<Treatment> = treatmentRepository.findByUserIdAndType(userId, type, from, to, status)

    suspend fun archiveTreatments(ids: List<Uuid>, userId: Uuid) {
        if (ids.isEmpty()) throw ResourceNotFoundException("No treatment IDs provided")
        treatmentRepository.archiveAll(ids, userId)
    }

    suspend fun unarchiveTreatments(ids: List<Uuid>, userId: Uuid) {
        if (ids.isEmpty()) throw ResourceNotFoundException("No treatment IDs provided")
        treatmentRepository.unarchiveAll(ids, userId)
    }

    suspend fun deleteTreatments(ids: List<Uuid>, userId: Uuid) {
        if (ids.isEmpty()) throw ResourceNotFoundException("No treatment IDs provided")
        treatmentRepository.deleteAll(ids, userId)
    }
}

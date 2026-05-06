@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@file:Suppress("LongParameterList")
package org.javafreedom.kdiab.treatments.domain.repository

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.treatments.domain.model.Treatment
import org.javafreedom.kdiab.treatments.domain.model.TreatmentStatus
import org.javafreedom.kdiab.treatments.domain.model.TreatmentType

interface TreatmentRepository {
    suspend fun save(treatment: Treatment): Treatment
    suspend fun update(treatmentId: Uuid, userId: Uuid, treatedAt: Instant, data: JsonObject, notes: String?): Treatment
    suspend fun findByUserId(
        userId: Uuid,
        from: Instant? = null,
        to: Instant? = null,
        status: TreatmentStatus = TreatmentStatus.ACTIVE,
        page: Int = 0,
        size: Int = 50,
    ): List<Treatment>
    suspend fun countByUserId(
        userId: Uuid,
        from: Instant? = null,
        to: Instant? = null,
        status: TreatmentStatus = TreatmentStatus.ACTIVE,
    ): Long
    suspend fun findByUserIdAndType(
        userId: Uuid,
        type: TreatmentType,
        from: Instant? = null,
        to: Instant? = null,
        status: TreatmentStatus = TreatmentStatus.ACTIVE,
    ): List<Treatment>
    suspend fun archiveAll(ids: List<Uuid>, userId: Uuid)
    suspend fun deleteAll(ids: List<Uuid>, userId: Uuid)
}

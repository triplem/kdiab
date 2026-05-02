@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.domain.repository

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.treatments.domain.model.Treatment
import org.javafreedom.kdiab.treatments.domain.model.TreatmentType

interface TreatmentRepository {
    suspend fun save(treatment: Treatment): Treatment
    suspend fun findByUserId(userId: Uuid): List<Treatment>
    suspend fun findByUserIdAndType(userId: Uuid, type: TreatmentType): List<Treatment>
    suspend fun deleteAll(ids: List<Uuid>, userId: Uuid)
}

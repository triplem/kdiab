@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.domain.repository

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.measures.domain.model.Measure
import org.javafreedom.kdiab.measures.domain.model.MeasureStatus
import org.javafreedom.kdiab.measures.domain.model.MeasureType

interface MeasureRepository {
    suspend fun save(measure: Measure): Measure
    @Suppress("LongParameterList")
    suspend fun findByUserId(
        userId: Uuid, page: Int, size: Int,
        from: Instant? = null, to: Instant? = null,
        status: MeasureStatus = MeasureStatus.ACTIVE,
    ): List<Measure>
    suspend fun countByUserId(
        userId: Uuid, from: Instant? = null, to: Instant? = null,
        status: MeasureStatus = MeasureStatus.ACTIVE,
    ): Long
    suspend fun findByUserIdAndType(userId: Uuid, type: MeasureType): List<Measure>
    suspend fun update(measureId: Uuid, userId: Uuid, measuredAt: Instant, data: JsonObject): Measure
    suspend fun archive(ids: List<Uuid>, userId: Uuid)
    suspend fun unarchive(ids: List<Uuid>, userId: Uuid)
    suspend fun deleteAll(ids: List<Uuid>, userId: Uuid)
}

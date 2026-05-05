@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.domain.repository

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.measures.domain.model.Measure
import org.javafreedom.kdiab.measures.domain.model.MeasureType

interface MeasureRepository {
    suspend fun save(measure: Measure): Measure
    suspend fun findByUserId(
        userId: Uuid, page: Int, size: Int,
        from: Instant? = null, to: Instant? = null,
    ): List<Measure>
    suspend fun countByUserId(userId: Uuid, from: Instant? = null, to: Instant? = null): Long
    suspend fun findByUserIdAndType(userId: Uuid, type: MeasureType): List<Measure>
    suspend fun archive(ids: List<Uuid>, userId: Uuid)
    suspend fun deleteAll(ids: List<Uuid>, userId: Uuid)
}

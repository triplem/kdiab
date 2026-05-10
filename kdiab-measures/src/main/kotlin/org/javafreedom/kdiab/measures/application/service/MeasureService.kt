@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.application.service

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.measures.domain.model.Measure
import org.javafreedom.kdiab.measures.domain.model.MeasureStatus
import org.javafreedom.kdiab.measures.domain.model.PagedMeasures
import org.javafreedom.kdiab.measures.domain.repository.MeasureRepository

class MeasureService(
    private val measureRepository: MeasureRepository
) {
    suspend fun addMeasure(measure: Measure): Measure =
        measureRepository.save(measure)

    suspend fun updateMeasure(measureId: Uuid, userId: Uuid, measuredAt: Instant, data: JsonObject): Measure =
        measureRepository.update(measureId, userId, measuredAt, data)

    @Suppress("LongParameterList")
    suspend fun getMeasures(
        userId: Uuid, page: Int, size: Int,
        from: Instant? = null, to: Instant? = null,
        status: MeasureStatus = MeasureStatus.ACTIVE,
    ): PagedMeasures {
        val items = measureRepository.findByUserId(userId, page, size, from, to, status)
        val total = measureRepository.countByUserId(userId, from, to, status)
        return PagedMeasures(items = items, page = page, size = size, totalCount = total)
    }

    suspend fun archiveMeasures(ids: List<Uuid>, userId: Uuid) {
        if (ids.isEmpty()) throw ResourceNotFoundException("No measure IDs provided")
        measureRepository.archive(ids, userId)
    }

    suspend fun unarchiveMeasures(ids: List<Uuid>, userId: Uuid) {
        if (ids.isEmpty()) throw ResourceNotFoundException("No measure IDs provided")
        measureRepository.unarchive(ids, userId)
    }

    suspend fun deleteMeasures(ids: List<Uuid>, userId: Uuid) {
        if (ids.isEmpty()) throw ResourceNotFoundException("No measure IDs provided")
        measureRepository.deleteAll(ids, userId)
    }
}

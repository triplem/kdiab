@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.application.service

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.measures.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.measures.domain.model.Measure
import org.javafreedom.kdiab.measures.domain.model.PagedMeasures
import org.javafreedom.kdiab.measures.domain.repository.MeasureRepository

class MeasureService(
    private val measureRepository: MeasureRepository
) {
    suspend fun addMeasure(measure: Measure): Measure =
        measureRepository.save(measure)

    suspend fun getMeasures(userId: Uuid, page: Int, size: Int): PagedMeasures {
        val items = measureRepository.findByUserId(userId, page, size)
        val total = measureRepository.countByUserId(userId)
        return PagedMeasures(items = items, page = page, size = size, totalCount = total)
    }

    suspend fun archiveMeasures(ids: List<Uuid>, userId: Uuid) {
        if (ids.isEmpty()) throw ResourceNotFoundException("No measure IDs provided")
        measureRepository.archive(ids, userId)
    }

    suspend fun deleteMeasures(ids: List<Uuid>, userId: Uuid) {
        if (ids.isEmpty()) throw ResourceNotFoundException("No measure IDs provided")
        measureRepository.deleteAll(ids, userId)
    }
}

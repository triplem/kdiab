@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package org.javafreedom.kdiab.analyze.application.service

import org.javafreedom.kdiab.analyze.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.analyze.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.analyze.domain.model.Timeline
import org.javafreedom.kdiab.analyze.domain.model.TimelineMeasure
import org.javafreedom.kdiab.analyze.domain.model.TimelineTreatment
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlin.time.Instant
import kotlin.uuid.Uuid

class TimelineService(
    private val measuresClient: MeasuresClient,
    private val treatmentsClient: TreatmentsClient,
) {
    suspend fun getTimeline(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        correlationId: String,
    ): Timeline = supervisorScope {
        val fromInstant = Instant.parse(from)
        val toInstant = Instant.parse(to)

        val measuresDeferred = async {
            runCatching { measuresClient.getMeasures(userId, authorization, correlationId, from, to) }
        }
        val treatmentsDeferred = async {
            runCatching { treatmentsClient.getTreatments(userId, authorization, correlationId, from, to) }
        }

        val measuresResult = measuresDeferred.await()
        val treatmentsResult = treatmentsDeferred.await()

        val errors = mutableListOf<String>()
        val allMeasures = measuresResult.getOrElse { e -> errors.add("measures: ${e.message}"); emptyList() }
        val allTreatments = treatmentsResult.getOrElse { e -> errors.add("treatments: ${e.message}"); emptyList() }

        val filteredMeasures = allMeasures
            .mapNotNull { dto ->
                val id = runCatching { Uuid.parse(dto.id) }.getOrNull() ?: return@mapNotNull null
                val uId = runCatching { Uuid.parse(dto.userId) }.getOrNull() ?: return@mapNotNull null
                val measuredAt = runCatching { Instant.parse(dto.measuredAt) }.getOrNull() ?: return@mapNotNull null
                TimelineMeasure(
                    id = id,
                    userId = uId,
                    measuredAt = measuredAt,
                    type = dto.type.value,
                    source = dto.source.value,
                    data = dto.data,
                    status = dto.status.value,
                )
            }
            .filter { it.measuredAt >= fromInstant && it.measuredAt <= toInstant }

        val filteredTreatments = allTreatments
            .mapNotNull { dto ->
                val id = runCatching { Uuid.parse(dto.id) }.getOrNull() ?: return@mapNotNull null
                val uId = runCatching { Uuid.parse(dto.userId) }.getOrNull() ?: return@mapNotNull null
                val treatedAt = runCatching { Instant.parse(dto.treatedAt) }.getOrNull() ?: return@mapNotNull null
                TimelineTreatment(
                    id = id,
                    userId = uId,
                    treatedAt = treatedAt,
                    type = dto.type.value,
                    notes = dto.notes,
                    data = dto.data,
                )
            }
            .filter { it.treatedAt >= fromInstant && it.treatedAt <= toInstant }

        Timeline(measures = filteredMeasures, treatments = filteredTreatments, errors = errors)
    }
}

package org.javafreedom.kdiab.analyze.application.service

import org.javafreedom.kdiab.analyze.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.analyze.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.analyze.domain.model.Timeline
import org.javafreedom.kdiab.analyze.domain.model.TimelineMeasure
import org.javafreedom.kdiab.analyze.domain.model.TimelineTreatment
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Instant

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
            .filter { dto ->
                val t = runCatching { Instant.parse(dto.measuredAt) }.getOrNull() ?: return@filter false
                t >= fromInstant && t <= toInstant
            }
            .map { dto ->
                TimelineMeasure(
                    id = dto.id,
                    userId = dto.userId,
                    measuredAt = dto.measuredAt,
                    type = dto.type,
                    source = dto.source,
                    data = dto.data,
                    status = dto.status,
                )
            }

        val filteredTreatments = allTreatments
            .filter { dto ->
                val t = runCatching { Instant.parse(dto.treatedAt) }.getOrNull() ?: return@filter false
                t >= fromInstant && t <= toInstant
            }
            .map { dto ->
                TimelineTreatment(
                    id = dto.id,
                    userId = dto.userId,
                    treatedAt = dto.treatedAt,
                    type = dto.type,
                    notes = dto.notes,
                    data = dto.data,
                )
            }

        Timeline(measures = filteredMeasures, treatments = filteredTreatments, errors = errors)
    }
}

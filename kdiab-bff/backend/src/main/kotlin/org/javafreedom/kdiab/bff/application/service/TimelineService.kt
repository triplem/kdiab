package org.javafreedom.kdiab.bff.application.service

import org.javafreedom.kdiab.bff.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.bff.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.bff.domain.model.Timeline
import org.javafreedom.kdiab.bff.domain.model.TimelineMeasure
import org.javafreedom.kdiab.bff.domain.model.TimelineTreatment
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant

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
    ): Timeline = coroutineScope {
        val fromInstant = Instant.parse(from)
        val toInstant = Instant.parse(to)

        val measuresDeferred = async { measuresClient.getMeasures(userId, authorization, correlationId) }
        val treatmentsDeferred = async { treatmentsClient.getTreatments(userId, authorization, correlationId) }

        val allMeasures = measuresDeferred.await()
        val allTreatments = treatmentsDeferred.await()

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

        Timeline(measures = filteredMeasures, treatments = filteredTreatments)
    }
}

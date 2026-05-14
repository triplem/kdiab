@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.adapters.inbound.web

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.measures.api.models.CreateHba1cEntryRequest
import org.javafreedom.kdiab.measures.api.models.HbA1cEntryResponse
import org.javafreedom.kdiab.measures.api.models.HbA1cSource as ApiHbA1cSource
import org.javafreedom.kdiab.measures.domain.model.HbA1cEntry
import org.javafreedom.kdiab.measures.domain.model.HbA1cSource

fun CreateHba1cEntryRequest.toDomain(targetUserId: Uuid): HbA1cEntry = HbA1cEntry(
    id = Uuid.random(),
    userId = targetUserId,
    measuredAt = try {
        Instant.parse(this.measuredAt)
    } catch (e: IllegalArgumentException) {
        throw BusinessValidationException(
            "Invalid measuredAt timestamp: '${this.measuredAt}' is not a valid ISO-8601 instant",
            e
        )
    },
    valuePercent = this.valuePercent,
    source = this.source?.let { HbA1cSource.valueOf(it.name) } ?: HbA1cSource.LAB,
    notes = this.notes,
    createdAt = Clock.System.now(),
)

fun HbA1cEntry.toApi(): HbA1cEntryResponse = HbA1cEntryResponse(
    id = this.id.toString(),
    userId = this.userId.toString(),
    measuredAt = this.measuredAt.toString(),
    valuePercent = this.valuePercent,
    source = ApiHbA1cSource.valueOf(this.source.name),
    notes = this.notes,
    createdAt = this.createdAt.toString(),
)

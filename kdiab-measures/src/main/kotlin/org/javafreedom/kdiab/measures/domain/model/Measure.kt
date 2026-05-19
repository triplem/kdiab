@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject

enum class MeasureType {
    CGM,
    BGM,
    BLOOD_PRESSURE,
    WEIGHT,
    PULSE,
    BG_CHECK,
    KETONE_CHECK,
    GLYCOSYLATED_HEMOGLOBIN,
}

enum class MeasureSource {
    MANUAL,
    NIGHTSCOUT,
    GOOGLE_FIT,
    APPLE_HEALTH,
    LAB,
    ESTIMATED,
}

enum class MeasureStatus {
    ACTIVE,
    ARCHIVED
}

data class Measure(
    val id: Uuid,
    val userId: Uuid,
    val measuredAt: Instant,
    val createdAt: Instant,
    val type: MeasureType,
    val source: MeasureSource,
    val data: JsonObject,
    val status: MeasureStatus = MeasureStatus.ACTIVE
)

data class PagedMeasures(
    val items: List<Measure>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)

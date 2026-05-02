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
}

enum class MeasureSource {
    MANUAL,
    NIGHTSCOUT,
    GOOGLE_FIT,
    APPLE_HEALTH
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

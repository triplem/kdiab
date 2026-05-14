@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class HbA1cSource {
    LAB,
    CGM_ESTIMATED,
}

data class HbA1cEntry(
    val id: Uuid,
    val userId: Uuid,
    val measuredAt: Instant,
    val valuePercent: Double,
    val source: HbA1cSource,
    val notes: String?,
    val createdAt: Instant,
)

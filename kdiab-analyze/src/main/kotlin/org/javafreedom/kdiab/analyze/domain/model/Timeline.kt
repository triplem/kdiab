@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package org.javafreedom.kdiab.analyze.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

data class TimelineMeasure(
    val id: Uuid,
    val userId: Uuid,
    val measuredAt: Instant,
    val type: String,
    val source: String?,
    val data: JsonObject,
    val status: String,
)

data class TimelineTreatment(
    val id: Uuid,
    val userId: Uuid,
    val treatedAt: Instant,
    val type: String,
    val notes: String?,
    val data: JsonObject,
)

data class Timeline(
    val measures: List<TimelineMeasure>,
    val treatments: List<TimelineTreatment>,
    val errors: List<String> = emptyList(),
)

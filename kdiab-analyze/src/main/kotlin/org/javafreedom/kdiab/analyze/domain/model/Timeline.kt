package org.javafreedom.kdiab.analyze.domain.model

import kotlinx.serialization.json.JsonObject

data class TimelineMeasure(
    val id: String,
    val userId: String,
    val measuredAt: String,
    val type: String,
    val source: String?,
    val data: JsonObject,
    val status: String,
)

data class TimelineTreatment(
    val id: String,
    val userId: String,
    val treatedAt: String,
    val type: String,
    val notes: String?,
    val data: JsonObject,
)

data class Timeline(
    val measures: List<TimelineMeasure>,
    val treatments: List<TimelineTreatment>,
)

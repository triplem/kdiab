package org.javafreedom.kdiab.analyze.domain.model

import kotlinx.serialization.json.JsonObject

data class UpstreamMeasure(
    val id: String,
    val userId: String,
    val measuredAt: String,
    val type: String,
    val source: String?,
    val data: JsonObject,
    val status: String,
)

data class UpstreamTreatment(
    val id: String,
    val userId: String,
    val treatedAt: String,
    val type: String,
    val notes: String?,
    val data: JsonObject,
)

data class UpstreamProfile(
    val id: String,
    val userId: String,
    val status: String,
    val name: String,
    val insulinType: String,
    val durationOfAction: Int,
    val analysisLow: Double?,
    val analysisHigh: Double?,
    val createdAt: String?,
    val validFrom: String?,
    val previousProfileId: String?,
    val activatedAt: String?,
    val archivedAt: String?,
    val basal: List<BasalSegment>?,
    val icr: List<RatioSegment>?,
    val isf: List<RatioSegment>?,
    val targets: List<TargetSegment>?,
)

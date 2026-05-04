package org.javafreedom.kdiab.analyze.adapters.inbound.web

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.analyze.domain.model.AgpResult
import org.javafreedom.kdiab.analyze.domain.model.Hba1cResult
import org.javafreedom.kdiab.analyze.domain.model.ProfilesResult
import org.javafreedom.kdiab.analyze.domain.model.Timeline

@Serializable
data class TimelineResponse(
    val measures: List<TimelineMeasureDto>,
    val treatments: List<TimelineTreatmentDto>,
    val errors: List<String> = emptyList(),
)

@Serializable
data class TimelineMeasureDto(
    val id: String,
    val userId: String,
    val measuredAt: String,
    val type: String,
    val source: String? = null,
    val data: JsonObject,
    val status: String,
)

@Serializable
data class TimelineTreatmentDto(
    val id: String,
    val userId: String,
    val treatedAt: String,
    val type: String,
    val notes: String? = null,
    val data: JsonObject,
)

@Serializable
data class Hba1cResponseDto(
    val hba1c: Double?,
    val meanGlucose: Double,
    val readingCount: Int,
    val tir: TirBreakdownDto,
)

@Serializable
data class TirBreakdownDto(
    val belowCount: Int,
    val inRangeCount: Int,
    val aboveCount: Int,
    val highCount: Int,
    val totalCount: Int,
)

@Serializable
data class AgpResponseDto(val hourlyData: List<AgpHourlyDataDto>)

@Serializable
data class AgpHourlyDataDto(
    val hour: Int,
    val p10: Double?,
    val p25: Double?,
    val median: Double?,
    val p75: Double?,
    val p90: Double?,
    val count: Int,
)

@Serializable
data class ProfilesResponseDto(val profiles: List<ProfileSummaryDto>)

@Serializable
data class ProfileSummaryDto(
    val id: String,
    val userId: String,
    val status: String,
    val name: String,
    val createdAt: String? = null,
    val validFrom: String? = null,
    val previousProfileId: String? = null,
)

fun Timeline.toResponse() = TimelineResponse(
    measures = measures.map {
        TimelineMeasureDto(it.id, it.userId, it.measuredAt, it.type, it.source, it.data, it.status)
    },
    treatments = treatments.map {
        TimelineTreatmentDto(it.id, it.userId, it.treatedAt, it.type, it.notes, it.data)
    },
    errors = errors,
)

fun Hba1cResult.toResponse() = Hba1cResponseDto(
    hba1c = hba1c,
    meanGlucose = meanGlucose,
    readingCount = readingCount,
    tir = TirBreakdownDto(tir.belowCount, tir.inRangeCount, tir.aboveCount, tir.highCount, tir.totalCount),
)

fun AgpResult.toResponse() = AgpResponseDto(
    hourlyData = hourlyData.map {
        AgpHourlyDataDto(it.hour, it.p10, it.p25, it.median, it.p75, it.p90, it.count)
    },
)

fun ProfilesResult.toResponse() = ProfilesResponseDto(
    profiles = profiles.map {
        ProfileSummaryDto(it.id, it.userId, it.status, it.name, it.createdAt, it.validFrom, it.previousProfileId)
    },
)

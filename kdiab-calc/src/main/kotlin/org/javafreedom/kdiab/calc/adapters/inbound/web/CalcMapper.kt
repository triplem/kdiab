package org.javafreedom.kdiab.calc.adapters.inbound.web

import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.calc.domain.model.CgmTrend
import org.javafreedom.kdiab.calc.domain.model.DoseBreakdown
import org.javafreedom.kdiab.calc.domain.model.DoseRequest
import org.javafreedom.kdiab.calc.domain.model.DoseResult
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException

@Serializable
data class DoseRequestDto(
    val currentBg: Double,
    val glucoseUnit: String,
    val trend: String,
    val carbsGrams: Double = 0.0,
    // Nullable with a null default so an omitted OR explicit-null activeIob both deserialize to null and
    // are rejected in toDomain() with a clear clinical 400 (rather than the generic SerializationException
    // "Invalid request body"). Insulin-on-board is required — never silently defaulted to 0 (#1563).
    val activeIob: Double? = null,
    val useProfileTime: String? = null,
)

@Serializable
data class DoseBreakdownDto(
    val currentBgMgDl: Double,
    val targetBgMgDl: Double,
    val isf: Double,
    val icr: Double,
    val trend: String,
    val carbsGrams: Double,
)

@Serializable
data class DoseResponseDto(
    val correctionDose: Double,
    val carbDose: Double,
    val trendAdjustment: Double,
    val totalRecommended: Double,
    val breakdown: DoseBreakdownDto,
    val profileId: String,
    val warnings: List<String>,
    val recommendedWaitMinutes: Int? = null,
)

fun DoseRequestDto.toDomain(): DoseRequest {
    val cgmTrend = runCatching { CgmTrend.valueOf(trend) }
        .getOrElse { throw IllegalArgumentException("Unknown CGM trend: $trend") }
    val iob = activeIob
        ?: throw BusinessValidationException(
            "activeIob is required to prevent insulin stacking — supply the patient's current insulin-on-board"
        )
    if (iob < 0.0) throw BusinessValidationException("activeIob must be zero or positive")
    return DoseRequest(
        currentBg = currentBg,
        glucoseUnit = glucoseUnit,
        trend = cgmTrend,
        carbsGrams = carbsGrams,
        activeIob = iob,
        useProfileTime = useProfileTime,
    )
}

fun DoseResult.toDto() = DoseResponseDto(
    correctionDose = correctionDose,
    carbDose = carbDose,
    trendAdjustment = trendAdjustment,
    totalRecommended = totalRecommended,
    breakdown = breakdown.toDto(),
    profileId = profileId,
    warnings = warnings,
    recommendedWaitMinutes = recommendedWaitMinutes,
)

fun DoseBreakdown.toDto() = DoseBreakdownDto(
    currentBgMgDl = currentBgMgDl,
    targetBgMgDl = targetBgMgDl,
    isf = isf,
    icr = icr,
    trend = trend.name,
    carbsGrams = carbsGrams,
)

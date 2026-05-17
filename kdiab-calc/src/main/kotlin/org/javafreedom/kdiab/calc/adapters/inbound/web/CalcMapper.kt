package org.javafreedom.kdiab.calc.adapters.inbound.web

import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.calc.domain.model.CgmTrend
import org.javafreedom.kdiab.calc.domain.model.DoseBreakdown
import org.javafreedom.kdiab.calc.domain.model.DoseRequest
import org.javafreedom.kdiab.calc.domain.model.DoseResult

@Serializable
data class DoseRequestDto(
    val currentBg: Double,
    val glucoseUnit: String,
    val trend: String,
    val carbsGrams: Double = 0.0,
    val activeIob: Double = 0.0,
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
)

fun DoseRequestDto.toDomain(): DoseRequest {
    val cgmTrend = runCatching { CgmTrend.valueOf(trend) }
        .getOrElse { throw IllegalArgumentException("Unknown CGM trend: $trend") }
    return DoseRequest(
        currentBg = currentBg,
        glucoseUnit = glucoseUnit,
        trend = cgmTrend,
        carbsGrams = carbsGrams,
        activeIob = activeIob,
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
)

fun DoseBreakdown.toDto() = DoseBreakdownDto(
    currentBgMgDl = currentBgMgDl,
    targetBgMgDl = targetBgMgDl,
    isf = isf,
    icr = icr,
    trend = trend.name,
    carbsGrams = carbsGrams,
)

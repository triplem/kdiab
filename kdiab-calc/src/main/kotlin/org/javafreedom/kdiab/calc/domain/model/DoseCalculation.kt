package org.javafreedom.kdiab.calc.domain.model

enum class CgmTrend { DOUBLE_UP, SINGLE_UP, FORTY_FIVE_UP, FLAT, FORTY_FIVE_DOWN, SINGLE_DOWN, DOUBLE_DOWN, NONE }

data class DoseRequest(
    val currentBg: Double,
    val glucoseUnit: String,
    val trend: CgmTrend,
    val carbsGrams: Double = 0.0,
    // No default: insulin-on-board is a required, safety-critical input (#1563). A silent 0.0 default
    // would let a caller omit IOB and receive a correction that ignores active insulin (stacking risk).
    val activeIob: Double,
    val useProfileTime: String? = null,
)

data class DoseBreakdown(
    val currentBgMgDl: Double,
    val targetBgMgDl: Double,
    val isf: Double,
    val icr: Double,
    val trend: CgmTrend,
    val carbsGrams: Double,
)

data class DoseResult(
    val correctionDose: Double,
    val carbDose: Double,
    val trendAdjustment: Double,
    val totalRecommended: Double,
    val breakdown: DoseBreakdown,
    val profileId: String,
    val warnings: List<String>,
    val recommendedWaitMinutes: Int? = null,
)

package org.javafreedom.kdiab.calc.domain.model

data class ActiveProfile(
    val id: String,
    val timeZone: String?,
    val isf: List<IsfRatio>,
    val icr: List<IcrRatio>,
    val targets: List<GlucoseTarget>,
    val insulinToMealInterval: List<InsulinToMealInterval> = emptyList(),
)

data class IsfRatio(val startTime: String, val value: Double)

data class IcrRatio(val startTime: String, val value: Double)

data class GlucoseTarget(val startTime: String, val low: Double, val high: Double)

data class InsulinToMealInterval(val startTime: String, val minutes: Int)

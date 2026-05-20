package org.javafreedom.kdiab.calc.domain.model

data class ActiveProfile(
    val id: String,
    val timeZone: String?,
    val isf: List<IsfRatio>,
    val icr: List<IcrRatio>,
    val targets: List<GlucoseTarget>,
)

data class IsfRatio(val startTime: String, val value: Double)

data class IcrRatio(val startTime: String, val value: Double)

data class GlucoseTarget(val startTime: String, val low: Double, val high: Double)

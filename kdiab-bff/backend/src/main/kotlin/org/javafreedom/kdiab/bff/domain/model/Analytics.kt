package org.javafreedom.kdiab.bff.domain.model

data class TirBreakdown(
    val belowCount: Int = 0,
    val inRangeCount: Int = 0,
    val aboveCount: Int = 0,
    val highCount: Int = 0,
    val totalCount: Int = 0,
)

data class Hba1cResult(
    val hba1c: Double?,
    val meanGlucose: Double,
    val readingCount: Int,
    val tir: TirBreakdown,
)

data class AgpHourlyData(
    val hour: Int,
    val p10: Double,
    val p25: Double,
    val median: Double,
    val p75: Double,
    val p90: Double,
    val count: Int,
)

data class AgpResult(
    val hourlyData: List<AgpHourlyData>,
)

data class ProfileSummary(
    val id: String,
    val userId: String,
    val status: String,
    val name: String,
    val createdAt: String?,
    val previousProfileId: String?,
)

data class ProfilesResult(
    val profiles: List<ProfileSummary>,
)

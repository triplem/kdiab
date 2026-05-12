package org.javafreedom.kdiab.analyze.domain.model

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
    val warnings: List<String> = emptyList(),
)

data class AgpHourlyData(
    val hour: Int,
    val p10: Double?,
    val p25: Double?,
    val median: Double?,
    val p75: Double?,
    val p90: Double?,
    val count: Int,
)

data class AgpResult(
    val hourlyData: List<AgpHourlyData>,
    val totalReadingCount: Int = 0,
    val sensorWearDays: Int = 0,
    val warnings: List<String> = emptyList(),
)

data class ProfileSummary(
    val id: String,
    val userId: String,
    val status: String,
    val name: String,
    val createdAt: String?,
    val validFrom: String?,
    val previousProfileId: String?,
    val activatedAt: String? = null,
    val archivedAt: String? = null,
)

data class ProfilesResult(
    val profiles: List<ProfileSummary>,
)

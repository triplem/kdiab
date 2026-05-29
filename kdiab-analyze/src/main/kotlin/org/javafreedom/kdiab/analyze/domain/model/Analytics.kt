package org.javafreedom.kdiab.analyze.domain.model

data class TirBreakdown(
    val veryLowCount: Int = 0,
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

data class AgpBucketData(
    val minuteOfDay: Int,
    val p10: Double?,
    val p25: Double?,
    val median: Double?,
    val p75: Double?,
    val p90: Double?,
    val count: Int,
)

data class AgpResult(
    val bucketData: List<AgpBucketData>,
    val totalReadingCount: Int = 0,
    val sensorWearDays: Int = 0,
    val warnings: List<String> = emptyList(),
)

data class BasalSegment(val startTime: String, val value: Double)
data class RatioSegment(val startTime: String, val value: Double)
data class TargetSegment(val startTime: String, val low: Double, val high: Double)

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
    val insulinType: String? = null,
    val durationOfAction: Int? = null,
    val basal: List<BasalSegment>? = null,
    val icr: List<RatioSegment>? = null,
    val isf: List<RatioSegment>? = null,
    val targets: List<TargetSegment>? = null,
)

data class ProfilesResult(
    val profiles: List<ProfileSummary>,
)

data class DeviceUsageResult(
    val userId: String,
    val avgSensorDays: Double?,
    val stddevSensorDays: Double?,
    val avgCatheterDays: Double?,
    val stddevCatheterDays: Double?,
    val avgReservoirDays: Double?,
    val stddevReservoirDays: Double?,
    val avgBatteryDays: Double?,
    val stddevBatteryDays: Double?,
)

data class DeviceAge(
    val catheterChangedAt: String?,
    val reservoirChangedAt: String?,
    val sensorInsertedAt: String?,
    val batteryChangedAt: String?,
)

data class DeviceStatus(
    val id: String,
    val userId: String,
    val recordedAt: String,
    val device: String,
    val pumpName: String?,
    val reservoirUnits: Double?,
    val batteryLevel: Int?,
    val pumpConnected: Boolean?,
)

data class DailyStatRow(
    val date: String,           // YYYY-MM-DD in patient's local timezone, or "summary"
    val cgmCount: Int,
    val veryLowPercent: Double?,    // null if no readings
    val lowPercent: Double?,
    val inRangePercent: Double?,
    val highPercent: Double?,
    val veryHighPercent: Double?,
    val p25: Double?,
    val median: Double?,
    val p75: Double?,
    val sd: Double?,
    val eHbA1c: Double?,
)

data class DailyStatsResult(
    val rows: List<DailyStatRow>,  // ordered newest first (reverse chronological)
    val summary: DailyStatRow,     // averages across days with ≥1 reading; date="summary"
    val warnings: List<String> = emptyList(),
)

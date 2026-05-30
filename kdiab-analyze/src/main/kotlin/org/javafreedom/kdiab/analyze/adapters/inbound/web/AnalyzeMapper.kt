package org.javafreedom.kdiab.analyze.adapters.inbound.web

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.analyze.domain.model.AgpResult
import org.javafreedom.kdiab.analyze.domain.model.CgpResult
import org.javafreedom.kdiab.analyze.domain.model.DailyStatRow
import org.javafreedom.kdiab.analyze.domain.model.DailyStatsResult
import org.javafreedom.kdiab.analyze.domain.model.DailyTrendResult
import org.javafreedom.kdiab.analyze.domain.model.DeviceAge
import org.javafreedom.kdiab.analyze.domain.model.DeviceStatus
import org.javafreedom.kdiab.analyze.domain.model.DeviceUsageResult
import org.javafreedom.kdiab.analyze.domain.model.GlucoseDistributionResult
import org.javafreedom.kdiab.analyze.domain.model.Hba1cResult
import org.javafreedom.kdiab.analyze.domain.model.ProfilesResult
import org.javafreedom.kdiab.analyze.domain.model.ReportSummaryResult
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
    val warnings: List<String> = emptyList(),
)

@Serializable
data class TirBreakdownDto(
    val veryLowCount: Int,
    val belowCount: Int,
    val inRangeCount: Int,
    val aboveCount: Int,
    val highCount: Int,
    val totalCount: Int,
)

@Serializable
data class AgpResponseDto(
    val bucketData: List<AgpBucketDataDto>,
    val totalReadingCount: Int = 0,
    val sensorWearDays: Int = 0,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class AgpBucketDataDto(
    val minuteOfDay: Int,
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
    val activatedAt: String? = null,
    val archivedAt: String? = null,
    val insulinType: String? = null,
    val durationOfAction: Int? = null,
    val basal: List<BasalSegmentDto>? = null,
    val icr: List<RatioSegmentDto>? = null,
    val isf: List<RatioSegmentDto>? = null,
    val targets: List<TargetSegmentDto>? = null,
)

@Serializable
data class BasalSegmentDto(val startTime: String, val value: Double)

@Serializable
data class RatioSegmentDto(val startTime: String, val value: Double)

@Serializable
data class TargetSegmentDto(val startTime: String, val low: Double, val high: Double)

@Serializable
data class DeviceAgeResponseDto(
    val catheterChangedAt: String?,
    val reservoirChangedAt: String?,
    val sensorInsertedAt: String?,
    val batteryChangedAt: String?,
)

@Serializable
data class DeviceStatusResponseDto(
    val id: String,
    val userId: String,
    val recordedAt: String,
    val device: String,
    val pumpName: String?,
    val reservoirUnits: Double?,
    val batteryLevel: Int?,
    val pumpConnected: Boolean?,
)

@Serializable
data class HourlyTrendRowDto(
    val hour: Int,
    val meanGlucose: Double?,
    val trendPercent: Double?,
    val trendZone: String?,
    val zone: String?,
    val basalRateIePerH: Double?,
    val carbsG: Double,
)

@Serializable
data class DailyTrendDayDto(
    val date: String,
    val hours: List<HourlyTrendRowDto>,
)

@Serializable
data class DailyTrendResponseDto(
    val days: List<DailyTrendDayDto>,
    val warnings: List<String> = emptyList(),
)

fun Timeline.toResponse() = TimelineResponse(
    measures = measures.map {
        TimelineMeasureDto(
            it.id.toString(),
            it.userId.toString(),
            it.measuredAt.toString(),
            it.type,
            it.source,
            it.data,
            it.status,
        )
    },
    treatments = treatments.map {
        TimelineTreatmentDto(
            it.id.toString(),
            it.userId.toString(),
            it.treatedAt.toString(),
            it.type,
            it.notes,
            it.data,
        )
    },
    errors = errors,
)

fun Hba1cResult.toResponse() = Hba1cResponseDto(
    hba1c = hba1c,
    meanGlucose = meanGlucose,
    readingCount = readingCount,
    tir = TirBreakdownDto(
        veryLowCount = tir.veryLowCount,
        belowCount = tir.belowCount,
        inRangeCount = tir.inRangeCount,
        aboveCount = tir.aboveCount,
        highCount = tir.highCount,
        totalCount = tir.totalCount,
    ),
    warnings = warnings,
)

fun AgpResult.toResponse() = AgpResponseDto(
    bucketData = bucketData.map {
        AgpBucketDataDto(it.minuteOfDay, it.p10, it.p25, it.median, it.p75, it.p90, it.count)
    },
    totalReadingCount = totalReadingCount,
    sensorWearDays = sensorWearDays,
    warnings = warnings,
)

@Serializable
data class DeviceUsageResponseDto(
    val userId: String,
    val avgSensorDays: Double? = null,
    val stddevSensorDays: Double? = null,
    val avgCatheterDays: Double? = null,
    val stddevCatheterDays: Double? = null,
    val avgReservoirDays: Double? = null,
    val stddevReservoirDays: Double? = null,
    val avgBatteryDays: Double? = null,
    val stddevBatteryDays: Double? = null,
)

fun DeviceUsageResult.toResponse() = DeviceUsageResponseDto(
    userId = userId,
    avgSensorDays = avgSensorDays,
    stddevSensorDays = stddevSensorDays,
    avgCatheterDays = avgCatheterDays,
    stddevCatheterDays = stddevCatheterDays,
    avgReservoirDays = avgReservoirDays,
    stddevReservoirDays = stddevReservoirDays,
    avgBatteryDays = avgBatteryDays,
    stddevBatteryDays = stddevBatteryDays,
)

fun ProfilesResult.toResponse() = ProfilesResponseDto(
    profiles = profiles.map {
        ProfileSummaryDto(
            id = it.id,
            userId = it.userId,
            status = it.status,
            name = it.name,
            createdAt = it.createdAt,
            validFrom = it.validFrom,
            previousProfileId = it.previousProfileId,
            activatedAt = it.activatedAt,
            archivedAt = it.archivedAt,
            insulinType = it.insulinType,
            durationOfAction = it.durationOfAction,
            basal = it.basal?.map { s -> BasalSegmentDto(s.startTime, s.value) },
            icr = it.icr?.map { s -> RatioSegmentDto(s.startTime, s.value) },
            isf = it.isf?.map { s -> RatioSegmentDto(s.startTime, s.value) },
            targets = it.targets?.map { s -> TargetSegmentDto(s.startTime, s.low, s.high) },
        )
    },
)

fun DeviceAge.toResponse() = DeviceAgeResponseDto(
    catheterChangedAt = catheterChangedAt,
    reservoirChangedAt = reservoirChangedAt,
    sensorInsertedAt = sensorInsertedAt,
    batteryChangedAt = batteryChangedAt,
)

fun DeviceStatus.toResponse() = DeviceStatusResponseDto(
    id = id,
    userId = userId,
    recordedAt = recordedAt,
    device = device,
    pumpName = pumpName,
    reservoirUnits = reservoirUnits,
    batteryLevel = batteryLevel,
    pumpConnected = pumpConnected,
)

fun DailyTrendResult.toResponse() = DailyTrendResponseDto(
    days = days.map { day ->
        DailyTrendDayDto(
            date = day.date,
            hours = day.hours.map { row ->
                HourlyTrendRowDto(
                    hour = row.hour,
                    meanGlucose = row.meanGlucose,
                    trendPercent = row.trendPercent,
                    trendZone = row.trendZone,
                    zone = row.zone,
                    basalRateIePerH = row.basalRateIePerH,
                    carbsG = row.carbsG,
                )
            },
        )
    },
    warnings = warnings,
)

@Serializable
data class DailyStatRowDto(
    val date: String,
    val cgmCount: Int,
    val veryLowPercent: Double?,
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

@Serializable
data class DailyStatsResponseDto(
    val rows: List<DailyStatRowDto>,
    val summary: DailyStatRowDto,
    val warnings: List<String> = emptyList(),
)

private fun DailyStatRow.toDto() = DailyStatRowDto(
    date = date,
    cgmCount = cgmCount,
    veryLowPercent = veryLowPercent,
    lowPercent = lowPercent,
    inRangePercent = inRangePercent,
    highPercent = highPercent,
    veryHighPercent = veryHighPercent,
    p25 = p25,
    median = median,
    p75 = p75,
    sd = sd,
    eHbA1c = eHbA1c,
)

fun DailyStatsResult.toResponse() = DailyStatsResponseDto(
    rows = rows.map { it.toDto() },
    summary = summary.toDto(),
    warnings = warnings,
)

@Serializable
data class TirZoneDto(val count: Int, val percent: Double)

@Serializable
data class TirResultDto(
    val veryLow: TirZoneDto,
    val low: TirZoneDto,
    val inRange: TirZoneDto,
    val high: TirZoneDto,
    val veryHigh: TirZoneDto,
    val customTirFallback: Boolean,
)

@Suppress("LongParameterList")
@Serializable
data class ReportSummaryResponseDto(
    val displayName: String,
    val daysAnalysed: Int,
    val cgmReadingCount: Int,
    val cgmIntervalMinutes: Int,
    val insulinTypes: List<String>,
    val insulinChanges: Int,
    val avgDaysPerCartridge: Double?,
    val siteChanges: Int,
    val avgDaysPerSite: Double?,
    val sensorInserts: Int,
    val avgDaysPerSensor: Double?,
    val tirProfile: TirResultDto,
    val tirStandard: TirResultDto,
    val minGlucose: Double?,
    val maxGlucose: Double?,
    val meanGlucose: Double?,
    val sd: Double?,
    val gvi: Double?,
    val pgs: Double?,
    val gri: Double?,
    val griZone: String?,
    val eHbA1c: Double?,
    val avgCarbsPerDayG: Double?,
    val avgBolusPerDayIe: Double?,
    val bolusPercent: Double?,
    val avgBasalPerDayIe: Double?,
    val basalPercent: Double?,
    val avgTotalInsulinPerDayIe: Double?,
    val warnings: List<String>,
)

private fun org.javafreedom.kdiab.analyze.domain.model.TirZone.toDto() = TirZoneDto(count, percent)
private fun org.javafreedom.kdiab.analyze.domain.model.TirResult.toDto() = TirResultDto(
    veryLow = veryLow.toDto(),
    low = low.toDto(),
    inRange = inRange.toDto(),
    high = high.toDto(),
    veryHigh = veryHigh.toDto(),
    customTirFallback = customTirFallback,
)

fun ReportSummaryResult.toResponse() = ReportSummaryResponseDto(
    displayName = displayName,
    daysAnalysed = daysAnalysed,
    cgmReadingCount = cgmReadingCount,
    cgmIntervalMinutes = cgmIntervalMinutes,
    insulinTypes = insulinTypes,
    insulinChanges = insulinChanges,
    avgDaysPerCartridge = avgDaysPerCartridge,
    siteChanges = siteChanges,
    avgDaysPerSite = avgDaysPerSite,
    sensorInserts = sensorInserts,
    avgDaysPerSensor = avgDaysPerSensor,
    tirProfile = tirProfile.toDto(),
    tirStandard = tirStandard.toDto(),
    minGlucose = minGlucose,
    maxGlucose = maxGlucose,
    meanGlucose = meanGlucose,
    sd = sd,
    gvi = gvi,
    pgs = pgs,
    gri = gri,
    griZone = griZone,
    eHbA1c = eHbA1c,
    avgCarbsPerDayG = avgCarbsPerDayG,
    avgBolusPerDayIe = avgBolusPerDayIe,
    bolusPercent = bolusPercent,
    avgBasalPerDayIe = avgBasalPerDayIe,
    basalPercent = basalPercent,
    avgTotalInsulinPerDayIe = avgTotalInsulinPerDayIe,
    warnings = warnings,
)

@Serializable
data class GlucoseBucketDto(
    val lowerBound: Double,
    val upperBound: Double,
    val count: Int,
    val percent: Double,
    val zone: String,
)

@Serializable
data class ZonePercentsDto(
    val veryLow: Double,
    val low: Double,
    val inRange: Double,
    val high: Double,
    val veryHigh: Double,
)

@Serializable
data class GlucoseDistributionResponseDto(
    val buckets: List<GlucoseBucketDto>,
    val zonePercents: ZonePercentsDto,
    val unit: String,
    val totalCount: Int,
    val warnings: List<String> = emptyList(),
)

fun GlucoseDistributionResult.toResponse() = GlucoseDistributionResponseDto(
    buckets = buckets.map {
        GlucoseBucketDto(it.lowerBound, it.upperBound, it.count, it.percent, it.zone)
    },
    zonePercents = ZonePercentsDto(
        veryLow = zonePercents.veryLow,
        low = zonePercents.low,
        inRange = zonePercents.inRange,
        high = zonePercents.high,
        veryHigh = zonePercents.veryHigh,
    ),
    unit = unit,
    totalCount = totalCount,
    warnings = warnings,
)

@Suppress("LongParameterList")
@Serializable
data class CgpResponseDto(
    val tor: Double,
    val varK: Double,
    val hypoIntensity: Double,
    val hyperIntensity: Double,
    val meanGlucose: Double,
    val normTor: Double,
    val normVarK: Double,
    val normHypo: Double,
    val normHyper: Double,
    val normMeanGlucose: Double,
    val refTor: Double,
    val refVarK: Double,
    val refHypo: Double,
    val refHyper: Double,
    val refMeanGlucose: Double,
    val pgr: Double,
    val pgrRisk: String,
    val warnings: List<String> = emptyList(),
)

fun CgpResult.toResponse() = CgpResponseDto(
    tor = tor,
    varK = varK,
    hypoIntensity = hypoIntensity,
    hyperIntensity = hyperIntensity,
    meanGlucose = meanGlucose,
    normTor = normTor,
    normVarK = normVarK,
    normHypo = normHypo,
    normHyper = normHyper,
    normMeanGlucose = normMeanGlucose,
    refTor = refTor,
    refVarK = refVarK,
    refHypo = refHypo,
    refHyper = refHyper,
    refMeanGlucose = refMeanGlucose,
    pgr = pgr,
    pgrRisk = pgrRisk,
    warnings = warnings,
)

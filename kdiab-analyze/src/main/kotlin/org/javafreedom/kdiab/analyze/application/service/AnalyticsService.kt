package org.javafreedom.kdiab.analyze.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.sqrt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.javafreedom.kdiab.analyze.application.port.outbound.MeasuresPort
import org.javafreedom.kdiab.analyze.application.port.outbound.ProfilesPort
import org.javafreedom.kdiab.analyze.application.port.outbound.TreatmentsPort
import org.javafreedom.kdiab.analyze.domain.model.AgpBucketData
import org.javafreedom.kdiab.analyze.domain.model.AgpResult
import org.javafreedom.kdiab.analyze.domain.model.BasalSegment
import org.javafreedom.kdiab.analyze.domain.model.CgpResult
import org.javafreedom.kdiab.analyze.domain.model.DailyStatRow
import org.javafreedom.kdiab.analyze.domain.model.DailyStatsResult
import org.javafreedom.kdiab.analyze.domain.model.DailyTrendDay
import org.javafreedom.kdiab.analyze.domain.model.DailyTrendResult
import org.javafreedom.kdiab.analyze.domain.model.GlucoseBucket
import org.javafreedom.kdiab.analyze.domain.model.GlucoseDistributionResult
import org.javafreedom.kdiab.analyze.domain.model.Hba1cResult
import org.javafreedom.kdiab.analyze.domain.model.HourlyTrendRow
import org.javafreedom.kdiab.analyze.domain.model.ReportSummaryResult
import org.javafreedom.kdiab.analyze.domain.model.TirBreakdown
import org.javafreedom.kdiab.analyze.domain.model.TirResult
import org.javafreedom.kdiab.analyze.domain.model.TirZone
import org.javafreedom.kdiab.analyze.domain.model.UpstreamMeasure
import org.javafreedom.kdiab.analyze.domain.model.UpstreamProfile
import org.javafreedom.kdiab.analyze.domain.model.UpstreamTreatment
import org.javafreedom.kdiab.analyze.domain.model.ZonePercents
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.model.GLUCOSE_CONVERSION_FACTOR
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

private const val CACHE_TTL_MINUTES = 5
// DCCT formula: HbA1c (%) = (mean_glucose_mg_dL + 46.7) / 28.7
// Source: DCCT Research Group, NEJM 1993; https://doi.org/10.1056/NEJM199309303291401
private const val DCCT_ADDEND = 46.7
private const val DCCT_DIVISOR = 28.7

// TIR thresholds in mg/dL — ADA/EASD consensus targets for T1D (ADA Standards of Care 2023)
// International Consensus on TIR (Battelino et al. 2019, doi:10.2337/dci19-0028)
private const val TIR_VERY_LOW = 54.0  // below: Level 2 hypoglycaemia (severe)
private const val TIR_LOW = 70.0       // below: Level 1 hypoglycaemia
private const val TIR_HIGH = 180.0     // above: hyperglycaemia
private const val TIR_VERY_HIGH = 250.0 // high: severe hyperglycaemia

private const val MINUTES_IN_HOUR = 60
private const val BUCKET_SIZE_MINUTES = 5
private const val BUCKET_COUNT = 288  // 1440 minutes/day / 5 minutes/bucket
private const val HOURS_IN_DAY = 24
private const val MAX_TREND_DAYS = 365

// Daily-stats constants
// ADA consensus: very low <54, low 54–70, in-range 70–180, high 180–250, very high >250 mg/dL
private const val DS_VERY_LOW = 54.0
private const val DS_LOW = 70.0
private const val DS_HIGH = 180.0
private const val DS_VERY_HIGH = 250.0
private const val DAILY_STATS_MAX_DAYS = 365
private const val DAILY_STATS_MIN_DAYS_WARN = 14
private const val DAILY_STATS_SUMMARY_DATE = "summary"

// Clinical thresholds for data quality warnings
// < 1 day of 5-min CGM readings (288 = 24h * 12 readings/h)
private const val MIN_READINGS_RELIABLE = 288
// < 14 days of 5-min CGM readings (4032 = 14 * 288)
private const val MIN_READINGS_MEANINGFUL = 4032
// At least half of all 288 five-minute buckets must have at least one reading
private const val MIN_COVERED_BUCKETS = 144
private const val AGP_TENTH_PERCENTILE = 10
private const val AGP_LOWER_QUARTILE = 25
private const val AGP_MEDIAN_PERCENTILE = 50
private const val AGP_UPPER_QUARTILE = 75
private const val AGP_NINETIETH_PERCENTILE = 90
private const val PERCENT_FACTOR = 100.0

// Report summary constants
// Max date range for report summary (365 days)
private const val MAX_REPORT_DAYS = 365
// Minimum recommended date range for report summary (14 days)
private const val MIN_REPORT_DAYS = 14
private const val SECONDS_PER_DAY = 86_400.0
private const val SECONDS_PER_DAY_LONG = 86_400L
// GRI = 3.0×(%<54) + 2.4×(%54–70) + 1.6×(%180–250) + 0.8×(%>250) (Klonoff 2023)
private const val GRI_VERY_LOW_WEIGHT = 3.0
private const val GRI_LOW_WEIGHT = 2.4
private const val GRI_HIGH_WEIGHT = 1.6
private const val GRI_VERY_HIGH_WEIGHT = 0.8
// GRI zone thresholds
private const val GRI_ZONE_A_MAX = 20.0
private const val GRI_ZONE_B_MAX = 40.0
private const val GRI_ZONE_C_MAX = 60.0
private const val GRI_ZONE_D_MAX = 80.0
// Estimated CGM interval in minutes (standard 5-min CGM interval)
private const val CGM_INTERVAL_MINUTES = 5
private const val PGS_REFERENCE_GLUCOSE = 110.0
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_IN_DAY = 24 * MINUTES_PER_HOUR
private const val TEMP_BASAL_DURATION_TO_HOURS = 60.0
private const val HOURS_IN_DAY_D = 24.0

// Trend zone thresholds (percent change from prev hour)
private const val TREND_RISING_FAST = 20.0
private const val TREND_RISING = 10.0
private const val TREND_FALLING = -10.0
private const val TREND_FALLING_FAST = -20.0

// Glucose distribution histogram constants
// mg/dL: 5 mg/dL bins from 0 to 400 (80 buckets: 0–5, 5–10, ..., 395–400)
private const val DIST_MGDL_STEP = 5.0
private const val DIST_MGDL_MAX = 400.0
// mmol/L: 0.3 mmol/L bins from 0.0 to 22.2 (74 buckets)
private const val DIST_MMOL_STEP = 0.3
private const val DIST_MMOL_MAX = 22.2
// Zone thresholds in mg/dL (ADA/EASD consensus on TIR — Battelino et al. 2019)
private const val ZONE_VERY_LOW_UPPER = TIR_VERY_LOW   // < 54 mg/dL
private const val ZONE_LOW_UPPER = TIR_LOW              // 54–<70 mg/dL
private const val ZONE_IN_RANGE_UPPER = TIR_HIGH        // 70–180 mg/dL
private const val ZONE_HIGH_UPPER = TIR_VERY_HIGH       // 180–250 mg/dL; >250 → veryHigh
private const val DECIMAL_SCALE = 10.0  // rounding to 1 decimal place
private const val MAX_DISTRIBUTION_DAYS = 365L

// CGP — Comprehensive Glucose Pentagon constants (Vigersky et al. 2018)
// Reference values for healthy subjects without diabetes
private const val CGP_REF_TOR = 0.0           // min/day
private const val CGP_REF_VARK = 12.5         // %
private const val CGP_REF_HYPO = 0.0
private const val CGP_REF_HYPER = 0.0
private const val CGP_REF_MEAN_GLUCOSE = 100.0 // mg/dL

// Worst-case normalisation denominators (lower-is-better axes)
private const val CGP_WORST_TOR = 1440.0       // min/day (entire day out of range)
private const val CGP_WORST_VARK = 50.0        // %
private const val CGP_WORST_HYPO = 5000.0
private const val CGP_WORST_HYPER = 50000.0
private const val CGP_MEAN_GLUCOSE_OFFSET = 100.0  // reference mean glucose
private const val CGP_WORST_MEAN_GLUCOSE_RANGE = 200.0  // worst = 300 mg/dL

// Hypo/hyper intensity scale factor: per 5-min reading contributing to daily average
// Formula: sum of (delta)^2 × (5/1440) per reading / daysInRange
private const val CGP_INTENSITY_READING_WEIGHT = 5.0 / 1440.0

// CGP thresholds
private const val CGP_HYPO_THRESHOLD = 70.0   // mg/dL
private const val CGP_HYPER_THRESHOLD = 180.0  // mg/dL

// PGR risk category thresholds (geometric mean × 5, scale 0–5)
private const val PGR_RISK_VERY_LOW = 2.0
private const val PGR_RISK_LOW = 3.0
private const val PGR_RISK_MODERATE = 4.0
private const val PGR_RISK_HIGH = 4.5
private const val PGR_SCALE = 5.0
private const val CGP_AXES_COUNT = 5.0

// Minimum readings for meaningful CGP (14 days × 288 readings/day)
private const val CGP_MIN_READINGS = 4032

private data class CgmFetchResult(val readings: List<Double>, val mismatchCount: Int)
private data class MeasuresCacheKey(val userId: String, val from: String, val to: String)
private data class MeasuresCacheEntry(val measures: List<UpstreamMeasure>, val fetchedAt: Instant)

// TooManyFunctions/LargeClass: all functions implement AnalyticsOperation or are private helpers
// directly supporting those implementations — the class cannot be usefully split further.
@Suppress("TooManyFunctions", "LargeClass")
class AnalyticsService(
    private val measuresPort: MeasuresPort,
    private val profilesPort: ProfilesPort,
    private val treatmentsPort: TreatmentsPort,
) : AnalyticsOperation {
    // In-process cache keyed by (userId, from, to). Avoids double-fetching when getHba1c and
    // getAgp are called in the same request burst for the same time window.
    private val measuresCache = ConcurrentHashMap<MeasuresCacheKey, MeasuresCacheEntry>()


    override suspend fun preFetchCgmMeasures(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        correlationId: String,
    ) {
        // Warm the cache; ignore upstream failures — getHba1c / getAgp handle them gracefully.
        runCatching { getMeasuresCached(userId, from, to, authorization, correlationId) }
    }

    override suspend fun getAnalysisThresholds(
        userId: String,
        authorization: String,
        correlationId: String,
    ): Pair<Double, Double> {
        val activeProfile = runCatching {
            profilesPort.getProfiles(userId, authorization, correlationId)
                .firstOrNull { it.status == "ACTIVE" }
        }.getOrNull()
        val tirLow = activeProfile?.analysisLow ?: TIR_LOW
        val tirHigh = activeProfile?.analysisHigh ?: TIR_HIGH
        return Pair(tirLow, tirHigh)
    }

    private suspend fun getMeasuresCached(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        correlationId: String,
    ): List<UpstreamMeasure> {
        val key = MeasuresCacheKey(userId, from, to)
        val now = Clock.System.now()
        measuresCache[key]?.let { entry ->
            if (now - entry.fetchedAt < CACHE_TTL_MINUTES.minutes) {
                logger.debug { "CGM cache hit for userId=$userId from=$from to=$to" }
                return entry.measures
            }
        }
        val fresh = measuresPort.getMeasures(userId, authorization, correlationId, from, to)
        measuresCache[key] = MeasuresCacheEntry(measures = fresh, fetchedAt = now)
        // Evict entries older than the TTL to prevent unbounded growth.
        measuresCache.entries.removeIf { (_, v) -> now - v.fetchedAt >= CACHE_TTL_MINUTES.minutes }
        return fresh
    }

    @Suppress("LongParameterList")
    override suspend fun getHba1c(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
        tirLow: Double,
        tirHigh: Double,
    ): Hba1cResult {
        val fetchResult = runCatching {
            fetchCgmReadings(userId, from, to, authorization, glucoseUnit, correlationId)
        }.getOrElse { e ->
            logger.warn(e) {
                "analytics_service action=getHba1c status=upstream_error userId=$userId — returning empty result"
            }
            null
        }

        if (fetchResult == null) {
            return Hba1cResult(
                hba1c = null,
                meanGlucose = 0.0,
                readingCount = 0,
                tir = TirBreakdown(),
                warnings = listOf("Glucose data is temporarily unavailable. Please try again later."),
            )
        }

        val readings = fetchResult.readings
        val warnings = buildList {
            if (readings.isEmpty()) {
                add("No CGM readings found in the selected timeframe.")
            } else if (readings.size < MIN_READINGS_RELIABLE) {
                add("Fewer than 1 day of CGM readings — HbA1c estimate is unreliable.")
            } else if (readings.size < MIN_READINGS_MEANINGFUL) {
                add("Fewer than 14 days of CGM data — estimate may not reflect long-term glucose control.")
            }
            if (fetchResult.mismatchCount > 0) {
                add(
                    "Unit mismatch detected: ${fetchResult.mismatchCount} readings stored in a different unit " +
                        "than your profile setting. Values may be inaccurate."
                )
            }
        }

        val (hba1c, mean) = if (readings.isEmpty()) {
            null to 0.0
        } else {
            val m = readings.average()
            (m + DCCT_ADDEND) / DCCT_DIVISOR to m
        }
        val tir = if (readings.isEmpty()) TirBreakdown() else computeTir(readings, tirLow, tirHigh)

        return Hba1cResult(
            hba1c = hba1c, meanGlucose = mean, readingCount = readings.size, tir = tir, warnings = warnings
        )
    }

    // UnreachableCode: detekt false positive — `return@forEach` / `return@mapNotNull` guard clauses
    // inside lambdas cause subsequent lambda body lines to be flagged as unreachable; they are not.
    @Suppress("LongParameterList", "UnusedParameter", "UnreachableCode")
    override suspend fun getAgp(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
        tirLow: Double,
        tirHigh: Double,
        timeZone: TimeZone,
    ): AgpResult {
        val allMeasures = try {
            getMeasuresCached(userId, from, to, authorization, correlationId)
        } catch (e: Exception) {
            logger.warn(e) {
                "analytics_service action=getAgp status=upstream_error userId=$userId — returning empty result"
            }
            return AgpResult(
                bucketData = (0 until BUCKET_COUNT).map { bucketIndex ->
                    AgpBucketData(
                        minuteOfDay = bucketIndex * BUCKET_SIZE_MINUTES,
                        p10 = null, p25 = null, median = null, p75 = null, p90 = null, count = 0,
                    )
                },
                totalReadingCount = 0,
                sensorWearDays = 0,
                warnings = listOf("Glucose data is temporarily unavailable. Please try again later."),
            )
        }

        val byBucket = Array(BUCKET_COUNT) { mutableListOf<Double>() }

        allMeasures.forEach { dto ->
            if (dto.type != "CGM") return@forEach
            val t = runCatching { Instant.parse(dto.measuredAt) }.getOrNull() ?: return@forEach
            val sgv = dto.data["value"]?.toString()?.toDoubleOrNull() ?: return@forEach
            val storageUnit = dto.data["unit"]?.toString()?.trim('"') ?: glucoseUnit
            val mgDl = if (storageUnit.lowercase() == "mmol/l") sgv * GLUCOSE_CONVERSION_FACTOR else sgv
            if (mgDl <= 0.0) return@forEach
            val localTime = t.toLocalDateTime(timeZone)
            val localMinuteOfDay = localTime.hour * MINUTES_IN_HOUR + localTime.minute
            val bucketIndex = localMinuteOfDay / BUCKET_SIZE_MINUTES
            byBucket[bucketIndex].add(mgDl)
        }

        val sensorWearDays = allMeasures.mapNotNull { dto ->
            if (dto.type != "CGM") return@mapNotNull null
            runCatching { Instant.parse(dto.measuredAt) }.getOrNull()
                ?.toLocalDateTime(timeZone)?.date
        }.toSet().size

        val bucketData = byBucket.mapIndexed { bucketIndex, values ->
            if (values.isEmpty()) {
                AgpBucketData(
                    minuteOfDay = bucketIndex * BUCKET_SIZE_MINUTES,
                    p10 = null, p25 = null, median = null, p75 = null, p90 = null, count = 0,
                )
            } else {
                values.sort()
                AgpBucketData(
                    minuteOfDay = bucketIndex * BUCKET_SIZE_MINUTES,
                    p10 = percentile(values, AGP_TENTH_PERCENTILE),
                    p25 = percentile(values, AGP_LOWER_QUARTILE),
                    median = percentile(values, AGP_MEDIAN_PERCENTILE),
                    p75 = percentile(values, AGP_UPPER_QUARTILE),
                    p90 = percentile(values, AGP_NINETIETH_PERCENTILE),
                    count = values.size,
                )
            }
        }

        val coveredBuckets = bucketData.count { it.count > 0 }
        val totalReadingCount = bucketData.sumOf { it.count }
        val agpWarnings = buildList {
            if (coveredBuckets < MIN_COVERED_BUCKETS) {
                add(
                    "Only $coveredBuckets of 288 five-minute buckets have CGM data " +
                        "— AGP pattern may not be representative."
                )
            }
        }

        return AgpResult(
            bucketData = bucketData,
            totalReadingCount = totalReadingCount,
            sensorWearDays = sensorWearDays,
            warnings = agpWarnings,
        )
    }

    // UnreachableCode: detekt false positive — `return@forEach` / `return@mapNotNull` guard clauses
    // inside lambdas cause subsequent lambda body lines to be flagged as unreachable; they are not.
    // LongMethod: complex hourly trend aggregation — cannot be split without losing cohesion.
    @Suppress("LongParameterList", "UnreachableCode", "LongMethod")
    override suspend fun getDailyTrend(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
        timeZone: TimeZone,
    ): DailyTrendResult {
        val warnings = mutableListOf<String>()

        // Fetch CGM measures and profiles in parallel; treatments separately (soft failure)
        val (allMeasures, profiles) = try {
            coroutineScope {
                val measuresDeferred = async {
                    getMeasuresCached(userId, from, to, authorization, correlationId)
                }
                val profilesDeferred = async {
                    runCatching {
                        profilesPort.getProfiles(userId, authorization, correlationId)
                    }.getOrElse { e ->
                        logger.warn(e) {
                            "analytics_service action=getDailyTrend status=profiles_error userId=$userId"
                        }
                        emptyList()
                    }
                }
                measuresDeferred.await() to profilesDeferred.await()
            }
        } catch (e: Exception) {
            logger.warn(e) {
                "analytics_service action=getDailyTrend status=upstream_error userId=$userId — returning empty result"
            }
            return DailyTrendResult(
                days = emptyList(),
                warnings = listOf("Glucose data is temporarily unavailable. Please try again later."),
            )
        }

        // Fetch treatments (carbs) — soft failure: missing treatments produce zero carbsG
        val allTreatments = treatmentsPort.let { port ->
            runCatching {
                port.getTreatments(userId, authorization, correlationId, from, to)
            }.getOrElse { e ->
                logger.warn(e) {
                    "analytics_service action=getDailyTrend status=treatments_error " +
                        "userId=$userId — continuing without carbs"
                }
                warnings.add("Carbohydrate data is temporarily unavailable.")
                emptyList()
            }
        }

        // Group CGM readings by (localDate, hour) → list of mg/dL values
        val cgmByDayHour = mutableMapOf<LocalDate, Array<MutableList<Double>>>()
        allMeasures.forEach { dto ->
            if (dto.type != "CGM") return@forEach
            val t = runCatching { Instant.parse(dto.measuredAt) }.getOrNull() ?: return@forEach
            val sgv = dto.data["value"]?.toString()?.toDoubleOrNull() ?: return@forEach
            val storageUnit = dto.data["unit"]?.toString()?.trim('"') ?: glucoseUnit
            val mgDl = if (storageUnit.lowercase() == "mmol/l") sgv * GLUCOSE_CONVERSION_FACTOR else sgv
            if (mgDl <= 0.0) return@forEach
            val ldt = t.toLocalDateTime(timeZone)
            val date = ldt.date
            val hour = ldt.hour
            cgmByDayHour.getOrPut(date) { Array(HOURS_IN_DAY) { mutableListOf() } }[hour].add(mgDl)
        }

        // Group CARBS treatments by (localDate, hour) → sum of carbsG
        val carbsByDayHour = mutableMapOf<LocalDate, Array<Double>>()
        allTreatments.forEach { dto ->
            if (dto.type != "CARBS") return@forEach
            val t = runCatching { Instant.parse(dto.treatedAt) }.getOrNull() ?: return@forEach
            val carbs = dto.data["carbsG"]?.toString()?.toDoubleOrNull()
                ?: dto.data["amount"]?.toString()?.toDoubleOrNull()
                ?: return@forEach
            if (carbs <= 0.0) return@forEach
            val ldt = t.toLocalDateTime(timeZone)
            val date = ldt.date
            val hour = ldt.hour
            val dayArr = carbsByDayHour.getOrPut(date) { Array(HOURS_IN_DAY) { 0.0 } }
            dayArr[hour] += carbs
        }

        // Warn if range exceeds max days
        if (cgmByDayHour.size > MAX_TREND_DAYS) {
            warnings.add(
                "Range exceeds $MAX_TREND_DAYS days — results are truncated to the most recent $MAX_TREND_DAYS days.",
            )
        }

        val sortedDates = cgmByDayHour.keys.sorted().takeLast(MAX_TREND_DAYS)

        val days = sortedDates.map { date ->
            val byHour = cgmByDayHour[date] ?: Array(HOURS_IN_DAY) { mutableListOf() }
            val carbsHour = carbsByDayHour[date]

            // Find the profile active on this date
            val activeProfile = findActiveProfileForDate(profiles, date)

            val hourlyMeans = Array<Double?>(HOURS_IN_DAY) { h ->
                val values = byHour[h]
                if (values.isEmpty()) null else values.average()
            }

            val hours = (0 until HOURS_IN_DAY).map { hour ->
                val meanGlucose = hourlyMeans[hour]
                val prevMean = if (hour == 0) null else hourlyMeans[hour - 1]
                val trendPercent = computeTrendPercent(meanGlucose, prevMean)
                val trendZone = computeTrendZone(trendPercent)
                val zone = computeGlucoseZone(meanGlucose)
                val basalRate = findBasalRateForHour(activeProfile, hour)
                val carbsG = carbsHour?.get(hour) ?: 0.0

                HourlyTrendRow(
                    hour = hour,
                    meanGlucose = meanGlucose,
                    trendPercent = trendPercent,
                    trendZone = trendZone,
                    zone = zone,
                    basalRateIePerH = basalRate,
                    carbsG = carbsG,
                )
            }

            DailyTrendDay(date = date.toString(), hours = hours)
        }

        return DailyTrendResult(days = days, warnings = warnings)
    }

    // UnreachableCode: detekt false positive — `return@mapNotNull null` guard clauses inside lambdas
    // cause subsequent lambda body lines to be flagged as unreachable; they are not.
    @Suppress("LongParameterList", "UnreachableCode")
    private suspend fun fetchCgmReadings(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
    ): CgmFetchResult {
        var mismatchCount = 0
        val readings = getMeasuresCached(userId, from, to, authorization, correlationId)
            .filter { dto -> dto.type == "CGM" }
            .mapNotNull { dto ->
                val sgv = dto.data["value"]?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
                val storageUnit = dto.data["unit"]?.toString()?.trim('"') ?: glucoseUnit
                if (storageUnit != glucoseUnit) mismatchCount++
                val mgDl = if (storageUnit.lowercase() == "mmol/l") sgv * GLUCOSE_CONVERSION_FACTOR else sgv
                if (mgDl <= 0.0) return@mapNotNull null
                mgDl
            }
        return CgmFetchResult(readings = readings, mismatchCount = mismatchCount)
    }

    private fun computeTir(readings: List<Double>, tirLow: Double = TIR_LOW, tirHigh: Double = TIR_HIGH): TirBreakdown {
        var veryLow = 0
        var below = 0
        var inRange = 0
        var above = 0
        var high = 0
        readings.forEach { v ->
            when {
                v < TIR_VERY_LOW -> veryLow++
                v < tirLow -> below++
                v <= tirHigh -> inRange++
                v <= TIR_VERY_HIGH -> above++
                else -> high++
            }
        }
        return TirBreakdown(
            veryLowCount = veryLow,
            belowCount = below,
            inRangeCount = inRange,
            aboveCount = above,
            highCount = high,
            totalCount = readings.size,
        )
    }

    private fun percentile(sorted: List<Double>, p: Int): Double {
        if (sorted.isEmpty()) return 0.0
        val index = (p / PERCENT_FACTOR * (sorted.size - 1))
        val lower = index.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.size - 1)
        val fraction = index - lower
        return sorted[lower] + fraction * (sorted[upper] - sorted[lower])
    }

    private fun computeTrendPercent(meanGlucose: Double?, prevMean: Double?): Double? {
        if (meanGlucose == null || prevMean == null || prevMean == 0.0) return null
        return (meanGlucose - prevMean) / prevMean * PERCENT_FACTOR
    }

    private fun computeTrendZone(trendPercent: Double?): String? {
        if (trendPercent == null) return null
        return when {
            trendPercent >= TREND_RISING_FAST  -> "risingFast"
            trendPercent >= TREND_RISING       -> "rising"
            trendPercent > TREND_FALLING       -> "stable"
            trendPercent > TREND_FALLING_FAST  -> "falling"
            else                               -> "fallingFast"
        }
    }

    private fun computeGlucoseZone(meanGlucose: Double?): String {
        if (meanGlucose == null) return "noData"
        return when {
            meanGlucose < TIR_VERY_LOW   -> "veryHypo"
            meanGlucose < TIR_LOW        -> "hypo"
            meanGlucose <= TIR_HIGH      -> "inRange"
            meanGlucose <= TIR_VERY_HIGH -> "hyper"
            else                         -> "veryHyper"
        }
    }

    // Find the profile that was active on a given date.
    // Uses activatedAt (when ACTIVE state was entered) or validFrom as fallback.
    // Returns the most recently activated profile whose activation date is ≤ the given date.
    // UnreachableCode: return@mapNotNull null guards are falsely flagged as unreachable by detekt.
    @Suppress("UnreachableCode")
    private fun findActiveProfileForDate(profiles: List<UpstreamProfile>, date: LocalDate): UpstreamProfile? =
        profiles
            .mapNotNull { profile ->
                val activationStr = profile.activatedAt ?: profile.validFrom ?: return@mapNotNull null
                val activationDate = runCatching {
                    Instant.parse(activationStr).toLocalDateTime(TimeZone.UTC).date
                }.getOrNull() ?: return@mapNotNull null
                profile to activationDate
            }
            .filter { (_, activationDate) -> activationDate <= date }
            .maxByOrNull { (_, activationDate) -> activationDate }
            ?.first

    // Find the basal rate from the profile's basal schedule for a given clock hour.
    // The schedule is a list of segments with startTime in "HH:MM" format.
    // Returns the rate of the last segment whose startTime ≤ hour:00.
    // UnreachableCode: return@mapNotNull null guards are falsely flagged as unreachable by detekt.
    @Suppress("UnreachableCode", "ReturnCount")
    private fun findBasalRateForHour(profile: UpstreamProfile?, hour: Int): Double? {
        val segments = profile?.basal?.takeIf { it.isNotEmpty() } ?: return null
        // startTime format: "HH:MM"; convert to total minutes so the latest segment before `hour` wins
        val minutesPerHour = MINUTES_PER_HOUR
        val active = segments
            .mapNotNull { seg ->
                val parts = seg.startTime.split(":")
                if (parts.size < 2) return@mapNotNull null
                val h = parts[0].toIntOrNull() ?: return@mapNotNull null
                val m = parts[1].toIntOrNull() ?: return@mapNotNull null
                Triple(h, m, seg.value)
            }
            .filter { (h, _, _) -> h <= hour }
            .maxByOrNull { (h, m, _) -> h * minutesPerHour + m }
        return active?.third
    }

    // UnreachableCode: detekt false positive — `return@forEach` / `return@mapNotNull` guard clauses
    // inside lambdas cause subsequent lambda body lines to be flagged as unreachable; they are not.
    @Suppress("LongParameterList", "UnreachableCode", "LongMethod")
    override suspend fun getDailyStats(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
        timeZone: TimeZone,
    ): DailyStatsResult {
        val allMeasures = try {
            getMeasuresCached(userId, from, to, authorization, correlationId)
        } catch (e: Exception) {
            logger.warn(e) {
                "analytics_service action=getDailyStats status=upstream_error userId=$userId — returning empty result"
            }
            val emptyRow = DailyStatRow(
                date = DAILY_STATS_SUMMARY_DATE, cgmCount = 0,
                veryLowPercent = null, lowPercent = null, inRangePercent = null,
                highPercent = null, veryHighPercent = null,
                p25 = null, median = null, p75 = null, sd = null, eHbA1c = null,
            )
            return DailyStatsResult(
                rows = emptyList(),
                summary = emptyRow,
                warnings = listOf("Glucose data is temporarily unavailable. Please try again later."),
            )
        }

        // Convert CGM readings to (date, mgDl) pairs, bucketed by patient-local date
        val readingsByDate = mutableMapOf<String, MutableList<Double>>()
        allMeasures.forEach { dto ->
            if (dto.type != "CGM") return@forEach
            val t = runCatching { Instant.parse(dto.measuredAt) }.getOrNull() ?: return@forEach
            val sgv = dto.data["value"]?.toString()?.toDoubleOrNull() ?: return@forEach
            val storageUnit = dto.data["unit"]?.toString()?.trim('"') ?: glucoseUnit
            val mgDl = if (storageUnit.lowercase() == "mmol/l") sgv * GLUCOSE_CONVERSION_FACTOR else sgv
            if (mgDl <= 0.0) return@forEach
            val date = t.toLocalDateTime(timeZone).date.toString()
            readingsByDate.getOrPut(date) { mutableListOf() }.add(mgDl)
        }

        // Generate all dates in range (capped at 365), then build rows newest-first
        val allDates = generateDatesInRange(from, to, timeZone)
        val rows = allDates.map { date ->
            val readings = readingsByDate[date]
            if (readings.isNullOrEmpty()) emptyDailyStatRow(date)
            else buildDailyStatRow(date, readings)
        }.reversed() // newest first

        val daysWithReadings = rows.count { it.cgmCount > 0 }
        val warnings = buildList {
            if (daysWithReadings in 1 until DAILY_STATS_MIN_DAYS_WARN) {
                add(
                    "Fewer than $DAILY_STATS_MIN_DAYS_WARN days with CGM readings — " +
                        "summary statistics may not be representative.",
                )
            }
        }

        return DailyStatsResult(
            rows = rows,
            summary = buildSummaryRow(rows),
            warnings = warnings,
        )
    }

    private data class CgmStats(
        val min: Double?, val max: Double?, val mean: Double?, val sd: Double?,
        val gvi: Double?, val eHbA1c: Double?,
    )

    private fun computeCgmStats(readings: List<Double>): CgmStats {
        if (readings.isEmpty()) return CgmStats(null, null, null, null, null, null)
        val mean = readings.average()
        val sd = if (readings.size > 1) sqrt(readings.sumOf { (it - mean) * (it - mean) } / readings.size) else null
        val gvi = if (mean > 0.0 && sd != null) sd / mean else null
        val eHbA1c = (mean + DCCT_ADDEND) / DCCT_DIVISOR
        return CgmStats(readings.min(), readings.max(), mean, sd, gvi, eHbA1c)
    }

    private data class InsulinMetrics(
        val insulinChanges: Int, val avgDaysPerCartridge: Double?,
        val siteChanges: Int, val avgDaysPerSite: Double?,
        val sensorInserts: Int, val avgDaysPerSensor: Double?,
    )

    private fun computeInsulinMetrics(treatments: List<UpstreamTreatment>, daysAnalysed: Int): InsulinMetrics {
        val insulinCount = treatments.count { it.type == "INSULIN_CHANGE" }
        val siteCount = treatments.count { it.type == "SITE_CHANGE" }
        val sensorCount = treatments.count { it.type == "SENSOR_INSERT" }
        return InsulinMetrics(
            insulinChanges = insulinCount,
            avgDaysPerCartridge = if (insulinCount > 0) daysAnalysed.toDouble() / insulinCount else null,
            siteChanges = siteCount,
            avgDaysPerSite = if (siteCount > 0) daysAnalysed.toDouble() / siteCount else null,
            sensorInserts = sensorCount,
            avgDaysPerSensor = if (sensorCount > 0) daysAnalysed.toDouble() / sensorCount else null,
        )
    }

    // UnreachableCode: detekt false positive inside lambda guard clauses.
    @Suppress("LongParameterList", "LongMethod", "UnreachableCode")
    override suspend fun getReportSummary(
        userId: String,
        displayName: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
        timeZone: TimeZone,
    ): ReportSummaryResult {
        val fromInstant = Instant.parse(from)
        val toInstant = Instant.parse(to)
        val rangeDays = (toInstant - fromInstant).inWholeSeconds / SECONDS_PER_DAY
        if (rangeDays > MAX_REPORT_DAYS) {
            throw BusinessValidationException(
                "Report date range must not exceed $MAX_REPORT_DAYS days (requested ${rangeDays.toInt()} days)"
            )
        }
        val daysAnalysed = rangeDays.toInt().coerceAtLeast(1)

        val (cgmReadings, treatments, profiles) = coroutineScope {
            val cgmAsync = async {
                runCatching {
                    fetchCgmReadings(userId, from, to, authorization, glucoseUnit, correlationId)
                }.getOrNull()
            }
            val treatAsync = async {
                runCatching {
                    treatmentsPort.getTreatments(userId, authorization, correlationId, from, to)
                }.getOrElse { emptyList() }
            }
            val profAsync = async {
                runCatching {
                    profilesPort.getProfiles(userId, authorization, correlationId)
                }.getOrElse { emptyList() }
            }
            Triple(cgmAsync.await(), treatAsync.await(), profAsync.await())
        }

        val readings = cgmReadings?.readings ?: emptyList()
        val activeProfile = profiles.firstOrNull { it.status == "ACTIVE" }

        val warnings = buildList {
            if (rangeDays < MIN_REPORT_DAYS) add("lessThan14Days")
            if (readings.isEmpty()) add("No CGM readings found in the selected timeframe.")
        }

        val cgm = computeCgmStats(readings)
        val insulinMetrics = computeInsulinMetrics(treatments, daysAnalysed)

        // TIR profile (uses profile analysisLow/High if available, else standard 70/180)
        val profileTirLow = activeProfile?.analysisLow
        val profileTirHigh = activeProfile?.analysisHigh
        val tirProfile = computeTirResult(
            readings, profileTirLow ?: TIR_LOW, profileTirHigh ?: TIR_HIGH,
            customTirFallback = profileTirLow == null || profileTirHigh == null,
        )
        val tirStandard = computeTirResult(readings, TIR_LOW, TIR_HIGH, customTirFallback = false)

        val gri = if (readings.isNotEmpty()) computeGri(tirStandard) else null
        // PGS (Rodbard 2011): Mean_BG * (%time ≥180 fraction) + (110 – Mean_BG) * (%time ≤70 fraction)
        val pgs = cgm.mean?.let { mean ->
            val highFrac = (tirStandard.high.count + tirStandard.veryHigh.count).toDouble() / readings.size
            val lowFrac = (tirStandard.low.count + tirStandard.veryLow.count).toDouble() / readings.size
            maxOf(0.0, mean * highFrac + (PGS_REFERENCE_GLUCOSE - mean) * lowFrac)
        }

        val bolusTotalIe = treatments
            .filter { it.type in listOf("BOLUS", "CORRECTION_BOLUS", "COMBO_BOLUS") }
            .sumOf { it.data["amount"]?.toString()?.toDoubleOrNull() ?: 0.0 }
        val carbsTotalG = treatments
            .filter { it.type == "CARBS" }
            .sumOf { it.data["amount"]?.toString()?.toDoubleOrNull() ?: 0.0 }
        val basalTotalIe = computeBasalTotalIe(activeProfile?.basal, treatments, fromInstant, toInstant)

        val avgBolusPerDayIe = if (daysAnalysed > 0) bolusTotalIe / daysAnalysed else null
        val avgBasalPerDayIe = if (daysAnalysed > 0) basalTotalIe / daysAnalysed else null
        val avgTotalInsulinPerDayIe = if (avgBolusPerDayIe != null && avgBasalPerDayIe != null) {
            avgBolusPerDayIe + avgBasalPerDayIe
        } else avgBolusPerDayIe ?: avgBasalPerDayIe

        val bolusPercent = if (avgTotalInsulinPerDayIe != null && avgTotalInsulinPerDayIe > 0.0 &&
            avgBolusPerDayIe != null
        ) avgBolusPerDayIe / avgTotalInsulinPerDayIe * PERCENT_FACTOR else null
        val basalPercent = if (avgTotalInsulinPerDayIe != null && avgTotalInsulinPerDayIe > 0.0 &&
            avgBasalPerDayIe != null
        ) avgBasalPerDayIe / avgTotalInsulinPerDayIe * PERCENT_FACTOR else null

        val insulinTypes = profiles.mapNotNull { it.insulinType }.distinct()

        return ReportSummaryResult(
            displayName = displayName,
            daysAnalysed = daysAnalysed,
            cgmReadingCount = readings.size,
            cgmIntervalMinutes = CGM_INTERVAL_MINUTES,
            insulinTypes = insulinTypes,
            insulinChanges = insulinMetrics.insulinChanges,
            avgDaysPerCartridge = insulinMetrics.avgDaysPerCartridge,
            siteChanges = insulinMetrics.siteChanges,
            avgDaysPerSite = insulinMetrics.avgDaysPerSite,
            sensorInserts = insulinMetrics.sensorInserts,
            avgDaysPerSensor = insulinMetrics.avgDaysPerSensor,
            tirProfile = tirProfile,
            tirStandard = tirStandard,
            minGlucose = cgm.min,
            maxGlucose = cgm.max,
            meanGlucose = cgm.mean,
            sd = cgm.sd,
            gvi = cgm.gvi,
            pgs = pgs,
            gri = gri,
            griZone = gri?.let { griZone(it) },
            eHbA1c = cgm.eHbA1c,
            avgCarbsPerDayG = if (daysAnalysed > 0) carbsTotalG / daysAnalysed else null,
            avgBolusPerDayIe = avgBolusPerDayIe,
            bolusPercent = bolusPercent,
            avgBasalPerDayIe = avgBasalPerDayIe,
            basalPercent = basalPercent,
            avgTotalInsulinPerDayIe = avgTotalInsulinPerDayIe,
            warnings = warnings,
        )
    }

    private fun buildDailyStatRow(date: String, readings: List<Double>): DailyStatRow {
        val sorted = readings.sorted()
        val total = sorted.size.toDouble()
        // ADA consensus thresholds (closed upper bounds for in-range and very high):
        // very low: <54, low: 54–<70, in-range: 70–180, high: >180–250, very high: >250
        val veryLow = sorted.count { it < DS_VERY_LOW }
        val low = sorted.count { it >= DS_VERY_LOW && it < DS_LOW }
        val inRange = sorted.count { it >= DS_LOW && it <= DS_HIGH }
        val high = sorted.count { it > DS_HIGH && it <= DS_VERY_HIGH }
        val veryHigh = sorted.count { it > DS_VERY_HIGH }
        val mean = sorted.average()
        val sd = if (sorted.size < 2) 0.0 else {
            sqrt(sorted.sumOf { (it - mean) * (it - mean) } / sorted.size)
        }
        val eHbA1c = (mean + DCCT_ADDEND) / DCCT_DIVISOR
        return DailyStatRow(
            date = date,
            cgmCount = sorted.size,
            veryLowPercent = veryLow / total * PERCENT_FACTOR,
            lowPercent = low / total * PERCENT_FACTOR,
            inRangePercent = inRange / total * PERCENT_FACTOR,
            highPercent = high / total * PERCENT_FACTOR,
            veryHighPercent = veryHigh / total * PERCENT_FACTOR,
            p25 = percentile(sorted, AGP_LOWER_QUARTILE),
            median = percentile(sorted, AGP_MEDIAN_PERCENTILE),
            p75 = percentile(sorted, AGP_UPPER_QUARTILE),
            sd = sd,
            eHbA1c = eHbA1c,
        )
    }

    private fun emptyDailyStatRow(date: String) = DailyStatRow(
        date = date, cgmCount = 0,
        veryLowPercent = null, lowPercent = null, inRangePercent = null,
        highPercent = null, veryHighPercent = null,
        p25 = null, median = null, p75 = null, sd = null, eHbA1c = null,
    )

    @Suppress("ReturnCount")
    private fun buildSummaryRow(rows: List<DailyStatRow>): DailyStatRow {
        val active = rows.filter { it.cgmCount > 0 }
        if (active.isEmpty()) {
            return emptyDailyStatRow(DAILY_STATS_SUMMARY_DATE)
        }
        fun avg(selector: (DailyStatRow) -> Double?) =
            active.mapNotNull(selector).let { if (it.isEmpty()) null else it.average() }
        return DailyStatRow(
            date = DAILY_STATS_SUMMARY_DATE,
            cgmCount = active.sumOf { it.cgmCount },
            veryLowPercent = avg { it.veryLowPercent },
            lowPercent = avg { it.lowPercent },
            inRangePercent = avg { it.inRangePercent },
            highPercent = avg { it.highPercent },
            veryHighPercent = avg { it.veryHighPercent },
            p25 = avg { it.p25 },
            median = avg { it.median },
            p75 = avg { it.p75 },
            sd = avg { it.sd },
            eHbA1c = avg { it.eHbA1c },
        )
    }

    private fun generateDatesInRange(from: String, to: String, timeZone: TimeZone): List<String> {
        val fromInstant = runCatching { Instant.parse(from) }.getOrNull()
        val toInstant = runCatching { Instant.parse(to) }.getOrNull()
        if (fromInstant == null || toInstant == null) return emptyList()
        val startDate = fromInstant.toLocalDateTime(timeZone).date
        val endDate = toInstant.toLocalDateTime(timeZone).date
        val result = mutableListOf<String>()
        var current = startDate
        while (current <= endDate && result.size < DAILY_STATS_MAX_DAYS) {
            result.add(current.toString())
            current = current.plus(DatePeriod(days = 1))
        }
        return result
    }

    private fun computeTirResult(
        readings: List<Double>,
        tirLow: Double,
        tirHigh: Double,
        customTirFallback: Boolean,
    ): TirResult {
        if (readings.isEmpty()) {
            return TirResult(
                veryLow = TirZone(0, 0.0),
                low = TirZone(0, 0.0),
                inRange = TirZone(0, 0.0),
                high = TirZone(0, 0.0),
                veryHigh = TirZone(0, 0.0),
                customTirFallback = customTirFallback,
            )
        }
        var veryLowCount = 0
        var lowCount = 0
        var inRangeCount = 0
        var highCount = 0
        var veryHighCount = 0
        readings.forEach { v ->
            when {
                v < TIR_VERY_LOW -> veryLowCount++
                v < tirLow -> lowCount++
                v <= tirHigh -> inRangeCount++
                v <= TIR_VERY_HIGH -> highCount++
                else -> veryHighCount++
            }
        }
        val total = readings.size.toDouble()
        return TirResult(
            veryLow = TirZone(veryLowCount, veryLowCount / total * PERCENT_FACTOR),
            low = TirZone(lowCount, lowCount / total * PERCENT_FACTOR),
            inRange = TirZone(inRangeCount, inRangeCount / total * PERCENT_FACTOR),
            high = TirZone(highCount, highCount / total * PERCENT_FACTOR),
            veryHigh = TirZone(veryHighCount, veryHighCount / total * PERCENT_FACTOR),
            customTirFallback = customTirFallback,
        )
    }

    // GRI = 3.0×(%<54) + 2.4×(%54–70) + 1.6×(%180–250) + 0.8×(%>250), Klonoff 2023
    private fun computeGri(tir: TirResult): Double {
        return GRI_VERY_LOW_WEIGHT * tir.veryLow.percent +
            GRI_LOW_WEIGHT * tir.low.percent +
            GRI_HIGH_WEIGHT * tir.high.percent +
            GRI_VERY_HIGH_WEIGHT * tir.veryHigh.percent
    }

    private fun griZone(gri: Double): String = when {
        gri <= GRI_ZONE_A_MAX -> "A"
        gri <= GRI_ZONE_B_MAX -> "B"
        gri <= GRI_ZONE_C_MAX -> "C"
        gri <= GRI_ZONE_D_MAX -> "D"
        else -> "E"
    }

    // UnreachableCode: detekt false positive inside lambda guard clauses.
    @Suppress("UnreachableCode")
    private fun computeBasalTotalIe(
        basalSchedule: List<BasalSegment>?,
        treatments: List<UpstreamTreatment>,
        from: Instant,
        to: Instant,
    ): Double {
        // If no basal profile available, sum any BASAL treatment events directly.
        if (basalSchedule.isNullOrEmpty()) {
            return treatments
                .filter { it.type == "BASAL" }
                .sumOf { it.data["amount"]?.toString()?.toDoubleOrNull() ?: 0.0 }
        }

        // Build full-day scheduled basal schedule as list of (startMinute, rate) pairs.
        // Segments are in HH:MM format; convert to minutes from midnight.
        val scheduleMinutes = basalSchedule.mapNotNull { seg ->
            val parts = seg.startTime.split(":")
            if (parts.size != 2) return@mapNotNull null
            val h = parts[0].toIntOrNull() ?: return@mapNotNull null
            val m = parts[1].toIntOrNull() ?: return@mapNotNull null
            Pair(h * MINUTES_PER_HOUR + m, seg.value)
        }.sortedBy { it.first }

        var scheduledTotal = 0.0

        // For each day in the range, sum scheduled basal
        var cursor = from
        while (cursor < to) {
            val dayEnd = minOf(cursor + 1.days, to)
            val daySeconds = (dayEnd - cursor).inWholeSeconds.toDouble()
            // Sum scheduled basal for this day (pro-rated if partial day)
            scheduledTotal += computeScheduledBasalForDuration(scheduleMinutes, daySeconds / SECONDS_PER_DAY)
            cursor = dayEnd
        }

        // Adjust for TEMP_BASAL events: replace scheduled basal with temp rate for the duration.
        val tempBasalAdjustment = treatments
            .filter { it.type == "TEMP_BASAL" }
            .sumOf { t ->
                val treatedAt = runCatching { Instant.parse(t.treatedAt) }.getOrNull() ?: return@sumOf 0.0
                if (treatedAt < from || treatedAt >= to) return@sumOf 0.0
                val durationMinutes = t.data["duration"]?.toString()?.toDoubleOrNull() ?: return@sumOf 0.0
                val tempRate = t.data["rate"]?.toString()?.toDoubleOrNull() ?: return@sumOf 0.0
                val durationHours = durationMinutes / TEMP_BASAL_DURATION_TO_HOURS
                // Scheduled basal for the same duration (to subtract)
                val scheduledForDuration =
                    computeScheduledBasalForDuration(scheduleMinutes, durationHours / HOURS_IN_DAY_D)
                // Temp replaces scheduled for the duration
                (tempRate - scheduledForDuration / (durationHours / HOURS_IN_DAY_D)) * durationHours
            }

        return (scheduledTotal + tempBasalAdjustment).coerceAtLeast(0.0)
    }

    private fun computeScheduledBasalForDuration(
        scheduleMinutes: List<Pair<Int, Double>>,
        fractionOfDay: Double,
    ): Double {
        // Compute total insulin in one full day from the schedule, then scale by fractionOfDay.
        var totalForDay = 0.0
        for (i in scheduleMinutes.indices) {
            val startMin = scheduleMinutes[i].first
            val rate = scheduleMinutes[i].second
            val endMin = if (i + 1 < scheduleMinutes.size) scheduleMinutes[i + 1].first else MINUTES_IN_DAY
            val durationHours = (endMin - startMin).toDouble() / MINUTES_PER_HOUR
            totalForDay += rate * durationHours
        }
        return totalForDay * fractionOfDay
    }

    // UnreachableCode: detekt false positive — `return@mapNotNull null` guard clauses inside lambdas
    // cause subsequent lambda body lines to be flagged as unreachable; they are not.
    @Suppress("LongParameterList", "UnreachableCode")
    override suspend fun getGlucoseDistribution(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
    ): GlucoseDistributionResult {
        val isMmol = glucoseUnit.lowercase() == "mmol/l"
        val step = if (isMmol) DIST_MMOL_STEP else DIST_MGDL_STEP
        val maxBound = if (isMmol) DIST_MMOL_MAX else DIST_MGDL_MAX

        val fromInstant = runCatching { Instant.parse(from) }.getOrNull()
        val toInstant = runCatching { Instant.parse(to) }.getOrNull()
        val rangeWarnings = buildList {
            if (fromInstant != null && toInstant != null) {
                val rangeSeconds = (toInstant - fromInstant).inWholeSeconds
                if (rangeSeconds > MAX_DISTRIBUTION_DAYS * SECONDS_PER_DAY_LONG) {
                    add("Date range exceeds 365 days — results may be slow to compute.")
                }
            }
        }

        val allMeasures = try {
            getMeasuresCached(userId, from, to, authorization, correlationId)
        } catch (e: Exception) {
            logger.warn(e) {
                "analytics_service action=getGlucoseDistribution status=upstream_error userId=$userId"
            }
            return emptyDistribution(step, maxBound, glucoseUnit, isMmol,
                listOf("Glucose data is temporarily unavailable. Please try again later."))
        }

        val values = allMeasures.mapNotNull { dto ->
            if (dto.type != "CGM") return@mapNotNull null
            val sgv = dto.data["value"]?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
            val storageUnit = dto.data["unit"]?.toString()?.trim('"') ?: glucoseUnit
            val mgDl = if (storageUnit.lowercase() == "mmol/l") sgv * GLUCOSE_CONVERSION_FACTOR else sgv
            if (mgDl <= 0.0) return@mapNotNull null
            if (isMmol) mgDl / GLUCOSE_CONVERSION_FACTOR else mgDl
        }

        val totalCount = values.size
        val buckets = buildBuckets(values, step, maxBound, totalCount, isMmol)
        val zonePercents = computeZonePercents(buckets)
        val warnings = rangeWarnings + buildList {
            if (totalCount == 0) add("No CGM readings found in the selected timeframe.")
        }

        return GlucoseDistributionResult(
            buckets = buckets,
            zonePercents = zonePercents,
            unit = glucoseUnit,
            totalCount = totalCount,
            warnings = warnings,
        )
    }

    private fun buildBuckets(
        values: List<Double>,
        step: Double,
        maxBound: Double,
        totalCount: Int,
        isMmol: Boolean,
    ): List<GlucoseBucket> {
        val counts = mutableMapOf<Int, Int>()
        values.forEach { v ->
            val idx = (v / step).toInt().coerceAtMost(((maxBound / step) - 1).toInt())
            counts[idx] = (counts[idx] ?: 0) + 1
        }
        val numBuckets = (maxBound / step).toInt()
        return (0 until numBuckets).map { idx ->
            val lower = idx * step
            val upper = (idx + 1) * step
            val count = counts[idx] ?: 0
            val percent = if (totalCount == 0) 0.0
                else Math.round(count.toDouble() / totalCount * PERCENT_FACTOR * DECIMAL_SCALE) / DECIMAL_SCALE
            GlucoseBucket(
                lowerBound = lower,
                upperBound = upper,
                count = count,
                percent = percent,
                zone = zoneForBucket(lower, upper, isMmol),
            )
        }
    }

    private fun zoneForBucket(lowerBound: Double, upperBound: Double, isMmol: Boolean): String {
        val factor = if (isMmol) GLUCOSE_CONVERSION_FACTOR else 1.0
        val veryLowUpper = ZONE_VERY_LOW_UPPER / factor
        val lowUpper = ZONE_LOW_UPPER / factor
        val inRangeUpper = ZONE_IN_RANGE_UPPER / factor
        val highUpper = ZONE_HIGH_UPPER / factor
        return when {
            upperBound <= veryLowUpper -> "veryLow"
            lowerBound < lowUpper -> "low"
            lowerBound < inRangeUpper -> "inRange"
            lowerBound < highUpper -> "high"
            else -> "veryHigh"
        }
    }

    private fun computeZonePercents(buckets: List<GlucoseBucket>): ZonePercents {
        var veryLow = 0.0
        var low = 0.0
        var inRange = 0.0
        var high = 0.0
        var veryHigh = 0.0
        buckets.forEach { b ->
            when (b.zone) {
                "veryLow" -> veryLow += b.percent
                "low" -> low += b.percent
                "inRange" -> inRange += b.percent
                "high" -> high += b.percent
                "veryHigh" -> veryHigh += b.percent
            }
        }
        return ZonePercents(
            veryLow = Math.round(veryLow * DECIMAL_SCALE) / DECIMAL_SCALE,
            low = Math.round(low * DECIMAL_SCALE) / DECIMAL_SCALE,
            inRange = Math.round(inRange * DECIMAL_SCALE) / DECIMAL_SCALE,
            high = Math.round(high * DECIMAL_SCALE) / DECIMAL_SCALE,
            veryHigh = Math.round(veryHigh * DECIMAL_SCALE) / DECIMAL_SCALE,
        )
    }

    private fun emptyDistribution(
        step: Double,
        maxBound: Double,
        glucoseUnit: String,
        isMmol: Boolean,
        warnings: List<String>,
    ): GlucoseDistributionResult {
        val numBuckets = (maxBound / step).toInt()
        val emptyBuckets = (0 until numBuckets).map { idx ->
            val lower = idx * step
            val upper = (idx + 1) * step
            GlucoseBucket(lower, upper, 0, 0.0, zoneForBucket(lower, upper, isMmol))
        }
        return GlucoseDistributionResult(
            buckets = emptyBuckets,
            zonePercents = ZonePercents(0.0, 0.0, 0.0, 0.0, 0.0),
            unit = glucoseUnit,
            totalCount = 0,
            warnings = warnings,
        )
    }

    // UnreachableCode: detekt false positive inside lambda guard clauses.
    // ReturnCount: three early-return guards (upstream error, empty readings, and final result) are
    // all necessary for clarity and cannot be restructured without sacrificing readability.
    @Suppress("LongParameterList", "UnreachableCode", "ReturnCount")
    override suspend fun getCgp(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
    ): CgpResult {
        val allMeasures = try {
            getMeasuresCached(userId, from, to, authorization, correlationId)
        } catch (e: Exception) {
            logger.warn(e) {
                "analytics_service action=getCgp status=upstream_error userId=$userId — returning empty result"
            }
            return emptyCgpResult(listOf("Glucose data is temporarily unavailable. Please try again later."))
        }

        val readings = allMeasures.mapNotNull { dto ->
            if (dto.type != "CGM") return@mapNotNull null
            val sgv = dto.data["value"]?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
            val storageUnit = dto.data["unit"]?.toString()?.trim('"') ?: glucoseUnit
            val mgDl = if (storageUnit.lowercase() == "mmol/l") sgv * GLUCOSE_CONVERSION_FACTOR else sgv
            if (mgDl <= 0.0) return@mapNotNull null
            mgDl
        }

        val warnings = buildList {
            if (readings.isEmpty()) {
                add("No CGM readings found in the selected timeframe.")
            } else if (readings.size < CGP_MIN_READINGS) {
                add("Fewer than 14 days of CGM data — CGP values may not be representative.")
            }
        }

        if (readings.isEmpty()) return emptyCgpResult(warnings)

        // Compute days in range for intensity normalisation
        val fromInstant = runCatching { kotlin.time.Instant.parse(from) }.getOrNull()
        val toInstant = runCatching { kotlin.time.Instant.parse(to) }.getOrNull()
        val daysInRange = if (fromInstant != null && toInstant != null) {
            val secs = (toInstant - fromInstant).inWholeSeconds.toDouble()
            (secs / SECONDS_PER_DAY).coerceAtLeast(1.0)
        } else 1.0

        val mean = readings.average()
        val sd = if (readings.size > 1) {
            sqrt(readings.sumOf { (it - mean) * (it - mean) } / readings.size)
        } else 0.0

        // ToR: minutes/day where glucose < 70 or > 180
        val outOfRangeCount = readings.count { it < CGP_HYPO_THRESHOLD || it > CGP_HYPER_THRESHOLD }
        val tor = outOfRangeCount.toDouble() * CGP_INTENSITY_READING_WEIGHT * MINUTES_PER_HOUR * HOURS_IN_DAY_D

        // VarK: coefficient of variation (%)
        val varK = if (mean > 0.0) sd / mean * PERCENT_FACTOR else 0.0

        // Hypo intensity: sum of (70-g)^2 × (5/1440) per reading / days
        val hypoIntensity = readings
            .filter { it < CGP_HYPO_THRESHOLD }
            .sumOf { (CGP_HYPO_THRESHOLD - it) * (CGP_HYPO_THRESHOLD - it) * CGP_INTENSITY_READING_WEIGHT } /
            daysInRange

        // Hyper intensity: sum of (g-180)^2 × (5/1440) per reading / days
        val hyperIntensity = readings
            .filter { it > CGP_HYPER_THRESHOLD }
            .sumOf {
                (it - CGP_HYPER_THRESHOLD) * (it - CGP_HYPER_THRESHOLD) * CGP_INTENSITY_READING_WEIGHT
            } / daysInRange

        // Normalise all axes (0=worst, 1=healthy reference)
        val normTor = 1.0 - (tor / CGP_WORST_TOR).coerceIn(0.0, 1.0)
        val normVarK = 1.0 - (varK / CGP_WORST_VARK).coerceIn(0.0, 1.0)
        val normHypo = 1.0 - (hypoIntensity / CGP_WORST_HYPO).coerceIn(0.0, 1.0)
        val normHyper = 1.0 - (hyperIntensity / CGP_WORST_HYPER).coerceIn(0.0, 1.0)
        val normMeanGlucose = 1.0 -
            ((mean - CGP_MEAN_GLUCOSE_OFFSET) / CGP_WORST_MEAN_GLUCOSE_RANGE).coerceIn(0.0, 1.0)

        // PGR score: geometric mean of the 5 normalised values × 5
        val product = normTor * normVarK * normHypo * normHyper * normMeanGlucose
        val pgr = Math.pow(product, 1.0 / CGP_AXES_COUNT) * PGR_SCALE

        return CgpResult(
            tor = tor,
            varK = varK,
            hypoIntensity = hypoIntensity,
            hyperIntensity = hyperIntensity,
            meanGlucose = mean,
            normTor = normTor,
            normVarK = normVarK,
            normHypo = normHypo,
            normHyper = normHyper,
            normMeanGlucose = normMeanGlucose,
            refTor = CGP_REF_TOR,
            refVarK = CGP_REF_VARK,
            refHypo = CGP_REF_HYPO,
            refHyper = CGP_REF_HYPER,
            refMeanGlucose = CGP_REF_MEAN_GLUCOSE,
            pgr = pgr,
            pgrRisk = pgrRisk(pgr),
            warnings = warnings,
        )
    }

    private fun emptyCgpResult(warnings: List<String>): CgpResult = CgpResult(
        tor = 0.0, varK = 0.0, hypoIntensity = 0.0, hyperIntensity = 0.0, meanGlucose = 0.0,
        normTor = 0.0, normVarK = 0.0, normHypo = 0.0, normHyper = 0.0, normMeanGlucose = 0.0,
        refTor = CGP_REF_TOR, refVarK = CGP_REF_VARK, refHypo = CGP_REF_HYPO,
        refHyper = CGP_REF_HYPER, refMeanGlucose = CGP_REF_MEAN_GLUCOSE,
        pgr = 0.0, pgrRisk = "very_low", warnings = warnings,
    )

    private fun pgrRisk(pgr: Double): String = when {
        pgr <= PGR_RISK_VERY_LOW -> "very_low"
        pgr <= PGR_RISK_LOW      -> "low"
        pgr <= PGR_RISK_MODERATE -> "moderate"
        pgr <= PGR_RISK_HIGH     -> "high"
        else                      -> "very_high"
    }
}

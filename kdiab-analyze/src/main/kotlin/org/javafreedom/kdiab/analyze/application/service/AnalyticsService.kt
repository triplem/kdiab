package org.javafreedom.kdiab.analyze.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.javafreedom.kdiab.analyze.application.port.outbound.MeasuresPort
import org.javafreedom.kdiab.analyze.application.port.outbound.ProfilesPort
import org.javafreedom.kdiab.analyze.domain.model.UpstreamMeasure
import org.javafreedom.kdiab.analyze.domain.model.AgpBucketData
import org.javafreedom.kdiab.analyze.domain.model.AgpResult
import org.javafreedom.kdiab.analyze.domain.model.DailyStatRow
import org.javafreedom.kdiab.analyze.domain.model.DailyStatsResult
import org.javafreedom.kdiab.analyze.domain.model.Hba1cResult
import org.javafreedom.kdiab.analyze.domain.model.TirBreakdown
import org.javafreedom.kdiab.common.domain.model.GLUCOSE_CONVERSION_FACTOR
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.sqrt

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

private data class CgmFetchResult(val readings: List<Double>, val mismatchCount: Int)
private data class MeasuresCacheKey(val userId: String, val from: String, val to: String)
private data class MeasuresCacheEntry(val measures: List<UpstreamMeasure>, val fetchedAt: Instant)

class AnalyticsService(
    private val measuresPort: MeasuresPort,
    private val profilesPort: ProfilesPort,
) : AnalyticsOperation {
    // In-process cache keyed by (userId, from, to). Avoids double-fetching when getHba1c and
    // getAgp are called in the same request burst for the same time window.
    private val measuresCache = ConcurrentHashMap<MeasuresCacheKey, MeasuresCacheEntry>()


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

    // UnreachableCode: detekt false positive — guard clauses inside lambdas flagged as unreachable.
    @Suppress("LongParameterList", "UnreachableCode")
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
}

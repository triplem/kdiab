package org.javafreedom.kdiab.analyze.application.service

import org.javafreedom.kdiab.analyze.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.analyze.domain.model.AgpHourlyData
import org.javafreedom.kdiab.analyze.domain.model.AgpResult
import org.javafreedom.kdiab.analyze.domain.model.Hba1cResult
import org.javafreedom.kdiab.analyze.domain.model.TirBreakdown
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// DCCT formula: HbA1c (%) = (mean_glucose_mg_dL + 46.7) / 28.7
// Source: DCCT Research Group, NEJM 1993; https://doi.org/10.1056/NEJM199309303291401
private const val DCCT_ADDEND = 46.7
private const val DCCT_DIVISOR = 28.7
// Conversion factor: 1 mmol/L = 18.0182 mg/dL (molecular weight of glucose = 180.16 g/mol)
private const val MMOL_TO_MGDL = 18.0

// TIR thresholds in mg/dL — ADA/EASD consensus targets for T1D (ADA Standards of Care 2023)
private const val TIR_LOW = 70.0       // below: hypoglycaemia
private const val TIR_HIGH = 180.0     // above: hyperglycaemia
private const val TIR_VERY_HIGH = 250.0 // high: severe hyperglycaemia

private const val HOURS_IN_DAY = 24

// Clinical thresholds for data quality warnings
// < 1 day of 5-min CGM readings (288 = 24h * 12 readings/h)
private const val MIN_READINGS_RELIABLE = 288
// < 14 days of 5-min CGM readings (4032 = 14 * 288)
private const val MIN_READINGS_MEANINGFUL = 4032
// At least half of all 24 UTC hours must have at least one reading
private const val MIN_COVERED_HOURS = 12

private const val P10 = 10
private const val P25 = 25
private const val P50 = 50
private const val P75 = 75
private const val P90 = 90
private const val PERCENT_FACTOR = 100.0

class AnalyticsService(
    private val measuresClient: MeasuresClient,
) {
    @Suppress("LongParameterList")
    suspend fun getHba1c(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
    ): Hba1cResult {
        val readings = fetchCgmReadings(userId, from, to, authorization, glucoseUnit, correlationId)

        val warnings = buildList {
            if (readings.isEmpty()) {
                add("No CGM readings found in the selected timeframe.")
            } else if (readings.size < MIN_READINGS_RELIABLE) {
                add("Fewer than 1 day of CGM readings — HbA1c estimate is unreliable.")
            } else if (readings.size < MIN_READINGS_MEANINGFUL) {
                add("Fewer than 14 days of CGM data — estimate may not reflect long-term glucose control.")
            }
        }

        if (readings.isEmpty()) {
            return Hba1cResult(
                hba1c = null, meanGlucose = 0.0, readingCount = 0, tir = TirBreakdown(), warnings = warnings
            )
        }

        val mean = readings.average()
        val hba1c = (mean + DCCT_ADDEND) / DCCT_DIVISOR
        val tir = computeTir(readings)

        return Hba1cResult(
            hba1c = hba1c, meanGlucose = mean, readingCount = readings.size, tir = tir, warnings = warnings
        )
    }

    @Suppress("LongParameterList")
    suspend fun getAgp(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
    ): AgpResult {
        val allMeasures = measuresClient.getMeasures(userId, authorization, correlationId, from, to)

        val byHour = Array(HOURS_IN_DAY) { mutableListOf<Double>() }

        allMeasures.forEach { dto ->
            if (dto.type != "CGM") return@forEach
            val t = runCatching { Instant.parse(dto.measuredAt) }.getOrNull() ?: return@forEach
            val sgv = dto.data["value"]?.toString()?.toDoubleOrNull() ?: return@forEach
            val mgDl = if (glucoseUnit == "mmol/L") sgv * MMOL_TO_MGDL else sgv
            val hour = t.toLocalDateTime(TimeZone.UTC).hour
            byHour[hour].add(mgDl)
        }

        val hourlyData = byHour.mapIndexed { hour, values ->
            if (values.isEmpty()) {
                AgpHourlyData(hour = hour, p10 = null, p25 = null, median = null, p75 = null, p90 = null, count = 0)
            } else {
                values.sort()
                AgpHourlyData(
                    hour = hour,
                    p10 = percentile(values, P10),
                    p25 = percentile(values, P25),
                    median = percentile(values, P50),
                    p75 = percentile(values, P75),
                    p90 = percentile(values, P90),
                    count = values.size,
                )
            }
        }

        val coveredHours = hourlyData.count { it.count > 0 }
        val agpWarnings = buildList {
            if (coveredHours < MIN_COVERED_HOURS) {
                add("Only $coveredHours of 24 hours have CGM data — AGP pattern may not be representative.")
            }
        }

        return AgpResult(hourlyData = hourlyData, warnings = agpWarnings)
    }

    @Suppress("LongParameterList")
    private suspend fun fetchCgmReadings(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        glucoseUnit: String,
        correlationId: String,
    ): List<Double> {
        return measuresClient.getMeasures(userId, authorization, correlationId, from, to)
            .filter { dto -> dto.type == "CGM" }
            .mapNotNull { dto ->
                val sgv = dto.data["value"]?.toString()?.toDoubleOrNull() ?: return@mapNotNull null
                if (glucoseUnit == "mmol/L") sgv * MMOL_TO_MGDL else sgv
            }
    }

    private fun computeTir(readings: List<Double>): TirBreakdown {
        var below = 0
        var inRange = 0
        var above = 0
        var high = 0
        readings.forEach { v ->
            when {
                v < TIR_LOW -> below++
                v <= TIR_HIGH -> inRange++
                v <= TIR_VERY_HIGH -> above++
                else -> high++
            }
        }
        return TirBreakdown(
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
}

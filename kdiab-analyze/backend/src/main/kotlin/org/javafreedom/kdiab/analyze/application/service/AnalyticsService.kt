package org.javafreedom.kdiab.analyze.application.service

import org.javafreedom.kdiab.analyze.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.analyze.domain.model.AgpHourlyData
import org.javafreedom.kdiab.analyze.domain.model.AgpResult
import org.javafreedom.kdiab.analyze.domain.model.Hba1cResult
import org.javafreedom.kdiab.analyze.domain.model.TirBreakdown
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val DCCT_ADDEND = 46.7
private const val DCCT_DIVISOR = 28.7
private const val MMOL_TO_MGDL = 18.0

private const val TIR_LOW = 70.0
private const val TIR_HIGH = 180.0
private const val TIR_VERY_HIGH = 250.0

private const val HOURS_IN_DAY = 24

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
        if (readings.isEmpty()) {
            return Hba1cResult(hba1c = null, meanGlucose = 0.0, readingCount = 0, tir = TirBreakdown())
        }

        val mean = readings.average()
        val hba1c = (mean + DCCT_ADDEND) / DCCT_DIVISOR
        val tir = computeTir(readings)

        return Hba1cResult(hba1c = hba1c, meanGlucose = mean, readingCount = readings.size, tir = tir)
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
        val fromInstant = Instant.parse(from)
        val toInstant = Instant.parse(to)

        val allMeasures = measuresClient.getMeasures(userId, authorization, correlationId)

        val byHour = Array(HOURS_IN_DAY) { mutableListOf<Double>() }

        allMeasures.forEach { dto ->
            if (dto.type != "CGM") return@forEach
            val t = runCatching { Instant.parse(dto.measuredAt) }.getOrNull() ?: return@forEach
            if (t < fromInstant || t > toInstant) return@forEach
            val sgv = dto.data["value"]?.toString()?.toDoubleOrNull() ?: return@forEach
            val mgDl = if (glucoseUnit == "mmol/L") sgv * MMOL_TO_MGDL else sgv
            val hour = t.toLocalDateTime(TimeZone.UTC).hour
            byHour[hour].add(mgDl)
        }

        val hourlyData = byHour.mapIndexed { hour, values ->
            if (values.isEmpty()) {
                AgpHourlyData(hour = hour, p10 = 0.0, p25 = 0.0, median = 0.0, p75 = 0.0, p90 = 0.0, count = 0)
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

        return AgpResult(hourlyData = hourlyData)
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
        val fromInstant = Instant.parse(from)
        val toInstant = Instant.parse(to)

        return measuresClient.getMeasures(userId, authorization, correlationId)
            .filter { dto ->
                if (dto.type != "CGM") return@filter false
                val t = runCatching { Instant.parse(dto.measuredAt) }.getOrNull() ?: return@filter false
                t >= fromInstant && t <= toInstant
            }
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

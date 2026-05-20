package org.javafreedom.kdiab.analyze.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.javafreedom.kdiab.analyze.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.analyze.api.upstream.treatments.models.TreatmentType
import org.javafreedom.kdiab.analyze.application.port.outbound.TreatmentsPort
import org.javafreedom.kdiab.analyze.domain.model.DeviceUsageResult

private val logger = KotlinLogging.logger {}

private const val SECONDS_PER_DAY = 86_400.0

class DeviceUsageService(
    private val treatmentsPort: TreatmentsPort,
) : DeviceUsageOperation {
    override suspend fun compute(
        userId: String,
        days: Int,
        authorization: String,
        correlationId: String,
    ): DeviceUsageResult {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val from = (now - days.days).toString()
        val to = now.toString()

        val deviceTypes = listOf(
            TreatmentType.SENSOR_INSERT,
            TreatmentType.SITE_CHANGE,
            TreatmentType.INSULIN_CHANGE,
            TreatmentType.PUMP_BATTERY_CHANGE,
        )

        val treatmentsByType = coroutineScope {
            deviceTypes.associateWith { type ->
                async {
                    runCatching {
                        treatmentsPort.getTreatmentsByType(userId, authorization, correlationId, type, from, to)
                    }.getOrElse { ex ->
                        logger.warn(ex) { "Failed to fetch $type treatments for userId=$userId — treating as empty" }
                        emptyList()
                    }
                }
            }.mapValues { it.value.await() }
        }

        return DeviceUsageResult(
            userId = userId,
            avgSensorDays     = avg(treatmentsByType[TreatmentType.SENSOR_INSERT] ?: emptyList()),
            stddevSensorDays  = stddev(treatmentsByType[TreatmentType.SENSOR_INSERT] ?: emptyList()),
            avgCatheterDays   = avg(treatmentsByType[TreatmentType.SITE_CHANGE] ?: emptyList()),
            stddevCatheterDays = stddev(treatmentsByType[TreatmentType.SITE_CHANGE] ?: emptyList()),
            avgReservoirDays  = avg(treatmentsByType[TreatmentType.INSULIN_CHANGE] ?: emptyList()),
            stddevReservoirDays = stddev(treatmentsByType[TreatmentType.INSULIN_CHANGE] ?: emptyList()),
            avgBatteryDays    = avg(treatmentsByType[TreatmentType.PUMP_BATTERY_CHANGE] ?: emptyList()),
            stddevBatteryDays = stddev(treatmentsByType[TreatmentType.PUMP_BATTERY_CHANGE] ?: emptyList()),
        )
    }

    private fun durations(treatments: List<TreatmentResponse>): List<Double> {
        if (treatments.size < 2) return emptyList()
        val sorted = treatments
            .mapNotNull { runCatching { Instant.parse(it.treatedAt) }.getOrNull() }
            .sorted()
        return sorted.zipWithNext { a, b ->
            (b - a).inWholeSeconds / SECONDS_PER_DAY
        }
    }

    private fun avg(treatments: List<TreatmentResponse>): Double? {
        val d = durations(treatments)
        return if (d.isEmpty()) null else d.average()
    }

    private fun stddev(treatments: List<TreatmentResponse>): Double? {
        val d = durations(treatments)
        if (d.isEmpty()) return null
        val mean = d.average()
        return sqrt(d.sumOf { (it - mean) * (it - mean) } / d.size)
    }
}

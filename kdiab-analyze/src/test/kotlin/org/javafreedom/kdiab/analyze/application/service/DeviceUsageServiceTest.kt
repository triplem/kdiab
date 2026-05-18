package org.javafreedom.kdiab.analyze.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.javafreedom.kdiab.analyze.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.analyze.api.upstream.treatments.models.TreatmentType
import org.javafreedom.kdiab.analyze.application.port.outbound.TreatmentsPort
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceUsageServiceTest {

    private val treatmentsPort = mockk<TreatmentsPort>()
    private val service = DeviceUsageService(treatmentsPort)

    private val userId = "user-1"
    private val auth = "Bearer token"
    private val correlationId = "corr-1"

    private fun treatment(type: TreatmentType, treatedAt: String) = TreatmentResponse(
        id = "t-${treatedAt.hashCode()}",
        userId = userId,
        treatedAt = treatedAt,
        createdAt = treatedAt,
        type = type,
        data = buildJsonObject {},
        status = TreatmentResponse.Status.ACTIVE,
    )

    private fun stubType(type: TreatmentType, treatments: List<TreatmentResponse>) {
        coEvery {
            treatmentsPort.getTreatmentsByType(userId, auth, correlationId, type, any(), any())
        } returns treatments
    }

    private fun stubAllEmpty() {
        TreatmentType.entries.forEach { type ->
            coEvery {
                treatmentsPort.getTreatmentsByType(userId, auth, correlationId, type, any(), any())
            } returns emptyList()
        }
    }

    @Test
    fun `returns null averages when no events exist`() = runTest {
        stubAllEmpty()

        val result = service.compute(userId, 90, auth, correlationId)

        assertNull(result.avgSensorDays)
        assertNull(result.stddevSensorDays)
        assertNull(result.avgCatheterDays)
        assertNull(result.stddevCatheterDays)
        assertNull(result.avgReservoirDays)
        assertNull(result.stddevReservoirDays)
        assertNull(result.avgBatteryDays)
        assertNull(result.stddevBatteryDays)
    }

    @Test
    fun `returns null averages when only one event exists`() = runTest {
        stubAllEmpty()
        stubType(TreatmentType.SENSOR_INSERT, listOf(
            treatment(TreatmentType.SENSOR_INSERT, "2026-04-01T00:00:00Z"),
        ))

        val result = service.compute(userId, 90, auth, correlationId)

        assertNull(result.avgSensorDays)
        assertNull(result.stddevSensorDays)
    }

    @Test
    fun `returns correct avg for two sensor events 14 days apart`() = runTest {
        stubAllEmpty()
        stubType(TreatmentType.SENSOR_INSERT, listOf(
            treatment(TreatmentType.SENSOR_INSERT, "2026-04-01T00:00:00Z"),
            treatment(TreatmentType.SENSOR_INSERT, "2026-04-15T00:00:00Z"),
        ))

        val result = service.compute(userId, 90, auth, correlationId)

        assertNotNull(result.avgSensorDays)
        assertTrue(abs(result.avgSensorDays - 14.0) < 0.01, "Expected ~14 days, got ${result.avgSensorDays}")
        assertNotNull(result.stddevSensorDays)
        // stddev of single value is 0
        assertTrue(abs(result.stddevSensorDays) < 0.01, "Expected stddev=0 for single duration")
    }

    @Test
    fun `returns correct avg and stddev for multiple events`() = runTest {
        stubAllEmpty()
        // 3 events: gaps of 7, 14 days → avg=10.5, stddev=sqrt(((7-10.5)^2+(14-10.5)^2)/2)=3.5
        stubType(TreatmentType.SITE_CHANGE, listOf(
            treatment(TreatmentType.SITE_CHANGE, "2026-01-01T00:00:00Z"),
            treatment(TreatmentType.SITE_CHANGE, "2026-01-08T00:00:00Z"),
            treatment(TreatmentType.SITE_CHANGE, "2026-01-22T00:00:00Z"),
        ))

        val result = service.compute(userId, 90, auth, correlationId)

        assertNotNull(result.avgCatheterDays)
        assertTrue(abs(result.avgCatheterDays - 10.5) < 0.01, "Expected avg=10.5, got ${result.avgCatheterDays}")
        assertNotNull(result.stddevCatheterDays)
        assertTrue(abs(result.stddevCatheterDays - 3.5) < 0.01, "Expected stddev=3.5, got ${result.stddevCatheterDays}")
    }

    @Test
    fun `battery events produce avg and stddev`() = runTest {
        stubAllEmpty()
        stubType(TreatmentType.PUMP_BATTERY_CHANGE, listOf(
            treatment(TreatmentType.PUMP_BATTERY_CHANGE, "2026-03-01T00:00:00Z"),
            treatment(TreatmentType.PUMP_BATTERY_CHANGE, "2026-03-22T00:00:00Z"),
        ))

        val result = service.compute(userId, 90, auth, correlationId)

        assertNotNull(result.avgBatteryDays)
        assertTrue(abs(result.avgBatteryDays - 21.0) < 0.01, "Expected ~21 days, got ${result.avgBatteryDays}")
        assertNull(result.avgSensorDays)
    }

    @Test
    fun `reservoir events produce correct averages`() = runTest {
        stubAllEmpty()
        stubType(TreatmentType.INSULIN_CHANGE, listOf(
            treatment(TreatmentType.INSULIN_CHANGE, "2026-02-01T00:00:00Z"),
            treatment(TreatmentType.INSULIN_CHANGE, "2026-02-04T00:00:00Z"),
        ))

        val result = service.compute(userId, 90, auth, correlationId)

        assertNotNull(result.avgReservoirDays)
        assertTrue(abs(result.avgReservoirDays - 3.0) < 0.01, "Expected ~3 days, got ${result.avgReservoirDays}")
    }

    @Test
    fun `userId is propagated to result`() = runTest {
        stubAllEmpty()

        val result = service.compute(userId, 90, auth, correlationId)

        assertTrue(result.userId == userId)
    }
}

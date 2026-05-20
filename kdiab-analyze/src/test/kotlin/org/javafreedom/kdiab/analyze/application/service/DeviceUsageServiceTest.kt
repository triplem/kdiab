package org.javafreedom.kdiab.analyze.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.javafreedom.kdiab.analyze.application.port.outbound.TreatmentsPort
import org.javafreedom.kdiab.analyze.domain.model.UpstreamTreatment
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

    private fun treatment(type: String, treatedAt: String) = UpstreamTreatment(
        id = "t-${treatedAt.hashCode()}",
        userId = userId,
        treatedAt = treatedAt,
        type = type,
        data = buildJsonObject {},
        notes = null,
    )

    private fun stubType(type: String, treatments: List<UpstreamTreatment>) {
        coEvery {
            treatmentsPort.getTreatmentsByType(userId, auth, correlationId, type, any(), any())
        } returns treatments
    }

    private fun stubAllEmpty() {
        listOf("SENSOR_INSERT", "SITE_CHANGE", "INSULIN_CHANGE", "PUMP_BATTERY_CHANGE").forEach { type ->
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
        stubType("SENSOR_INSERT", listOf(
            treatment("SENSOR_INSERT", "2026-04-01T00:00:00Z"),
        ))

        val result = service.compute(userId, 90, auth, correlationId)

        assertNull(result.avgSensorDays)
        assertNull(result.stddevSensorDays)
    }

    @Test
    fun `returns correct avg for two sensor events 14 days apart`() = runTest {
        stubAllEmpty()
        stubType("SENSOR_INSERT", listOf(
            treatment("SENSOR_INSERT", "2026-04-01T00:00:00Z"),
            treatment("SENSOR_INSERT", "2026-04-15T00:00:00Z"),
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
        stubType("SITE_CHANGE", listOf(
            treatment("SITE_CHANGE", "2026-01-01T00:00:00Z"),
            treatment("SITE_CHANGE", "2026-01-08T00:00:00Z"),
            treatment("SITE_CHANGE", "2026-01-22T00:00:00Z"),
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
        stubType("PUMP_BATTERY_CHANGE", listOf(
            treatment("PUMP_BATTERY_CHANGE", "2026-03-01T00:00:00Z"),
            treatment("PUMP_BATTERY_CHANGE", "2026-03-22T00:00:00Z"),
        ))

        val result = service.compute(userId, 90, auth, correlationId)

        assertNotNull(result.avgBatteryDays)
        assertTrue(abs(result.avgBatteryDays - 21.0) < 0.01, "Expected ~21 days, got ${result.avgBatteryDays}")
        assertNull(result.avgSensorDays)
    }

    @Test
    fun `reservoir events produce correct averages`() = runTest {
        stubAllEmpty()
        stubType("INSULIN_CHANGE", listOf(
            treatment("INSULIN_CHANGE", "2026-02-01T00:00:00Z"),
            treatment("INSULIN_CHANGE", "2026-02-04T00:00:00Z"),
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

    @Test
    fun `upstream failure for one type does not prevent other types from being computed`() = runTest {
        stubAllEmpty()
        coEvery {
            treatmentsPort.getTreatmentsByType(userId, auth, correlationId, "SENSOR_INSERT", any(), any())
        } throws RuntimeException("upstream down")
        stubType("SITE_CHANGE", listOf(
            treatment("SITE_CHANGE", "2026-03-01T00:00:00Z"),
            treatment("SITE_CHANGE", "2026-03-08T00:00:00Z"),
        ))

        val result = service.compute(userId, 90, auth, correlationId)

        assertNull(result.avgSensorDays, "Failed type should yield null avg")
        assertNotNull(result.avgCatheterDays, "Successful type should still compute avg")
    }

    @Test
    fun `all four device types are fetched in a single compute call`() = runTest {
        val calledTypes = mutableListOf<String>()
        listOf("SENSOR_INSERT", "SITE_CHANGE", "INSULIN_CHANGE", "PUMP_BATTERY_CHANGE").forEach { type ->
            coEvery {
                treatmentsPort.getTreatmentsByType(userId, auth, correlationId, type, any(), any())
            } answers {
                calledTypes += type
                emptyList()
            }
        }

        service.compute(userId, 90, auth, correlationId)

        val expectedTypes = setOf("SENSOR_INSERT", "SITE_CHANGE", "INSULIN_CHANGE", "PUMP_BATTERY_CHANGE")
        assertTrue(calledTypes.toSet() == expectedTypes, "All four device types must be fetched")
    }
}

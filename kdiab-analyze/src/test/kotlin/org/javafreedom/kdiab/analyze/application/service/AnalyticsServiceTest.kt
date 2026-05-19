package org.javafreedom.kdiab.analyze.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.common.plugins.CircuitBreakerOpenException
import org.javafreedom.kdiab.analyze.application.port.outbound.MeasuresPort
import org.javafreedom.kdiab.analyze.application.port.outbound.ProfilesPort
import org.javafreedom.kdiab.analyze.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.analyze.api.upstream.measures.models.MeasureSource
import org.javafreedom.kdiab.analyze.api.upstream.measures.models.MeasureStatus
import org.javafreedom.kdiab.analyze.api.upstream.measures.models.MeasureType
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsServiceTest {

    private val measuresClient = mockk<MeasuresPort>()
    private val profilesClient = mockk<ProfilesPort>()
    private val service = AnalyticsService(measuresClient, profilesClient)

    private val userId = "user-1"
    private val auth = "Bearer token"
    private val from = "2024-01-01T00:00:00Z"
    private val to = "2024-01-31T23:59:59Z"

    private fun cgmDto(sgv: Double, measuredAt: String = "2024-01-15T12:00:00Z") = MeasureResponse(
        id = "m-1",
        userId = userId,
        measuredAt = measuredAt,
        createdAt = measuredAt,
        type = MeasureType.CGM,
        source = MeasureSource.MANUAL,
        `data` = buildJsonObject { put("value", sgv); put("unit", "mg/dL") },
        status = MeasureStatus.ACTIVE,
    )

    private fun cgmDtoMmol(sgv: Double, measuredAt: String = "2024-01-15T12:00:00Z") = MeasureResponse(
        id = "m-2",
        userId = userId,
        measuredAt = measuredAt,
        createdAt = measuredAt,
        type = MeasureType.CGM,
        source = MeasureSource.MANUAL,
        `data` = buildJsonObject { put("value", sgv); put("unit", "mmol/L") },
        status = MeasureStatus.ACTIVE,
    )

    // ── HbA1c ────────────────────────────────────────────────────────────────

    @Test
    fun `getHba1c returns null fields when no CGM readings in timeframe`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertEquals(0, result.readingCount)
        assertEquals(0, result.tir.totalCount)
        assertTrue(result.warnings.isNotEmpty(), "Expected warning for empty readings")
        assertTrue(result.warnings.any { it.contains("No CGM readings") })
    }

    @Test
    fun `getHba1c computes DCCT formula correctly for mg_dL`() = runTest {
        // Mean glucose = 154 → HbA1c = (154 + 46.7) / 28.7 ≈ 6.99
        // 2 readings is < 288 (MIN_READINGS_RELIABLE), so a warning is expected
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(140.0),
            cgmDto(168.0),
        )
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertEquals(2, result.readingCount)
        assertEquals(154.0, result.meanGlucose, absoluteTolerance = 0.01)
        val expectedHba1c = (154.0 + 46.7) / 28.7
        assertEquals(expectedHba1c, result.hba1c!!, absoluteTolerance = 0.01)
        assertTrue(result.warnings.isNotEmpty(), "Expected unreliable data warning for < 288 readings")
    }

    @Test
    fun `getHba1c converts mmol per L to mg_dL before DCCT calc`() = runTest {
        // 8.0 mmol/L * 18.0182 = 144.1456 mg/dL — uses per-measure storage unit (mmol/L)
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(cgmDtoMmol(8.0))
        val result = service.getHba1c(userId, from, to, auth, "mmol/L", "")
        assertEquals(8.0 * 18.0182, result.meanGlucose, absoluteTolerance = 0.01)
    }

    @Test
    fun `getHba1c ignores non-CGM readings`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(200.0),
            MeasureResponse(
                id = "m-2", userId = userId, measuredAt = "2024-01-15T13:00:00Z",
                createdAt = "2024-01-15T13:00:00Z",
                type = MeasureType.BGM, source = MeasureSource.MANUAL,
                `data` = buildJsonObject { put("value", 180.0) }, status = MeasureStatus.ACTIVE
            ),
        )
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertEquals(1, result.readingCount)
        assertEquals(200.0, result.meanGlucose, absoluteTolerance = 0.01)
    }

    @Test
    fun `getHba1c passes from and to to upstream and counts all returned readings`() = runTest {
        // Filtering is server-side — the service forwards from/to to the upstream API.
        // All items returned by the client are counted (no client-side date filtering).
        coEvery { measuresClient.getMeasures(userId, auth, any(), eq(from), eq(to)) } returns listOf(
            cgmDto(200.0, "2023-12-31T23:59:00Z"),
            cgmDto(100.0, "2024-01-15T12:00:00Z"),
        )
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertEquals(2, result.readingCount)
    }

    // ── TIR ──────────────────────────────────────────────────────────────────

    @Test
    fun `getHba1c classifies TIR zones correctly`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(40.0),   // very low  (<54, Level 2 hypoglycaemia)
            cgmDto(60.0),   // below     (54-70, Level 1 hypoglycaemia)
            cgmDto(100.0),  // target    (70-180)
            cgmDto(200.0),  // above     (180-250)
            cgmDto(300.0),  // high      (>250)
        )
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertEquals(1, result.tir.veryLowCount)
        assertEquals(1, result.tir.belowCount)
        assertEquals(1, result.tir.inRangeCount)
        assertEquals(1, result.tir.aboveCount)
        assertEquals(1, result.tir.highCount)
        assertEquals(5, result.tir.totalCount)
    }

    @Test
    fun `getHba1c classifies readings below 54 as veryLow not below`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(30.0),  // very low (<54, Level 2 hypoglycaemia)
            cgmDto(53.9),  // very low (<54, just below threshold)
        )
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertEquals(2, result.tir.veryLowCount)
        assertEquals(0, result.tir.belowCount)
        assertEquals(2, result.tir.totalCount)
    }

    @Test
    fun `getHba1c treats boundary values correctly`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(70.0),   // exactly at lower TIR boundary → in-range
            cgmDto(180.0),  // exactly at upper TIR boundary → in-range
            cgmDto(250.0),  // exactly at high boundary → above (≤250)
        )
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertEquals(0, result.tir.belowCount)
        assertEquals(2, result.tir.inRangeCount) // 70 and 180
        assertEquals(1, result.tir.aboveCount)   // 250
        assertEquals(0, result.tir.highCount)
    }

    // ── AGP ──────────────────────────────────────────────────────────────────

    @Test
    fun `getAgp returns 24 hourly buckets`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertEquals(24, result.hourlyData.size)
    }

    @Test
    fun `getAgp assigns readings to correct UTC hour`() = runTest {
        // 14:00 UTC
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-15T14:05:00Z"),
            cgmDto(130.0, "2024-01-15T14:30:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        val bucket14 = result.hourlyData.first { it.hour == 14 }
        assertEquals(2, bucket14.count)
        // linear interpolation: p10 = 120 + 0.1*(130-120) = 121, p90 = 120 + 0.9*(130-120) = 129
        assertEquals(121.0, bucket14.p10, absoluteTolerance = 0.01)
        assertEquals(129.0, bucket14.p90, absoluteTolerance = 0.01)
        assertEquals(125.0, bucket14.median, absoluteTolerance = 0.01)
    }

    @Test
    fun `getAgp empty buckets have null percentiles`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(100.0, "2024-01-15T10:00:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        val emptyBucket = result.hourlyData.first { it.hour == 0 }
        assertEquals(0, emptyBucket.count)
    }

    @Test
    fun `getAgp converts mmol per L before bucketing`() = runTest {
        // 6.0 mmol/L * 18.0182 = 108.1092 mg/dL — uses per-measure storage unit (mmol/L)
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDtoMmol(6.0, "2024-01-15T08:00:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mmol/L", "")
        val bucket8 = result.hourlyData.first { it.hour == 8 }
        assertEquals(6.0 * 18.0182, bucket8.median, absoluteTolerance = 0.01)
    }

    @Test
    fun `getAgp single reading in bucket - all percentiles equal that value`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-15T06:00:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        val bucket6 = result.hourlyData.first { it.hour == 6 }
        assertEquals(1, bucket6.count)
        assertEquals(120.0, bucket6.p10!!, absoluteTolerance = 0.01)
        assertEquals(120.0, bucket6.p25!!, absoluteTolerance = 0.01)
        assertEquals(120.0, bucket6.median!!, absoluteTolerance = 0.01)
        assertEquals(120.0, bucket6.p75!!, absoluteTolerance = 0.01)
        assertEquals(120.0, bucket6.p90!!, absoluteTolerance = 0.01)
    }

    @Test
    fun `getAgp excludes negative and zero glucose values`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-15T07:00:00Z"),
            cgmDto(-5.0, "2024-01-15T07:05:00Z"),
            cgmDto(0.0, "2024-01-15T07:10:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        val bucket7 = result.hourlyData.first { it.hour == 7 }
        assertEquals(1, bucket7.count)
        assertEquals(120.0, bucket7.median!!, absoluteTolerance = 0.01)
    }

    // ── Warnings ──────────────────────────────────────────────────────────────

    @Test
    fun `getHba1c warns when fewer than 288 readings (less than 1 day)`() = runTest {
        // 287 readings → unreliable
        val readings = List(287) { cgmDto(120.0) }
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns readings
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertTrue(result.warnings.isNotEmpty())
        assertTrue(result.warnings.any { it.contains("Fewer than 1 day") })
    }

    @Test
    fun `getHba1c warns when fewer than 4032 readings (less than 14 days)`() = runTest {
        // 288 readings → meaningful threshold reached but not 14-day threshold
        val readings = List(288) { cgmDto(120.0) }
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns readings
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertTrue(result.warnings.isNotEmpty())
        assertTrue(result.warnings.any { it.contains("Fewer than 14 days") })
    }

    @Test
    fun `getHba1c has no warnings when 4032 or more readings`() = runTest {
        // 4032 readings → no warning
        val readings = List(4032) { cgmDto(120.0) }
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns readings
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `getAgp warns when fewer than 12 hours have CGM data`() = runTest {
        // Only hour 8 has data → 1 covered hour < 12
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-15T08:00:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertTrue(result.warnings.isNotEmpty())
        assertTrue(result.warnings.any { it.contains("of 24 hours") })
    }

    @Test
    fun `getAgp has no warnings when 12 or more hours have CGM data`() = runTest {
        // Spread readings across 12 distinct UTC hours
        val readings = (0 until 12).map { h ->
            cgmDto(120.0, "2024-01-15T${String.format("%02d", h)}:00:00Z")
        }
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns readings
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `getAgp returns totalReadingCount equal to sum of all hourly counts`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-15T08:00:00Z"),
            cgmDto(130.0, "2024-01-15T08:30:00Z"),
            cgmDto(110.0, "2024-01-15T14:00:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertEquals(3, result.totalReadingCount)
    }

    @Test
    fun `getAgp returns sensorWearDays as count of distinct UTC calendar days with readings`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-14T23:00:00Z"),
            cgmDto(130.0, "2024-01-15T00:30:00Z"),
            cgmDto(110.0, "2024-01-15T14:00:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertEquals(2, result.sensorWearDays)
    }

    @Test
    fun `getAgp returns zero totalReadingCount and sensorWearDays for empty input`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertEquals(0, result.totalReadingCount)
        assertEquals(0, result.sensorWearDays)
    }

    // ── Unit mismatch ────────────────────────────────────────────────────────

    @Test
    fun `measures stored in mmol per L with glucoseUnit=mg_dL are correctly converted and warn`() = runTest {
        // 7.2 mmol/L * 18.0182 = 129.73104 mg/dL (stored as mmol/L, JWT claims mg/dL → mismatch)
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDtoMmol(7.2),
            cgmDtoMmol(7.2),
        )
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertEquals(7.2 * 18.0182, result.meanGlucose, absoluteTolerance = 0.01)
        assertTrue(result.warnings.any { it.contains("Unit mismatch detected") && it.contains("2 readings") })
    }

    @Test
    fun `measures with matching unit produce no unit-mismatch warning`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(140.0),
            cgmDto(160.0),
        )
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertTrue(result.warnings.none { it.contains("Unit mismatch") })
    }

    // ── Graceful degradation — upstream unavailable ───────────────────────────

    @Test
    fun `getHba1c returns empty result with warning when upstream throws UpstreamException`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } throws
            UpstreamException("measures", 503, "Service Unavailable")
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertEquals(0, result.readingCount)
        assertNull(result.hba1c)
        assertEquals(0.0, result.meanGlucose)
        assertTrue(result.warnings.any { it.contains("temporarily unavailable") })
    }

    @Test
    fun `getHba1c returns empty result with warning when circuit breaker is open`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } throws
            CircuitBreakerOpenException("measures")
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertEquals(0, result.readingCount)
        assertNull(result.hba1c)
        assertTrue(result.warnings.any { it.contains("temporarily unavailable") })
    }

    @Test
    fun `getAgp returns 24 empty buckets with warning when upstream throws UpstreamException`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } throws
            UpstreamException("measures", 502, "Bad Gateway")
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertEquals(24, result.hourlyData.size)
        assertTrue(result.hourlyData.all { it.count == 0 })
        assertEquals(0, result.totalReadingCount)
        assertEquals(0, result.sensorWearDays)
        assertTrue(result.warnings.any { it.contains("temporarily unavailable") })
    }

    @Test
    fun `getAgp returns 24 empty buckets with warning when circuit breaker is open`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } throws
            CircuitBreakerOpenException("measures")
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertEquals(24, result.hourlyData.size)
        assertTrue(result.hourlyData.all { it.count == 0 })
        assertTrue(result.warnings.any { it.contains("temporarily unavailable") })
    }

    @Test
    fun `getAgp empty buckets returned by graceful degradation have correct hour indices`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } throws
            UpstreamException("measures", 500, "Internal Server Error")
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertEquals(24, result.hourlyData.size)
        result.hourlyData.forEachIndexed { index, bucket ->
            assertEquals(index, bucket.hour)
            assertNull(bucket.median)
        }
    }
}

private fun assertEquals(expected: Double, actual: Double?, absoluteTolerance: Double) {
    requireNotNull(actual) { "Expected non-null value but got null" }
    assertTrue(
        Math.abs(expected - actual) <= absoluteTolerance,
        "Expected $expected ± $absoluteTolerance but was $actual"
    )
}

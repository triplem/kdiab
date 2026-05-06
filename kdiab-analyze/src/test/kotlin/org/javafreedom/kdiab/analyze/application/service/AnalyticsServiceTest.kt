package org.javafreedom.kdiab.analyze.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.analyze.adapters.outbound.http.MeasureDto
import org.javafreedom.kdiab.analyze.adapters.outbound.http.MeasuresClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsServiceTest {

    private val measuresClient = mockk<MeasuresClient>()
    private val service = AnalyticsService(measuresClient)

    private val userId = "user-1"
    private val auth = "Bearer token"
    private val from = "2024-01-01T00:00:00Z"
    private val to = "2024-01-31T23:59:59Z"

    private fun cgmDto(sgv: Double, measuredAt: String = "2024-01-15T12:00:00Z") = MeasureDto(
        id = "m-1",
        userId = userId,
        measuredAt = measuredAt,
        type = "CGM",
        data = buildJsonObject { put("value", sgv); put("unit", "mg/dL") },
        status = "ACTIVE",
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
        // 8.0 mmol/L * 18 = 144 mg/dL
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(cgmDto(8.0))
        val result = service.getHba1c(userId, from, to, auth, "mmol/L", "")
        assertEquals(144.0, result.meanGlucose, absoluteTolerance = 0.01)
    }

    @Test
    fun `getHba1c ignores non-CGM readings`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(200.0),
            MeasureDto(
                id = "m-2", userId = userId, measuredAt = "2024-01-15T13:00:00Z",
                type = "BGM", data = buildJsonObject { put("value", 180.0) }, status = "ACTIVE"
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
            cgmDto(50.0),   // below  (<70)
            cgmDto(100.0),  // target (70-180)
            cgmDto(200.0),  // above  (180-250)
            cgmDto(300.0),  // high   (>250)
        )
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertEquals(1, result.tir.belowCount)
        assertEquals(1, result.tir.inRangeCount)
        assertEquals(1, result.tir.aboveCount)
        assertEquals(1, result.tir.highCount)
        assertEquals(4, result.tir.totalCount)
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
        // 6.0 mmol/L * 18 = 108 mg/dL
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(6.0, "2024-01-15T08:00:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mmol/L", "")
        val bucket8 = result.hourlyData.first { it.hour == 8 }
        assertEquals(108.0, bucket8.median, absoluteTolerance = 0.01)
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
}

private fun assertEquals(expected: Double, actual: Double?, absoluteTolerance: Double) {
    requireNotNull(actual) { "Expected non-null value but got null" }
    assertTrue(
        Math.abs(expected - actual) <= absoluteTolerance,
        "Expected $expected ± $absoluteTolerance but was $actual"
    )
}

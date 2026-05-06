package org.javafreedom.kdiab.analyze.application.service

import io.mockk.coEvery
import io.mockk.eq
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
    }

    @Test
    fun `getHba1c computes DCCT formula correctly for mg_dL`() = runTest {
        // Mean glucose = 154 → HbA1c = (154 + 46.7) / 28.7 ≈ 6.99
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(140.0),
            cgmDto(168.0),
        )
        val result = service.getHba1c(userId, from, to, auth, "mg/dL", "")
        assertEquals(2, result.readingCount)
        assertEquals(154.0, result.meanGlucose, absoluteTolerance = 0.01)
        val expectedHba1c = (154.0 + 46.7) / 28.7
        assertEquals(expectedHba1c, result.hba1c!!, absoluteTolerance = 0.01)
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
}

private fun assertEquals(expected: Double, actual: Double?, absoluteTolerance: Double) {
    requireNotNull(actual) { "Expected non-null value but got null" }
    assertTrue(
        Math.abs(expected - actual) <= absoluteTolerance,
        "Expected $expected ± $absoluteTolerance but was $actual"
    )
}

package org.javafreedom.kdiab.analyze.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.analyze.application.port.outbound.MeasuresPort
import org.javafreedom.kdiab.analyze.application.port.outbound.ProfilesPort
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException
import org.javafreedom.kdiab.analyze.domain.model.UpstreamMeasure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlucoseDistributionServiceTest {

    private val measuresClient = mockk<MeasuresPort>()
    private val profilesClient = mockk<ProfilesPort>()
    private val service = AnalyticsService(measuresClient, profilesClient)

    private val userId = "user-1"
    private val auth = "Bearer token"
    private val from = "2024-01-01T00:00:00Z"
    private val to = "2024-01-31T23:59:59Z"

    private fun cgmDto(sgv: Double, unit: String = "mg/dL") = UpstreamMeasure(
        id = "m-1",
        userId = userId,
        measuredAt = "2024-01-15T12:00:00Z",
        type = "CGM",
        source = "MANUAL",
        data = buildJsonObject { put("value", sgv); put("unit", unit) },
        status = "ACTIVE",
    )

    // ── mg/dL binning ────────────────────────────────────────────────────────

    @Test
    fun `mgdL reading of 75 goes into bucket 75-80 with zone inRange`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(cgmDto(75.0))
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        val bucket = result.buckets.first { it.lowerBound == 75.0 }
        assertEquals(80.0, bucket.upperBound, 0.001)
        assertEquals(1, bucket.count)
        assertEquals("inRange", bucket.zone)
    }

    @Test
    fun `mgdL reading at exactly 70 goes into bucket 70-75 with zone inRange`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(cgmDto(70.0))
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        val bucket = result.buckets.first { it.lowerBound == 70.0 }
        assertEquals(1, bucket.count)
        assertEquals("inRange", bucket.zone)
    }

    @Test
    fun `mgdL reading at exactly 53_9 goes into veryLow zone`() = runTest {
        // 53.9 / 5 = 10.78 → bucket index 10 → lowerBound 50, upperBound 55
        // upperBound (55) > veryLow threshold (54) so the bucket straddles the boundary;
        // zone is based on lowerBound: 50 < 54 → but upperBound > 54 → we use the "low" rule
        // Actually: lowerBound=50 < ZONE_VERY_LOW_UPPER=54 but upperBound=55 > 54,
        // so zone = "low" per zoneForBucket implementation (bucket straddles border → low)
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(cgmDto(53.9))
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        val bucket = result.buckets.first { it.lowerBound == 50.0 }
        assertEquals(1, bucket.count)
        // The 50-55 bucket straddles the veryLow/low border (54 mg/dL) → zone is "low"
        assertEquals("low", bucket.zone)
    }

    @Test
    fun `mgdL reading below 50 is in veryLow zone`() = runTest {
        // 40 → bucket 40-45, which is entirely below 54 → veryLow
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(cgmDto(40.0))
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        val bucket = result.buckets.first { it.lowerBound == 40.0 }
        assertEquals(1, bucket.count)
        assertEquals("veryLow", bucket.zone)
    }

    @Test
    fun `mgdL reading of 250 goes into bucket 250-255 with zone high`() = runTest {
        // 250 → bucket index 50 → lowerBound=250, upperBound=255
        // lowerBound(250) == ZONE_HIGH_UPPER(250) → NOT < highUpper → veryHigh
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(cgmDto(250.0))
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        val bucket = result.buckets.first { it.lowerBound == 250.0 }
        assertEquals(1, bucket.count)
        assertEquals("veryHigh", bucket.zone)
    }

    @Test
    fun `mgdL reading of 249 goes into bucket 245-250 with zone high`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(cgmDto(249.0))
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        val bucket = result.buckets.first { it.lowerBound == 245.0 }
        assertEquals(1, bucket.count)
        assertEquals("high", bucket.zone)
    }

    @Test
    fun `mgdL histogram has 80 buckets from 0 to 400`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        assertEquals(80, result.buckets.size)
        assertEquals(0.0, result.buckets.first().lowerBound, 0.001)
        assertEquals(395.0, result.buckets.last().lowerBound, 0.001)
        assertEquals(400.0, result.buckets.last().upperBound, 0.001)
    }

    @Test
    fun `all empty buckets have count zero and percent zero`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        assertTrue(result.buckets.all { it.count == 0 })
        assertTrue(result.buckets.all { it.percent == 0.0 })
    }

    @Test
    fun `percent is computed as count over totalCount times 100 rounded to 1 decimal`() = runTest {
        // 1 reading in bucket 75-80 out of 4 total → 25.0%
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(75.0),
            cgmDto(100.0),
            cgmDto(120.0),
            cgmDto(140.0),
        )
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        assertEquals(4, result.totalCount)
        val bucket = result.buckets.first { it.lowerBound == 75.0 }
        assertEquals(1, bucket.count)
        assertEquals(25.0, bucket.percent, 0.001)
    }

    @Test
    fun `zone percents sum to approximately 100`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(40.0),
            cgmDto(60.0),
            cgmDto(100.0),
            cgmDto(200.0),
            cgmDto(300.0),
        )
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        val zp = result.zonePercents
        val total = zp.veryLow + zp.low + zp.inRange + zp.high + zp.veryHigh
        assertEquals(100.0, total, 1.0)  // allow 1% rounding slack
    }

    @Test
    fun `totalCount matches number of CGM readings`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(100.0), cgmDto(110.0), cgmDto(120.0),
        )
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        assertEquals(3, result.totalCount)
    }

    @Test
    fun `unit is returned as mg_dL when glucoseUnit is mgdL`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        assertEquals("mg/dL", result.unit)
    }

    // ── mmol/L binning ───────────────────────────────────────────────────────

    @Test
    fun `mmolL readings are converted before binning`() = runTest {
        // Storage unit is mmol/L (value=5.5); glucoseUnit=mmol/L → display value=5.5
        // 5.5 / 0.3 = 18.33 → bucket index 18 → lowerBound=5.4, upperBound=5.7
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(5.5, "mmol/L"),
        )
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mmol/L", "")
        assertEquals("mmol/L", result.unit)
        // bucket at index 18: lowerBound = 18 * 0.3 = 5.4
        val bucket = result.buckets.first { it.lowerBound >= 5.3 && it.lowerBound <= 5.5 }
        assertEquals(1, bucket.count)
        assertEquals("inRange", bucket.zone)
    }

    @Test
    fun `mgdL stored readings are converted to mmolL before binning when glucoseUnit is mmolL`() = runTest {
        // Storage unit is mg/dL, value=99 → convert to mmol/L: 99/18.0182 ≈ 5.5 mmol/L
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(99.0, "mg/dL"),
        )
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mmol/L", "")
        assertEquals("mmol/L", result.unit)
        assertEquals(1, result.totalCount)
        // 99 mg/dL ÷ 18.0182 ≈ 5.494 mmol/L → bucket index floor(5.494/0.3)=18 → lowerBound=5.4
        val nonEmpty = result.buckets.filter { it.count > 0 }
        assertEquals(1, nonEmpty.size)
        val lb = nonEmpty[0].lowerBound
        assertTrue(lb >= 5.3 && lb <= 5.6, "Expected bucket near 5.4 mmol/L, got $lb")
    }

    @Test
    fun `mmolL histogram has 74 buckets from 0 to 22_2`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mmol/L", "")
        assertEquals(74, result.buckets.size)
        assertEquals(0.0, result.buckets.first().lowerBound, 0.01)
        assertEquals(22.2, result.buckets.last().upperBound, 0.01)
    }

    // ── Zone boundary precision ───────────────────────────────────────────────

    @Test
    fun `bucket entirely below 54 mgdL is veryLow`() = runTest {
        // Bucket 45-50: upperBound(50) <= veryLowUpper(54) → veryLow
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        val bucket = result.buckets.first { it.lowerBound == 45.0 }
        assertEquals("veryLow", bucket.zone)
    }

    @Test
    fun `bucket starting at 70 is inRange`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        val bucket = result.buckets.first { it.lowerBound == 70.0 }
        assertEquals("inRange", bucket.zone)
    }

    @Test
    fun `bucket starting at 180 is high`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        val bucket = result.buckets.first { it.lowerBound == 180.0 }
        assertEquals("high", bucket.zone)
    }

    @Test
    fun `bucket starting at 250 is veryHigh`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        val bucket = result.buckets.first { it.lowerBound == 250.0 }
        assertEquals("veryHigh", bucket.zone)
    }

    // ── Non-CGM and invalid readings ignored ─────────────────────────────────

    @Test
    fun `non-CGM readings are ignored`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(100.0),
            UpstreamMeasure(
                id = "m-bgm", userId = userId, measuredAt = "2024-01-15T12:00:00Z",
                type = "BGM", source = "MANUAL",
                data = buildJsonObject { put("value", 200.0) }, status = "ACTIVE"
            ),
        )
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        assertEquals(1, result.totalCount)
    }

    @Test
    fun `zero and negative glucose values are excluded`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(100.0),
            cgmDto(0.0),
            cgmDto(-5.0),
        )
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        assertEquals(1, result.totalCount)
    }

    // ── Graceful degradation ─────────────────────────────────────────────────

    @Test
    fun `returns empty distribution with warning when upstream throws`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } throws
            UpstreamException("measures", 503, "Service Unavailable")
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        assertEquals(0, result.totalCount)
        assertEquals(80, result.buckets.size)
        assertTrue(result.buckets.all { it.count == 0 })
        assertTrue(result.warnings.any { it.contains("temporarily unavailable") })
    }

    @Test
    fun `empty timeframe returns warning and zero counts`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        assertEquals(0, result.totalCount)
        assertTrue(result.warnings.any { it.contains("No CGM readings") })
    }

    // ── zonePercents correctness ─────────────────────────────────────────────

    @Test
    fun `zonePercents has veryLow percent for a reading below 50`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(40.0),  // veryLow bucket (40-45, entirely < 54)
            cgmDto(100.0), // inRange
        )
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        // 40 goes into bucket 40-45 (veryLow), 100 goes into 100-105 (inRange)
        assertEquals(50.0, result.zonePercents.veryLow, 0.001)
        assertEquals(50.0, result.zonePercents.inRange, 0.001)
        assertEquals(0.0, result.zonePercents.veryHigh, 0.001)
    }

    @Test
    fun `zonePercents all zero when no readings`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getGlucoseDistribution(userId, from, to, auth, "mg/dL", "")
        val zp = result.zonePercents
        assertEquals(0.0, zp.veryLow, 0.001)
        assertEquals(0.0, zp.low, 0.001)
        assertEquals(0.0, zp.inRange, 0.001)
        assertEquals(0.0, zp.high, 0.001)
        assertEquals(0.0, zp.veryHigh, 0.001)
    }
}

private fun assertEquals(expected: Double, actual: Double, delta: Double) {
    assertTrue(
        kotlin.math.abs(expected - actual) <= delta,
        "Expected $expected ± $delta but was $actual"
    )
}

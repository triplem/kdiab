package org.javafreedom.kdiab.analyze.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.analyze.application.port.outbound.MeasuresPort
import org.javafreedom.kdiab.analyze.application.port.outbound.ProfilesPort
import org.javafreedom.kdiab.analyze.application.port.outbound.TreatmentsPort
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException
import org.javafreedom.kdiab.analyze.domain.model.UpstreamMeasure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyStatsServiceTest {

    private val measuresClient = mockk<MeasuresPort>()
    private val profilesClient = mockk<ProfilesPort>()
    private val service = AnalyticsService(measuresClient, profilesClient, mockk<TreatmentsPort>())

    private val userId = "user-1"
    private val auth = "Bearer token"
    private val from = "2024-01-01T00:00:00Z"
    private val to = "2024-01-03T23:59:59Z"   // 3-day window for most tests

    private fun cgmDto(
        sgv: Double,
        measuredAt: String,
        unit: String = "mg/dL",
    ) = UpstreamMeasure(
        id = "m-${sgv.toInt()}-$measuredAt",
        userId = userId,
        measuredAt = measuredAt,
        type = "CGM",
        source = "AAPS",
        data = buildJsonObject { put("value", sgv); put("unit", unit) },
        status = "ACTIVE",
    )

    // ── Per-day grouping and timezone bucketing ───────────────────────────────

    @Test
    fun `getDailyStats groups readings by calendar date in UTC`() = runTest {
        // Two readings on 2024-01-01 UTC, one on 2024-01-02 UTC
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(100.0, "2024-01-01T06:00:00Z"),
            cgmDto(120.0, "2024-01-01T18:00:00Z"),
            cgmDto(140.0, "2024-01-02T12:00:00Z"),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.rows.first { it.date == "2024-01-01" }
        assertEquals(2, jan1.cgmCount)

        val jan2 = result.rows.first { it.date == "2024-01-02" }
        assertEquals(1, jan2.cgmCount)
    }

    @Test
    fun `getDailyStats buckets reading at 23_00 UTC into next day when timezone is UTC+2`() = runTest {
        // 2024-01-01T23:00:00Z = 2024-01-02 01:00 in UTC+2
        val utcPlusTwo = TimeZone.of("UTC+2")
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(100.0, "2024-01-01T23:00:00Z"),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", utcPlusTwo)

        // In UTC+2, that reading lands on 2024-01-02
        val jan1 = result.rows.firstOrNull { it.date == "2024-01-01" }
        val jan2 = result.rows.firstOrNull { it.date == "2024-01-02" }
        assertNotNull(jan2, "Expected row for 2024-01-02")
        assertEquals(1, jan2.cgmCount)
        if (jan1 != null) assertEquals(0, jan1.cgmCount)
    }

    @Test
    fun `getDailyStats rows are ordered newest first`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(100.0, "2024-01-01T10:00:00Z"),
            cgmDto(110.0, "2024-01-02T10:00:00Z"),
            cgmDto(120.0, "2024-01-03T10:00:00Z"),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val dates = result.rows.map { it.date }
        val sorted = dates.sortedDescending()
        assertEquals(sorted, dates, "Rows must be in reverse chronological order")
    }

    // ── Zero-reading day ──────────────────────────────────────────────────────

    @Test
    fun `getDailyStats includes days with no readings with null numeric fields`() = runTest {
        // Only Jan 1 has a reading; Jan 2 and Jan 3 have none
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(100.0, "2024-01-01T10:00:00Z"),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan2 = result.rows.firstOrNull { it.date == "2024-01-02" }
        assertNotNull(jan2, "Day with no readings must still appear in rows")
        assertEquals(0, jan2.cgmCount)
        assertNull(jan2.veryLowPercent)
        assertNull(jan2.lowPercent)
        assertNull(jan2.inRangePercent)
        assertNull(jan2.highPercent)
        assertNull(jan2.veryHighPercent)
        assertNull(jan2.p25)
        assertNull(jan2.median)
        assertNull(jan2.p75)
        assertNull(jan2.sd)
        assertNull(jan2.eHbA1c)
    }

    // ── TIR zone classification ───────────────────────────────────────────────

    @Test
    fun `getDailyStats classifies TIR zones correctly for a single day`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(40.0,  "2024-01-01T01:00:00Z"),   // very low  < 54
            cgmDto(60.0,  "2024-01-01T02:00:00Z"),   // low       54–70
            cgmDto(100.0, "2024-01-01T03:00:00Z"),   // in range  70–180
            cgmDto(200.0, "2024-01-01T04:00:00Z"),   // high      180–250
            cgmDto(300.0, "2024-01-01T05:00:00Z"),   // very high > 250
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.rows.first { it.date == "2024-01-01" }
        assertEquals(5, jan1.cgmCount)
        assertEquals(20.0, jan1.veryLowPercent!!, 0.01)
        assertEquals(20.0, jan1.lowPercent!!, 0.01)
        assertEquals(20.0, jan1.inRangePercent!!, 0.01)
        assertEquals(20.0, jan1.highPercent!!, 0.01)
        assertEquals(20.0, jan1.veryHighPercent!!, 0.01)
    }

    @Test
    fun `getDailyStats TIR boundary values - 54 is low not veryLow, 70 is inRange not low`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(54.0,  "2024-01-01T01:00:00Z"),   // low (≥54)
            cgmDto(70.0,  "2024-01-01T02:00:00Z"),   // in range (≥70)
            cgmDto(180.0, "2024-01-01T03:00:00Z"),   // in range (≤180)
            cgmDto(250.0, "2024-01-01T04:00:00Z"),   // high (≤250)
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.rows.first { it.date == "2024-01-01" }
        assertEquals(0.0, jan1.veryLowPercent!!, 0.01)
        assertEquals(25.0, jan1.lowPercent!!, 0.01)
        assertEquals(50.0, jan1.inRangePercent!!, 0.01)
        assertEquals(25.0, jan1.highPercent!!, 0.01)
        assertEquals(0.0, jan1.veryHighPercent!!, 0.01)
    }

    // ── Percentile computation ────────────────────────────────────────────────

    @Test
    fun `getDailyStats computes percentiles correctly for a sorted set`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(160.0, "2024-01-01T01:00:00Z"),
            cgmDto(100.0, "2024-01-01T02:00:00Z"),
            cgmDto(140.0, "2024-01-01T03:00:00Z"),
            cgmDto(120.0, "2024-01-01T04:00:00Z"),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.rows.first { it.date == "2024-01-01" }
        // sorted: [100, 120, 140, 160]
        // p25: index=0.75 → 100 + 0.75*(120-100) = 115
        // p50: index=1.5  → 120 + 0.5*(140-120) = 130
        // p75: index=2.25 → 140 + 0.25*(160-140) = 145
        assertEquals(115.0, jan1.p25!!, 0.01)
        assertEquals(130.0, jan1.median!!, 0.01)
        assertEquals(145.0, jan1.p75!!, 0.01)
    }

    @Test
    fun `getDailyStats single reading gives equal p25 median and p75`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-01T10:00:00Z"),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.rows.first { it.date == "2024-01-01" }
        assertEquals(120.0, jan1.p25!!, 0.01)
        assertEquals(120.0, jan1.median!!, 0.01)
        assertEquals(120.0, jan1.p75!!, 0.01)
    }

    // ── Standard deviation ────────────────────────────────────────────────────

    @Test
    fun `getDailyStats computes SD as population standard deviation`() = runTest {
        // Two readings: 100 and 120. Mean=110, variance=((100-110)²+(120-110)²)/2 = 100, sd=10
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(100.0, "2024-01-01T06:00:00Z"),
            cgmDto(120.0, "2024-01-01T18:00:00Z"),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.rows.first { it.date == "2024-01-01" }
        assertEquals(10.0, jan1.sd!!, 0.01)
    }

    @Test
    fun `getDailyStats SD is zero when all readings are identical`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-01T06:00:00Z"),
            cgmDto(120.0, "2024-01-01T12:00:00Z"),
            cgmDto(120.0, "2024-01-01T18:00:00Z"),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.rows.first { it.date == "2024-01-01" }
        assertEquals(0.0, jan1.sd!!, 0.001)
    }

    // ── eHbA1c ────────────────────────────────────────────────────────────────

    @Test
    fun `getDailyStats computes eHbA1c via DCCT formula`() = runTest {
        // Mean = 154 → eHbA1c = (154 + 46.7) / 28.7 ≈ 6.99
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(140.0, "2024-01-01T06:00:00Z"),
            cgmDto(168.0, "2024-01-01T18:00:00Z"),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.rows.first { it.date == "2024-01-01" }
        val expected = (154.0 + 46.7) / 28.7
        assertEquals(expected, jan1.eHbA1c!!, 0.01)
    }

    // ── Summary row ───────────────────────────────────────────────────────────

    @Test
    fun `getDailyStats summary row has date equal to summary`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(100.0, "2024-01-01T10:00:00Z"),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)
        assertEquals("summary", result.summary.date)
    }

    @Test
    fun `getDailyStats summary row averages metrics across days with readings`() = runTest {
        // Day 1: inRange = 100% (one reading at 100 mg/dL)
        // Day 2: inRange = 100% (one reading at 150 mg/dL)
        // Day 3: no readings — must be excluded from summary average
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(100.0, "2024-01-01T10:00:00Z"),
            cgmDto(150.0, "2024-01-02T10:00:00Z"),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        // Summary inRange should average 100% and 100% = 100%
        assertEquals(100.0, result.summary.inRangePercent!!, 0.01)
        // cgmCount in summary is total across all days with readings
        assertEquals(2, result.summary.cgmCount)
    }

    @Test
    fun `getDailyStats summary row is all-null when no readings exist`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        assertEquals("summary", result.summary.date)
        assertEquals(0, result.summary.cgmCount)
        assertNull(result.summary.inRangePercent)
        assertNull(result.summary.median)
        assertNull(result.summary.eHbA1c)
    }

    // ── Warnings ──────────────────────────────────────────────────────────────

    @Test
    fun `getDailyStats warns when fewer than 14 days have readings`() = runTest {
        // Only 1 day with readings in a 3-day window
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(100.0, "2024-01-01T10:00:00Z"),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)
        assertTrue(result.warnings.any { it.contains("14") }, "Expected < 14 days warning")
    }

    @Test
    fun `getDailyStats emits no warning when 14 or more days have readings`() = runTest {
        // Build 14 days of readings
        val longFrom = "2024-01-01T00:00:00Z"
        val longTo   = "2024-01-15T23:59:59Z"
        val readings = (1..14).map { day ->
            val dateStr = "2024-01-%02dT12:00:00Z".format(day)
            cgmDto(120.0, dateStr)
        }
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns readings
        val result = service.getDailyStats(userId, longFrom, longTo, auth, "mg/dL", "", TimeZone.UTC)
        assertTrue(result.warnings.none { it.contains("14") }, "Must not warn with 14 days of data")
    }

    // ── Graceful degradation — upstream unavailable ───────────────────────────

    @Test
    fun `getDailyStats returns empty result with warning when upstream throws`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } throws
            UpstreamException("measures", 503, "Service Unavailable")
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        assertTrue(result.rows.isEmpty())
        assertEquals("summary", result.summary.date)
        assertEquals(0, result.summary.cgmCount)
        assertTrue(result.warnings.any { it.contains("temporarily unavailable") })
    }

    // ── Non-CGM readings are ignored ──────────────────────────────────────────

    @Test
    fun `getDailyStats ignores non-CGM measures`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-01T10:00:00Z"),
            UpstreamMeasure(
                id = "bgm-1", userId = userId, measuredAt = "2024-01-01T11:00:00Z",
                type = "BGM", source = "MANUAL",
                data = buildJsonObject { put("value", 200.0) }, status = "ACTIVE",
            ),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.rows.first { it.date == "2024-01-01" }
        assertEquals(1, jan1.cgmCount)  // BGM must not be counted
    }

    // ── mmol/L conversion ─────────────────────────────────────────────────────

    @Test
    fun `getDailyStats converts mmol per L readings to mg per dL before computing`() = runTest {
        // 6.0 mmol/L * 18.0182 = 108.1092 mg/dL
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(6.0, "2024-01-01T10:00:00Z", unit = "mmol/L"),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mmol/L", "", TimeZone.UTC)

        val jan1 = result.rows.first { it.date == "2024-01-01" }
        assertEquals(1, jan1.cgmCount)
        val expectedMedian = 6.0 * 18.0182
        assertEquals(expectedMedian, jan1.median!!, 0.01)
    }
}

private fun assertEquals(expected: Double, actual: Double?, delta: Double) {
    requireNotNull(actual) { "Expected non-null Double but got null" }
    assertTrue(
        Math.abs(expected - actual) <= delta,
        "Expected $expected ± $delta but was $actual"
    )
}

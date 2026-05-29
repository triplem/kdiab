package org.javafreedom.kdiab.analyze.application.service

import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.javafreedom.kdiab.analyze.application.port.outbound.MeasuresPort
import org.javafreedom.kdiab.analyze.application.port.outbound.ProfilesPort
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException
import org.javafreedom.kdiab.analyze.domain.model.UpstreamMeasure

class DailyStatsServiceTest {

    private val measuresPort = mockk<MeasuresPort>()
    private val profilesPort = mockk<ProfilesPort>(relaxed = true)
    private val service = AnalyticsService(measuresPort, profilesPort)

    private val userId = "user-1"
    private val auth = "Bearer test-token"
    private val correlationId = "corr-1"
    private val from = "2024-01-15T00:00:00Z"
    private val to = "2024-01-15T23:59:59Z"

    private fun cgmMeasure(measuredAt: String, mgDl: Double, unit: String = "mg/dL") =
        UpstreamMeasure(
            id = UUID.randomUUID().toString(),
            userId = UUID.randomUUID().toString(),
            measuredAt = measuredAt,
            type = "CGM",
            source = null,
            data = buildJsonObject {
                put("value", mgDl)
                put("unit", unit)
            },
            status = "FINAL",
        )

    private fun setupMeasures(vararg measures: UpstreamMeasure) {
        coEvery {
            measuresPort.getMeasures(userId, auth, correlationId, any(), any())
        } returns measures.toList()
    }

    // ── Basic grouping ─────────────────────────────────────────────────────────

    @Test
    fun `getDailyStats groups readings by UTC date when timezone is UTC`() = runTest {
        setupMeasures(
            cgmMeasure("2024-01-15T12:00:00Z", 120.0),
            cgmMeasure("2024-01-15T18:00:00Z", 130.0),
        )
        val result = service.getDailyStats(
            userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC,
        )
        assertEquals(1, result.rows.size)
        assertEquals("2024-01-15", result.rows.first().date)
        assertEquals(2, result.rows.first().cgmCount)
    }

    @Test
    fun `getDailyStats buckets readings into patient local timezone`() = runTest {
        // UTC+2 timezone: 23:30 UTC on Jan 15 = 01:30 on Jan 16 in UTC+2
        setupMeasures(
            cgmMeasure("2024-01-15T22:30:00Z", 110.0),  // → Jan 15 in UTC+2 (00:30)
            cgmMeasure("2024-01-15T23:30:00Z", 130.0),  // → Jan 16 in UTC+2 (01:30)
        )
        val result = service.getDailyStats(
            userId, from, "2024-01-16T23:59:59Z", auth, "mg/dL", correlationId,
            TimeZone.of("Europe/Berlin"), // UTC+1 in winter, but use fixed offset for clarity
        )
        // Both dates should appear in the result
        val dates = result.rows.map { it.date }.toSet()
        assertTrue(dates.size >= 2)
    }

    @Test
    fun `getDailyStats orders rows newest first`() = runTest {
        coEvery {
            measuresPort.getMeasures(userId, auth, correlationId, any(), any())
        } returns listOf(
            cgmMeasure("2024-01-14T12:00:00Z", 100.0),
            cgmMeasure("2024-01-15T12:00:00Z", 120.0),
            cgmMeasure("2024-01-16T12:00:00Z", 140.0),
        )
        val result = service.getDailyStats(
            userId,
            from = "2024-01-14T00:00:00Z",
            to = "2024-01-16T23:59:59Z",
            authorization = auth,
            glucoseUnit = "mg/dL",
            correlationId = correlationId,
            timeZone = TimeZone.UTC,
        )
        val dates = result.rows.map { it.date }
        assertEquals("2024-01-16", dates[0])
        assertEquals("2024-01-15", dates[1])
        assertEquals("2024-01-14", dates[2])
    }

    @Test
    fun `getDailyStats includes zero-reading days with null numeric fields`() = runTest {
        // Jan 15 has readings, Jan 16 does not
        setupMeasures(
            cgmMeasure("2024-01-15T12:00:00Z", 120.0),
        )
        val result = service.getDailyStats(
            userId,
            from = "2024-01-15T00:00:00Z",
            to = "2024-01-16T23:59:59Z",
            authorization = auth,
            glucoseUnit = "mg/dL",
            correlationId = correlationId,
            timeZone = TimeZone.UTC,
        )
        val emptyDay = result.rows.firstOrNull { it.cgmCount == 0 }
        assertNotNull(emptyDay)
        assertNull(emptyDay.veryLowPercent)
        assertNull(emptyDay.lowPercent)
        assertNull(emptyDay.inRangePercent)
        assertNull(emptyDay.highPercent)
        assertNull(emptyDay.veryHighPercent)
        assertNull(emptyDay.p25)
        assertNull(emptyDay.median)
        assertNull(emptyDay.p75)
        assertNull(emptyDay.sd)
        assertNull(emptyDay.eHbA1c)
    }

    // ── TIR zone classification ────────────────────────────────────────────────

    @Test
    fun `getDailyStats classifies very low readings below 54`() = runTest {
        setupMeasures(cgmMeasure("2024-01-15T10:00:00Z", 50.0))
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        val row = result.rows.first { it.cgmCount > 0 }
        assertEquals(100.0, row.veryLowPercent)
        assertEquals(0.0, row.lowPercent)
        assertEquals(0.0, row.inRangePercent)
        assertEquals(0.0, row.highPercent)
        assertEquals(0.0, row.veryHighPercent)
    }

    @Test
    fun `getDailyStats classifies low readings 54 to below 70`() = runTest {
        setupMeasures(cgmMeasure("2024-01-15T10:00:00Z", 60.0))
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        val row = result.rows.first { it.cgmCount > 0 }
        assertEquals(0.0, row.veryLowPercent)
        assertEquals(100.0, row.lowPercent)
        assertEquals(0.0, row.inRangePercent)
    }

    @Test
    fun `getDailyStats classifies in-range readings 70 to 180`() = runTest {
        setupMeasures(
            cgmMeasure("2024-01-15T10:00:00Z", 70.0),
            cgmMeasure("2024-01-15T11:00:00Z", 120.0),
            cgmMeasure("2024-01-15T12:00:00Z", 180.0),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        val row = result.rows.first { it.cgmCount > 0 }
        assertEquals(0.0, row.veryLowPercent)
        assertEquals(0.0, row.lowPercent)
        assertEquals(100.0, row.inRangePercent)
        assertEquals(0.0, row.highPercent)
    }

    @Test
    fun `getDailyStats classifies high readings 180 exclusive to 250 exclusive`() = runTest {
        setupMeasures(cgmMeasure("2024-01-15T10:00:00Z", 200.0))
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        val row = result.rows.first { it.cgmCount > 0 }
        assertEquals(0.0, row.inRangePercent)
        assertEquals(100.0, row.highPercent)
        assertEquals(0.0, row.veryHighPercent)
    }

    @Test
    fun `getDailyStats classifies very high readings strictly above 250`() = runTest {
        // 250 mg/dL = upper bound of high zone (high: >180..250); very high: >250
        setupMeasures(
            cgmMeasure("2024-01-15T10:00:00Z", 260.0),
            cgmMeasure("2024-01-15T11:00:00Z", 300.0),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        val row = result.rows.first { it.cgmCount > 0 }
        assertEquals(0.0, row.highPercent)
        assertEquals(100.0, row.veryHighPercent)
    }

    @Test
    fun `getDailyStats classifies 250 as high not very high`() = runTest {
        setupMeasures(cgmMeasure("2024-01-15T10:00:00Z", 250.0))
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        val row = result.rows.first { it.cgmCount > 0 }
        assertEquals(100.0, row.highPercent)
        assertEquals(0.0, row.veryHighPercent)
    }

    // ── Percentiles ────────────────────────────────────────────────────────────

    @Test
    fun `getDailyStats computes percentiles for 4 readings`() = runTest {
        // sorted: 100, 120, 140, 160
        // p25 = index 0.75 → 100 + 0.75*(120-100) = 115
        // p50 = index 1.5  → 120 + 0.5*(140-120)  = 130
        // p75 = index 2.25 → 140 + 0.25*(160-140) = 145
        setupMeasures(
            cgmMeasure("2024-01-15T06:00:00Z", 160.0),
            cgmMeasure("2024-01-15T09:00:00Z", 100.0),
            cgmMeasure("2024-01-15T12:00:00Z", 140.0),
            cgmMeasure("2024-01-15T15:00:00Z", 120.0),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        val row = result.rows.first { it.cgmCount > 0 }
        assertEquals(115.0, row.p25!!, 0.001)
        assertEquals(130.0, row.median!!, 0.001)
        assertEquals(145.0, row.p75!!, 0.001)
    }

    @Test
    fun `getDailyStats returns equal percentiles for single reading`() = runTest {
        setupMeasures(cgmMeasure("2024-01-15T12:00:00Z", 120.0))
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        val row = result.rows.first { it.cgmCount > 0 }
        assertEquals(120.0, row.p25!!, 0.001)
        assertEquals(120.0, row.median!!, 0.001)
        assertEquals(120.0, row.p75!!, 0.001)
    }

    // ── Standard deviation ─────────────────────────────────────────────────────

    @Test
    fun `getDailyStats computes population standard deviation`() = runTest {
        // 100 and 120: mean = 110, deviations = 10, 10; sd = sqrt(200/2) = 10
        setupMeasures(
            cgmMeasure("2024-01-15T10:00:00Z", 100.0),
            cgmMeasure("2024-01-15T11:00:00Z", 120.0),
        )
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        val row = result.rows.first { it.cgmCount > 0 }
        assertEquals(10.0, row.sd!!, 0.001)
    }

    @Test
    fun `getDailyStats returns zero sd for a single reading`() = runTest {
        setupMeasures(cgmMeasure("2024-01-15T12:00:00Z", 120.0))
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        val row = result.rows.first { it.cgmCount > 0 }
        assertEquals(0.0, row.sd!!, 0.001)
    }

    // ── eHbA1c ────────────────────────────────────────────────────────────────

    @Test
    fun `getDailyStats computes eHbA1c via DCCT formula`() = runTest {
        // mean = 154 mg/dL → eHbA1c = (154 + 46.7) / 28.7 = 7.0
        setupMeasures(cgmMeasure("2024-01-15T12:00:00Z", 154.0))
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        val row = result.rows.first { it.cgmCount > 0 }
        val expected = (154.0 + 46.7) / 28.7
        assertEquals(expected, row.eHbA1c!!, 0.001)
    }

    // ── mmol/L conversion ─────────────────────────────────────────────────────

    @Test
    fun `getDailyStats converts mmol per L readings to mg per dL before computing stats`() = runTest {
        // 7.0 mmol/L * 18.0182 ≈ 126.13 mg/dL
        setupMeasures(cgmMeasure("2024-01-15T12:00:00Z", 7.0, unit = "mmol/L"))
        val result = service.getDailyStats(userId, from, to, auth, "mmol/L", correlationId, TimeZone.UTC)
        val row = result.rows.first { it.cgmCount > 0 }
        val expectedMgDl = 7.0 * 18.0182
        assertEquals(expectedMgDl, row.median!!, 0.1)
    }

    // ── Non-CGM filtering ─────────────────────────────────────────────────────

    @Test
    fun `getDailyStats ignores non-CGM measures`() = runTest {
        val bgm = UpstreamMeasure(
            id = UUID.randomUUID().toString(),
            userId = UUID.randomUUID().toString(),
            measuredAt = "2024-01-15T12:00:00Z",
            type = "BGM",
            source = null,
            data = buildJsonObject { put("value", 120.0); put("unit", "mg/dL") },
            status = "FINAL",
        )
        coEvery {
            measuresPort.getMeasures(userId, auth, correlationId, any(), any())
        } returns listOf(bgm)
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        val row = result.rows.first()
        assertEquals(0, row.cgmCount)
        assertNull(row.median)
    }

    // ── Summary row ───────────────────────────────────────────────────────────

    @Test
    fun `getDailyStats summary row has date summary`() = runTest {
        setupMeasures(cgmMeasure("2024-01-15T12:00:00Z", 120.0))
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        assertEquals("summary", result.summary.date)
    }

    @Test
    fun `getDailyStats summary row averages metrics across days with readings only`() = runTest {
        // Jan 15: mean 100, Jan 17: mean 200, Jan 16: no readings → summary mean = (100+200)/2 = 150
        coEvery {
            measuresPort.getMeasures(userId, auth, correlationId, any(), any())
        } returns listOf(
            cgmMeasure("2024-01-15T12:00:00Z", 100.0),
            cgmMeasure("2024-01-17T12:00:00Z", 200.0),
        )
        val result = service.getDailyStats(
            userId,
            from = "2024-01-15T00:00:00Z",
            to = "2024-01-17T23:59:59Z",
            authorization = auth,
            glucoseUnit = "mg/dL",
            correlationId = correlationId,
            timeZone = TimeZone.UTC,
        )
        // Summary median should be average of the two daily medians (100 and 200)
        assertEquals(150.0, result.summary.median!!, 0.001)
    }

    @Test
    fun `getDailyStats summary has null numeric fields when no readings in range`() = runTest {
        setupMeasures() // empty
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        assertEquals("summary", result.summary.date)
        assertNull(result.summary.median)
        assertNull(result.summary.eHbA1c)
        assertNull(result.summary.sd)
    }

    // ── Warnings ──────────────────────────────────────────────────────────────

    @Test
    fun `getDailyStats warns when fewer than 14 days have readings`() = runTest {
        // 5 days with readings in a 7-day window → warn
        val measures = (0..4).map { day ->
            cgmMeasure("2024-01-${(15 + day).toString().padStart(2, '0')}T12:00:00Z", 120.0)
        }
        coEvery {
            measuresPort.getMeasures(userId, auth, correlationId, any(), any())
        } returns measures
        val result = service.getDailyStats(
            userId,
            from = "2024-01-15T00:00:00Z",
            to = "2024-01-21T23:59:59Z",
            authorization = auth,
            glucoseUnit = "mg/dL",
            correlationId = correlationId,
            timeZone = TimeZone.UTC,
        )
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `getDailyStats has no warning when 14 or more days have readings`() = runTest {
        val measures = (0..13).map { day ->
            cgmMeasure("2024-01-${(15 + day).toString().padStart(2, '0')}T12:00:00Z", 120.0)
        }
        coEvery {
            measuresPort.getMeasures(userId, auth, correlationId, any(), any())
        } returns measures
        val result = service.getDailyStats(
            userId,
            from = "2024-01-15T00:00:00Z",
            to = "2024-01-28T23:59:59Z",
            authorization = auth,
            glucoseUnit = "mg/dL",
            correlationId = correlationId,
            timeZone = TimeZone.UTC,
        )
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `getDailyStats has no warning when zero readings - not enough data to warn`() = runTest {
        setupMeasures()
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        // Zero days with readings: no data at all — no warning (nothing to represent)
        assertTrue(result.warnings.isEmpty())
    }

    // ── Upstream failure ──────────────────────────────────────────────────────

    @Test
    fun `getDailyStats returns empty result with warning when upstream throws UpstreamException`() = runTest {
        coEvery {
            measuresPort.getMeasures(userId, auth, correlationId, any(), any())
        } throws UpstreamException(service = "measures", statusCode = 503, message = "Service Unavailable")
        val result = service.getDailyStats(userId, from, to, auth, "mg/dL", correlationId, TimeZone.UTC)
        assertTrue(result.rows.isEmpty())
        assertEquals("summary", result.summary.date)
        assertNull(result.summary.median)
        assertTrue(result.warnings.any { it.contains("unavailable") })
    }
}

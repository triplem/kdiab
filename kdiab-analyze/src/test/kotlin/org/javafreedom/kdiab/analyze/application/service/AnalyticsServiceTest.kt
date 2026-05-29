package org.javafreedom.kdiab.analyze.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.common.plugins.CircuitBreakerOpenException
import org.javafreedom.kdiab.analyze.application.port.outbound.MeasuresPort
import org.javafreedom.kdiab.analyze.application.port.outbound.ProfilesPort
import org.javafreedom.kdiab.analyze.application.port.outbound.TreatmentsPort
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException
import org.javafreedom.kdiab.analyze.domain.model.UpstreamMeasure
import org.javafreedom.kdiab.analyze.domain.model.UpstreamProfile
import org.javafreedom.kdiab.analyze.domain.model.UpstreamTreatment
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsServiceTest {

    private val measuresClient = mockk<MeasuresPort>()
    private val profilesClient = mockk<ProfilesPort>()
    private val treatmentsClient = mockk<TreatmentsPort>()
    private val service = AnalyticsService(measuresClient, profilesClient, treatmentsClient)

    private val userId = "user-1"
    private val auth = "Bearer token"
    private val from = "2024-01-01T00:00:00Z"
    private val to = "2024-01-31T23:59:59Z"

    private fun cgmDto(sgv: Double, measuredAt: String = "2024-01-15T12:00:00Z") = UpstreamMeasure(
        id = "m-1",
        userId = userId,
        measuredAt = measuredAt,
        type = "CGM",
        source = "MANUAL",
        data = buildJsonObject { put("value", sgv); put("unit", "mg/dL") },
        status = "ACTIVE",
    )

    private fun cgmDtoMmol(sgv: Double, measuredAt: String = "2024-01-15T12:00:00Z") = UpstreamMeasure(
        id = "m-2",
        userId = userId,
        measuredAt = measuredAt,
        type = "CGM",
        source = "MANUAL",
        data = buildJsonObject { put("value", sgv); put("unit", "mmol/L") },
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
        // 8.0 mmol/L * 18.0182 = 144.1456 mg/dL — uses per-measure storage unit (mmol/L)
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(cgmDtoMmol(8.0))
        val result = service.getHba1c(userId, from, to, auth, "mmol/L", "")
        assertEquals(8.0 * 18.0182, result.meanGlucose, absoluteTolerance = 0.01)
    }

    @Test
    fun `getHba1c ignores non-CGM readings`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(200.0),
            UpstreamMeasure(
                id = "m-2", userId = userId, measuredAt = "2024-01-15T13:00:00Z",
                type = "BGM", source = "MANUAL",
                data = buildJsonObject { put("value", 180.0) }, status = "ACTIVE"
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
    fun `getAgp returns 288 five-minute buckets`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertEquals(288, result.bucketData.size)
    }

    @Test
    fun `getAgp bucket minuteOfDay values are multiples of 5 from 0 to 1435`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns emptyList()
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        result.bucketData.forEachIndexed { index, bucket ->
            assertEquals(index * 5, bucket.minuteOfDay)
        }
    }

    @Test
    fun `getAgp assigns readings to correct 5-minute bucket`() = runTest {
        // 14:05 UTC → minute-of-day 845 → bucketIndex 169 → minuteOfDay 845
        // 14:30 UTC → minute-of-day 870 → bucketIndex 174 → minuteOfDay 870
        // Both readings at 14:05 land in bucket 169, 14:30 in bucket 174
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-15T14:05:00Z"),
            cgmDto(130.0, "2024-01-15T14:05:30Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        // 14:05 = 845 min → bucketIndex 169 → minuteOfDay 845
        val bucket845 = result.bucketData.first { it.minuteOfDay == 845 }
        assertEquals(2, bucket845.count)
        // linear interpolation: p10 = 120 + 0.1*(130-120) = 121, p90 = 120 + 0.9*(130-120) = 129
        assertEquals(121.0, bucket845.p10, absoluteTolerance = 0.01)
        assertEquals(129.0, bucket845.p90, absoluteTolerance = 0.01)
        assertEquals(125.0, bucket845.median, absoluteTolerance = 0.01)
    }

    @Test
    fun `getAgp empty buckets have null percentiles`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(100.0, "2024-01-15T10:00:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        // bucket at minuteOfDay=0 (midnight) has no readings
        val emptyBucket = result.bucketData.first { it.minuteOfDay == 0 }
        assertEquals(0, emptyBucket.count)
    }

    @Test
    fun `getAgp converts mmol per L before bucketing`() = runTest {
        // 6.0 mmol/L * 18.0182 = 108.1092 mg/dL — uses per-measure storage unit (mmol/L)
        // 08:00 UTC → minute-of-day 480 → bucketIndex 96 → minuteOfDay 480
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDtoMmol(6.0, "2024-01-15T08:00:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mmol/L", "")
        val bucket480 = result.bucketData.first { it.minuteOfDay == 480 }
        assertEquals(6.0 * 18.0182, bucket480.median, absoluteTolerance = 0.01)
    }

    @Test
    fun `getAgp single reading in bucket - all percentiles equal that value`() = runTest {
        // 06:00 UTC → minute-of-day 360 → bucketIndex 72 → minuteOfDay 360
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-15T06:00:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        val bucket360 = result.bucketData.first { it.minuteOfDay == 360 }
        assertEquals(1, bucket360.count)
        assertEquals(120.0, bucket360.p10!!, absoluteTolerance = 0.01)
        assertEquals(120.0, bucket360.p25!!, absoluteTolerance = 0.01)
        assertEquals(120.0, bucket360.median!!, absoluteTolerance = 0.01)
        assertEquals(120.0, bucket360.p75!!, absoluteTolerance = 0.01)
        assertEquals(120.0, bucket360.p90!!, absoluteTolerance = 0.01)
    }

    @Test
    fun `getAgp excludes negative and zero glucose values`() = runTest {
        // All three readings at 07:xx UTC → 420-424 min → bucketIndex 84 → minuteOfDay 420
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-15T07:00:00Z"),
            cgmDto(-5.0, "2024-01-15T07:01:00Z"),
            cgmDto(0.0, "2024-01-15T07:02:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        val bucket420 = result.bucketData.first { it.minuteOfDay == 420 }
        assertEquals(1, bucket420.count)
        assertEquals(120.0, bucket420.median!!, absoluteTolerance = 0.01)
    }

    @Test
    fun `getAgp buckets reading at 05_00 UTC into minute 420 when timezone is UTC+2`() = runTest {
        // A reading at 2024-01-15T05:00:00Z is 07:00 local time in UTC+2.
        // UTC bucket would be minuteOfDay=300 (05:00); UTC+2 bucket is minuteOfDay=420 (07:00).
        val utcPlusTwoHours = TimeZone.of("UTC+2")
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(150.0, "2024-01-15T05:00:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "", timeZone = utcPlusTwoHours)
        val bucket300 = result.bucketData.first { it.minuteOfDay == 300 }
        val bucket420 = result.bucketData.first { it.minuteOfDay == 420 }
        assertEquals(0, bucket300.count, "UTC minute 300 must be empty when timezone is UTC+2")
        assertEquals(1, bucket420.count, "Local minute 420 (UTC 05:00 + 2h) must contain the reading")
        assertEquals(150.0, bucket420.median!!, absoluteTolerance = 0.01)
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
    fun `getAgp warns when fewer than 144 buckets have CGM data`() = runTest {
        // Only one 5-minute bucket has data → 1 covered bucket < 144
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-15T08:00:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertTrue(result.warnings.isNotEmpty())
        assertTrue(result.warnings.any { it.contains("of 288 five-minute buckets") })
    }

    @Test
    fun `getAgp has no warnings when 144 or more buckets have CGM data`() = runTest {
        // Spread 144 readings across 144 distinct 5-minute buckets (one per 10-minute slot)
        val readings = (0 until 144).map { i ->
            val minuteOfDay = i * 10
            val hour = minuteOfDay / 60
            val minute = minuteOfDay % 60
            cgmDto(120.0, "2024-01-15T${String.format("%02d", hour)}:${String.format("%02d", minute)}:00Z")
        }
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns readings
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `getAgp returns totalReadingCount equal to sum of all bucket counts`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmDto(120.0, "2024-01-15T08:00:00Z"),
            cgmDto(130.0, "2024-01-15T08:30:00Z"),
            cgmDto(110.0, "2024-01-15T14:00:00Z"),
        )
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertEquals(3, result.totalReadingCount)
    }

    @Test
    fun `getAgp returns sensorWearDays as count of distinct calendar days with readings`() = runTest {
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
    fun `getAgp returns 288 empty buckets with warning when upstream throws UpstreamException`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } throws
            UpstreamException("measures", 502, "Bad Gateway")
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertEquals(288, result.bucketData.size)
        assertTrue(result.bucketData.all { it.count == 0 })
        assertEquals(0, result.totalReadingCount)
        assertEquals(0, result.sensorWearDays)
        assertTrue(result.warnings.any { it.contains("temporarily unavailable") })
    }

    @Test
    fun `getAgp returns 288 empty buckets with warning when circuit breaker is open`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } throws
            CircuitBreakerOpenException("measures")
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertEquals(288, result.bucketData.size)
        assertTrue(result.bucketData.all { it.count == 0 })
        assertTrue(result.warnings.any { it.contains("temporarily unavailable") })
    }

    @Test
    fun `getAgp empty buckets returned by graceful degradation have correct minuteOfDay values`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } throws
            UpstreamException("measures", 500, "Internal Server Error")
        val result = service.getAgp(userId, from, to, auth, "mg/dL", "")
        assertEquals(288, result.bucketData.size)
        result.bucketData.forEachIndexed { index, bucket ->
            assertEquals(index * 5, bucket.minuteOfDay)
            assertNull(bucket.median)
        }
    }

    // ── Report Summary ────────────────────────────────────────────────────────

    private fun treatmentDto(type: String, treatedAt: String = "2024-01-15T12:00:00Z") = UpstreamTreatment(
        id = "t-1",
        userId = userId,
        treatedAt = treatedAt,
        type = type,
        notes = null,
        data = buildJsonObject { },
    )

    private fun activeProfile() = UpstreamProfile(
        id = "p-1",
        userId = userId,
        status = "ACTIVE",
        name = "Test Profile",
        insulinType = "NovoRapid",
        durationOfAction = 240,
        analysisLow = null,
        analysisHigh = null,
        createdAt = null,
        validFrom = null,
        previousProfileId = null,
        activatedAt = null,
        archivedAt = null,
        basal = null,
        icr = null,
        isf = null,
        targets = null,
    )

    @Test
    fun `getReportSummary throws BusinessValidationException when range exceeds 365 days`() = runTest {
        assertFailsWith<BusinessValidationException> {
            service.getReportSummary(userId, "Test User", "2024-01-01T00:00:00Z", "2025-01-01T00:00:00Z", auth, "mg/dL", "")
        }
    }

    @Test
    fun `getReportSummary adds lessThan14Days warning when range is under 14 days`() = runTest {
        coEvery { measuresClient.getMeasures(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { profilesClient.getProfiles(any(), any(), any()) } returns emptyList()
        val result = service.getReportSummary(userId, "Test User", "2024-01-01T00:00:00Z", "2024-01-07T00:00:00Z", auth, "mg/dL", "")
        assertTrue(result.warnings.any { it.contains("lessThan14Days") })
    }

    @Test
    fun `getReportSummary adds no CGM warning when measures list is empty`() = runTest {
        coEvery { measuresClient.getMeasures(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { profilesClient.getProfiles(any(), any(), any()) } returns emptyList()
        val result = service.getReportSummary(userId, "Test User", from, to, auth, "mg/dL", "")
        assertTrue(result.warnings.any { it.contains("No CGM readings") })
        assertEquals(0, result.cgmReadingCount)
        assertNull(result.meanGlucose)
        assertNull(result.eHbA1c)
    }

    @Test
    fun `getReportSummary computes CGM stats correctly`() = runTest {
        coEvery { measuresClient.getMeasures(any(), any(), any(), any(), any()) } returns listOf(
            cgmDto(100.0), cgmDto(120.0), cgmDto(140.0),
        )
        coEvery { treatmentsClient.getTreatments(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { profilesClient.getProfiles(any(), any(), any()) } returns emptyList()
        val result = service.getReportSummary(userId, "Test User", from, to, auth, "mg/dL", "")
        assertEquals(3, result.cgmReadingCount)
        assertEquals(100.0, result.minGlucose, absoluteTolerance = 0.01)
        assertEquals(140.0, result.maxGlucose, absoluteTolerance = 0.01)
        assertEquals(120.0, result.meanGlucose, absoluteTolerance = 0.01)
        assertNotNull(result.eHbA1c)
        val expectedEHba1c = (120.0 + 46.7) / 28.7
        assertEquals(expectedEHba1c, result.eHbA1c, absoluteTolerance = 0.01)
        assertNotNull(result.gri)
        assertNotNull(result.griZone)
    }

    @Test
    fun `getReportSummary computes insulin device metrics from treatments`() = runTest {
        coEvery { measuresClient.getMeasures(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { profilesClient.getProfiles(any(), any(), any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(any(), any(), any(), any(), any()) } returns listOf(
            treatmentDto("INSULIN_CHANGE", "2024-01-05T10:00:00Z"),
            treatmentDto("INSULIN_CHANGE", "2024-01-20T10:00:00Z"),
            treatmentDto("SITE_CHANGE", "2024-01-08T10:00:00Z"),
            treatmentDto("SENSOR_INSERT", "2024-01-10T10:00:00Z"),
        )
        val result = service.getReportSummary(userId, "Test User", from, to, auth, "mg/dL", "")
        assertEquals(2, result.insulinChanges)
        assertEquals(1, result.siteChanges)
        assertEquals(1, result.sensorInserts)
        assertNotNull(result.avgDaysPerCartridge)
        assertNotNull(result.avgDaysPerSite)
        assertNotNull(result.avgDaysPerSensor)
    }

    @Test
    fun `getReportSummary computes bolus and carbs averages per day`() = runTest {
        coEvery { measuresClient.getMeasures(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { profilesClient.getProfiles(any(), any(), any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(any(), any(), any(), any(), any()) } returns listOf(
            UpstreamTreatment("t1", userId, "2024-01-05T10:00:00Z", "BOLUS", null, buildJsonObject { put("amount", 5.0) }),
            UpstreamTreatment("t2", userId, "2024-01-06T10:00:00Z", "CARBS", null, buildJsonObject { put("amount", 40.0) }),
        )
        val result = service.getReportSummary(userId, "Test User", from, to, auth, "mg/dL", "")
        assertNotNull(result.avgBolusPerDayIe)
        assertNotNull(result.avgCarbsPerDayG)
        assertTrue(result.avgBolusPerDayIe > 0.0)
        assertTrue(result.avgCarbsPerDayG > 0.0)
    }

    @Test
    fun `getReportSummary uses displayName in result`() = runTest {
        coEvery { measuresClient.getMeasures(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { profilesClient.getProfiles(any(), any(), any()) } returns emptyList()
        val result = service.getReportSummary(userId, "Sarah Müller", from, to, auth, "mg/dL", "")
        assertEquals("Sarah Müller", result.displayName)
    }

    @Test
    fun `getReportSummary picks up insulinType from active profile`() = runTest {
        coEvery { measuresClient.getMeasures(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { profilesClient.getProfiles(any(), any(), any()) } returns listOf(activeProfile())
        val result = service.getReportSummary(userId, "Test User", from, to, auth, "mg/dL", "")
        assertTrue(result.insulinTypes.contains("NovoRapid"))
    }

    @Test
    fun `getReportSummary degrades gracefully when measures upstream fails`() = runTest {
        coEvery { measuresClient.getMeasures(any(), any(), any(), any(), any()) } throws
            UpstreamException("measures", 503, "Service Unavailable")
        coEvery { treatmentsClient.getTreatments(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { profilesClient.getProfiles(any(), any(), any()) } returns emptyList()
        val result = service.getReportSummary(userId, "Test User", from, to, auth, "mg/dL", "")
        assertEquals(0, result.cgmReadingCount)
        assertNull(result.meanGlucose)
        assertNull(result.eHbA1c)
    }

    @Test
    fun `getReportSummary degrades gracefully when treatments upstream fails`() = runTest {
        coEvery { measuresClient.getMeasures(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(any(), any(), any(), any(), any()) } throws
            UpstreamException("treatments", 503, "Service Unavailable")
        coEvery { profilesClient.getProfiles(any(), any(), any()) } returns emptyList()
        val result = service.getReportSummary(userId, "Test User", from, to, auth, "mg/dL", "")
        assertEquals(0, result.insulinChanges)
        assertEquals(0, result.siteChanges)
        assertEquals(0, result.sensorInserts)
    }

    @Test
    fun `getReportSummary degrades gracefully when profiles upstream fails`() = runTest {
        coEvery { measuresClient.getMeasures(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { profilesClient.getProfiles(any(), any(), any()) } throws
            UpstreamException("profiles", 503, "Service Unavailable")
        val result = service.getReportSummary(userId, "Test User", from, to, auth, "mg/dL", "")
        assertTrue(result.insulinTypes.isEmpty())
    }
}

private fun assertEquals(expected: Double, actual: Double?, absoluteTolerance: Double) {
    requireNotNull(actual) { "Expected non-null value but got null" }
    assertTrue(
        Math.abs(expected - actual) <= absoluteTolerance,
        "Expected $expected ± $absoluteTolerance but was $actual"
    )
}

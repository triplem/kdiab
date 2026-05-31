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
import org.javafreedom.kdiab.analyze.domain.model.BasalSegment
import org.javafreedom.kdiab.analyze.domain.model.UpstreamMeasure
import org.javafreedom.kdiab.analyze.domain.model.UpstreamProfile
import org.javafreedom.kdiab.analyze.domain.model.UpstreamTreatment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyTrendServiceTest {

    private val measuresClient = mockk<MeasuresPort>()
    private val profilesClient = mockk<ProfilesPort>()
    private val treatmentsClient = mockk<TreatmentsPort>()
    private val service = AnalyticsService(measuresClient, profilesClient, treatmentsClient)

    private val userId = "user-1"
    private val auth = "Bearer token"
    private val from = "2024-01-01T00:00:00Z"
    private val to = "2024-01-07T23:59:59Z"

    private fun cgmAt(sgv: Double, timestamp: String) = UpstreamMeasure(
        id = "m-${timestamp.hashCode()}",
        userId = userId,
        measuredAt = timestamp,
        type = "CGM",
        source = "AAPS",
        data = buildJsonObject { put("value", sgv); put("unit", "mg/dL") },
        status = "ACTIVE",
    )

    private fun carbsAt(grams: Double, timestamp: String) = UpstreamTreatment(
        id = "t-${timestamp.hashCode()}",
        userId = userId,
        treatedAt = timestamp,
        type = "CARBS",
        notes = null,
        data = buildJsonObject { put("carbsG", grams) },
    )

    private fun profileWithBasal(basal: List<BasalSegment>, activatedAt: String = "2024-01-01T00:00:00Z") = UpstreamProfile(
        id = "profile-1",
        userId = userId,
        status = "ACTIVE",
        name = "Test Profile",
        insulinType = "NovoRapid",
        durationOfAction = 240,
        analysisLow = null,
        analysisHigh = null,
        createdAt = "2024-01-01T00:00:00Z",
        validFrom = "2024-01-01T00:00:00Z",
        previousProfileId = null,
        activatedAt = activatedAt,
        archivedAt = null,
        basal = basal,
        icr = null,
        isf = null,
        targets = null,
    )

    // ── Hourly mean computation ───────────────────────────────────────────────

    @Test
    fun `getDailyTrend computes hourly mean from multiple readings in the same hour`() = runTest {
        // Two readings in hour 10: 100 and 140 -> mean = 120
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(100.0, "2024-01-01T10:00:00Z"),
            cgmAt(140.0, "2024-01-01T10:45:00Z"),
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.days.firstOrNull { it.date == "2024-01-01" }
        assertNotNull(jan1, "Day 2024-01-01 must be present")
        val hour10 = jan1.hours[10]
        assertEquals(120.0, hour10.meanGlucose!!, "Mean of 100 and 140 must be 120")
    }

    @Test
    fun `getDailyTrend sets meanGlucose to null for hours with no readings`() = runTest {
        // Only hour 10 has a reading; hour 11 should be null
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(120.0, "2024-01-01T10:00:00Z"),
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.days.first { it.date == "2024-01-01" }
        assertNull(jan1.hours[11].meanGlucose, "Hour 11 has no readings and must be null")
        assertEquals("noData", jan1.hours[11].zone, "Zone must be noData when meanGlucose is null")
        assertNull(jan1.hours[11].trendPercent, "trendPercent must be null when meanGlucose is null")
        assertNull(jan1.hours[11].trendZone, "trendZone must be null when meanGlucose is null")
    }

    @Test
    fun `getDailyTrend returns 24 hourly entries per day`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(120.0, "2024-01-01T08:00:00Z"),
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.days.first { it.date == "2024-01-01" }
        assertEquals(24, jan1.hours.size, "Each day must have exactly 24 hourly entries")
        jan1.hours.forEachIndexed { index, row ->
            assertEquals(index, row.hour, "Hour $index must have hour field == $index")
        }
    }

    // ── Trend calculation ─────────────────────────────────────────────────────

    @Test
    fun `getDailyTrend computes trendPercent relative to previous hour mean`() = runTest {
        // Hour 9: mean=100, Hour 10: mean=120 -> trendPercent = (120-100)/100 * 100 = 20%
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(100.0, "2024-01-01T09:00:00Z"),
            cgmAt(120.0, "2024-01-01T10:00:00Z"),
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.days.first { it.date == "2024-01-01" }
        val hour10 = jan1.hours[10]
        assertEquals(20.0, hour10.trendPercent!!, 0.01, "TrendPercent must be 20% when rising from 100 to 120")
        assertEquals("risingFast", hour10.trendZone, "20% rise qualifies as risingFast")
    }

    @Test
    fun `getDailyTrend sets trendPercent null for hour 0 (no previous hour)`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(100.0, "2024-01-01T00:30:00Z"),
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.days.first { it.date == "2024-01-01" }
        assertNull(jan1.hours[0].trendPercent, "Hour 0 has no previous hour and must be null")
        assertNull(jan1.hours[0].trendZone, "trendZone must be null when trendPercent is null")
    }

    @Test
    fun `getDailyTrend classifies trendZone correctly for all zone boundaries`() = runTest {
        // Hours 0..5: means 100, 120, 111, 100, 89, 80
        // Hour 1 vs 0: +20% -> risingFast
        // Hour 2 vs 1: -7.5% -> stable (between -10% and +10%)
        // Hour 3 vs 2: -9.9% -> stable
        // Hour 4 vs 3: -11% -> falling
        // Hour 5 vs 4: -10.1% -> falling
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(100.0, "2024-01-01T00:00:00Z"),
            cgmAt(120.0, "2024-01-01T01:00:00Z"),  // +20% -> risingFast
            cgmAt(111.0, "2024-01-01T02:00:00Z"),  // -7.5% -> stable
            cgmAt(100.0, "2024-01-01T03:00:00Z"),  // -9.9% -> stable
            cgmAt(89.0,  "2024-01-01T04:00:00Z"),  // -11% -> falling
            cgmAt(71.1,  "2024-01-01T05:00:00Z"),  // ~-20.1% -> fallingFast
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.days.first { it.date == "2024-01-01" }
        assertEquals("risingFast", jan1.hours[1].trendZone)
        assertEquals("stable",     jan1.hours[2].trendZone)
        assertEquals("stable",     jan1.hours[3].trendZone)
        assertEquals("falling",    jan1.hours[4].trendZone)
        assertEquals("fallingFast",jan1.hours[5].trendZone)
    }

    @Test
    fun `getDailyTrend classifies rising zone for 10 to 19 percent increase`() = runTest {
        // Hour 6: 100, Hour 7: 115 -> +15% -> rising
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(100.0, "2024-01-01T06:00:00Z"),
            cgmAt(115.0, "2024-01-01T07:00:00Z"),
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.days.first { it.date == "2024-01-01" }
        assertEquals("rising", jan1.hours[7].trendZone, "15% rise must be classified as rising")
    }

    // ── Glucose zone classification ───────────────────────────────────────────

    @Test
    fun `getDailyTrend classifies glucose zones correctly`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(40.0,  "2024-01-01T00:00:00Z"),  // veryHypo < 54
            cgmAt(60.0,  "2024-01-01T01:00:00Z"),  // hypo 54..70
            cgmAt(120.0, "2024-01-01T02:00:00Z"),  // inRange 70..180
            cgmAt(200.0, "2024-01-01T03:00:00Z"),  // hyper 180..250
            cgmAt(300.0, "2024-01-01T04:00:00Z"),  // veryHyper > 250
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.days.first { it.date == "2024-01-01" }
        assertEquals("veryHypo", jan1.hours[0].zone)
        assertEquals("hypo",     jan1.hours[1].zone)
        assertEquals("inRange",  jan1.hours[2].zone)
        assertEquals("hyper",    jan1.hours[3].zone)
        assertEquals("veryHyper",jan1.hours[4].zone)
    }

    // ── Basal rate lookup ─────────────────────────────────────────────────────

    @Test
    fun `getDailyTrend returns basalRateIePerH from active profile for the matching hour`() = runTest {
        // Profile has two basal segments: 00:00 = 0.8 IU/h, 12:00 = 1.0 IU/h
        val profile = profileWithBasal(
            listOf(
                BasalSegment(startTime = "00:00", value = 0.8),
                BasalSegment(startTime = "12:00", value = 1.0),
            ),
        )
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(120.0, "2024-01-01T06:00:00Z"),
            cgmAt(120.0, "2024-01-01T14:00:00Z"),
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns listOf(profile)
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.days.first { it.date == "2024-01-01" }
        assertEquals(0.8, jan1.hours[6].basalRateIePerH!!, "Hour 6 must use 00:00 segment rate 0.8")
        assertEquals(1.0, jan1.hours[14].basalRateIePerH!!, "Hour 14 must use 12:00 segment rate 1.0")
    }

    @Test
    fun `getDailyTrend sets basalRateIePerH to null when no profile available`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(120.0, "2024-01-01T08:00:00Z"),
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.days.first { it.date == "2024-01-01" }
        assertNull(jan1.hours[8].basalRateIePerH, "basalRateIePerH must be null when no profile exists")
    }

    // ── Carbs sum ─────────────────────────────────────────────────────────────

    @Test
    fun `getDailyTrend sums carbsG for multiple carb entries in the same hour`() = runTest {
        // Two CARBS treatments at 13:10 and 13:40 on Jan 1 → sum should be in hour 13
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(120.0, "2024-01-01T13:00:00Z"),
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns listOf(
            carbsAt(30.0, "2024-01-01T13:10:00Z"),
            carbsAt(15.0, "2024-01-01T13:40:00Z"),
        )

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.days.first { it.date == "2024-01-01" }
        assertEquals(45.0, jan1.hours[13].carbsG, "Hour 13 must sum 30+15=45g of carbs")
    }

    @Test
    fun `getDailyTrend sets carbsG to 0 for hours with no carb entries`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(120.0, "2024-01-01T08:00:00Z"),
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        val jan1 = result.days.first { it.date == "2024-01-01" }
        assertEquals(0.0, jan1.hours[8].carbsG, "carbsG must be 0.0 when no carb entries exist")
        assertEquals(0.0, jan1.hours[9].carbsG, "carbsG must be 0.0 for hours with no readings or carbs")
    }

    // ── Timezone bucketing ────────────────────────────────────────────────────

    @Test
    fun `getDailyTrend buckets reading at 22_00 UTC into next day local date when timezone is UTC+3`() = runTest {
        // 2024-01-01T22:00:00Z in UTC+3 is 2024-01-02T01:00:00 local
        val utcPlus3 = TimeZone.of("UTC+3")
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(150.0, "2024-01-01T22:00:00Z"),
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", utcPlus3)

        val jan2 = result.days.firstOrNull { it.date == "2024-01-02" }
        assertNotNull(jan2, "Reading at 22:00 UTC in UTC+3 must land on 2024-01-02")
        assertEquals(150.0, jan2.hours[1].meanGlucose!!, "Local hour 1 of Jan 2 must have the reading")
    }

    // ── Graceful degradation ──────────────────────────────────────────────────

    @Test
    fun `getDailyTrend returns empty with warning when upstream measures throws`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } throws
            UpstreamException("measures", 503, "Service Unavailable")
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        assertTrue(result.days.isEmpty(), "days must be empty when upstream fails")
        assertTrue(result.warnings.any { it.contains("temporarily unavailable") })
    }

    @Test
    fun `getDailyTrend continues without carbs when treatments port throws`() = runTest {
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(120.0, "2024-01-01T10:00:00Z"),
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } throws
            UpstreamException("treatments", 503, "Service Unavailable")

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        assertTrue(result.days.isNotEmpty(), "Days must still be returned when treatments fail")
        assertTrue(result.warnings.any { it.contains("Carbohydrate data") })
        val jan1 = result.days.first { it.date == "2024-01-01" }
        assertEquals(0.0, jan1.hours[10].carbsG, "carbsG must default to 0 when treatments unavailable")
    }

    @Test
    fun `getDailyTrend continues with empty profiles when profiles port throws`() = runTest {
        // Profiles failure is caught internally; measures data and days are still returned.
        coEvery { measuresClient.getMeasures(userId, auth, any(), any(), any()) } returns listOf(
            cgmAt(120.0, "2024-01-01T10:00:00Z"),
        )
        coEvery { profilesClient.getProfiles(userId, auth, any()) } throws
            UpstreamException("profiles", 503, "Service Unavailable")
        coEvery { treatmentsClient.getTreatments(userId, auth, any(), any(), any()) } returns emptyList()

        val result = service.getDailyTrend(userId, from, to, auth, "mg/dL", "", TimeZone.UTC)

        assertTrue(result.days.isNotEmpty(), "Days must still be returned when profiles port fails")
        val jan1 = result.days.first { it.date == "2024-01-01" }
        assertNull(jan1.hours[10].basalRateIePerH, "basalRate must be null when profiles fail")
    }
}

private fun assertEquals(expected: Double, actual: Double?, absoluteTolerance: Double, message: String = "") {
    requireNotNull(actual) { "Expected non-null but got null. $message" }
    assertTrue(
        Math.abs(expected - actual) <= absoluteTolerance,
        "Expected $expected ± $absoluteTolerance but was $actual. $message",
    )
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package org.javafreedom.kdiab.analyze.adapters.inbound.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant
import org.javafreedom.kdiab.analyze.domain.model.AgpBucketData
import org.javafreedom.kdiab.analyze.domain.model.AgpResult
import org.javafreedom.kdiab.analyze.domain.model.BasalSegment
import org.javafreedom.kdiab.analyze.domain.model.CgpResult
import org.javafreedom.kdiab.analyze.domain.model.DailyStatRow
import org.javafreedom.kdiab.analyze.domain.model.DailyStatsResult
import org.javafreedom.kdiab.analyze.domain.model.DailyTrendDay
import org.javafreedom.kdiab.analyze.domain.model.DailyTrendResult
import org.javafreedom.kdiab.analyze.domain.model.DeviceAge
import org.javafreedom.kdiab.analyze.domain.model.DeviceStatus
import org.javafreedom.kdiab.analyze.domain.model.DeviceUsageResult
import org.javafreedom.kdiab.analyze.domain.model.GlucoseBucket
import org.javafreedom.kdiab.analyze.domain.model.GlucoseDistributionResult
import org.javafreedom.kdiab.analyze.domain.model.Hba1cResult
import org.javafreedom.kdiab.analyze.domain.model.HourlyTrendRow
import org.javafreedom.kdiab.analyze.domain.model.ProfileSummary
import org.javafreedom.kdiab.analyze.domain.model.ProfilesResult
import org.javafreedom.kdiab.analyze.domain.model.RatioSegment
import org.javafreedom.kdiab.analyze.domain.model.ReportSummaryResult
import org.javafreedom.kdiab.analyze.domain.model.TargetSegment
import org.javafreedom.kdiab.analyze.domain.model.TirBreakdown
import org.javafreedom.kdiab.analyze.domain.model.TirResult
import org.javafreedom.kdiab.analyze.domain.model.TirZone
import org.javafreedom.kdiab.analyze.domain.model.Timeline
import org.javafreedom.kdiab.analyze.domain.model.TimelineMeasure
import org.javafreedom.kdiab.analyze.domain.model.TimelineTreatment
import org.javafreedom.kdiab.analyze.domain.model.ZonePercents

// One class per mapper is appropriate: AnalyzeMapper has 511 lines and 10 public mapping
// functions. Splitting by function group would reduce readability without reducing complexity.
@Suppress("LargeClass")
class AnalyzeMapperTest {

    // ────────────────────────────────────────────────────────────────────────────
    // Timeline.toResponse()
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should map Timeline with measures and treatments to TimelineResponse`() {
        val measureId = Uuid.random()
        val userId = Uuid.random()
        val measureTs = Instant.parse("2024-01-15T08:00:00Z")
        val treatmentId = Uuid.random()
        val treatmentTs = Instant.parse("2024-01-15T12:30:00Z")
        val measureData = buildJsonObject { put("sgv", 120) }
        val treatmentData = buildJsonObject { put("insulin", 2.5) }

        val domain = Timeline(
            measures = listOf(
                TimelineMeasure(
                    id = measureId,
                    userId = userId,
                    measuredAt = measureTs,
                    type = "CGM",
                    source = "dexcom",
                    data = measureData,
                    status = "VALID",
                )
            ),
            treatments = listOf(
                TimelineTreatment(
                    id = treatmentId,
                    userId = userId,
                    treatedAt = treatmentTs,
                    type = "BOLUS",
                    notes = "meal bolus",
                    data = treatmentData,
                )
            ),
            errors = listOf("upstream-timeout"),
        )

        val response = domain.toResponse()

        assertEquals(1, response.measures.size)
        assertEquals(1, response.treatments.size)
        assertEquals(listOf("upstream-timeout"), response.errors)

        val m = response.measures[0]
        assertEquals(measureId.toString(), m.id)
        assertEquals(userId.toString(), m.userId)
        assertEquals(measureTs.toString(), m.measuredAt)
        assertEquals("CGM", m.type)
        assertEquals("dexcom", m.source)
        assertEquals(measureData, m.data)
        assertEquals("VALID", m.status)

        val t = response.treatments[0]
        assertEquals(treatmentId.toString(), t.id)
        assertEquals(userId.toString(), t.userId)
        assertEquals(treatmentTs.toString(), t.treatedAt)
        assertEquals("BOLUS", t.type)
        assertEquals("meal bolus", t.notes)
        assertEquals(treatmentData, t.data)
    }

    @Test
    fun `should map Timeline with null source and notes`() {
        val domain = Timeline(
            measures = listOf(
                TimelineMeasure(
                    id = Uuid.random(),
                    userId = Uuid.random(),
                    measuredAt = Instant.parse("2024-01-15T08:00:00Z"),
                    type = "BGM",
                    source = null,
                    data = buildJsonObject { },
                    status = "VALID",
                )
            ),
            treatments = listOf(
                TimelineTreatment(
                    id = Uuid.random(),
                    userId = Uuid.random(),
                    treatedAt = Instant.parse("2024-01-15T08:05:00Z"),
                    type = "CARBS",
                    notes = null,
                    data = buildJsonObject { },
                )
            ),
        )

        val response = domain.toResponse()

        assertNull(response.measures[0].source)
        assertNull(response.treatments[0].notes)
    }

    @Test
    fun `should map empty Timeline`() {
        val domain = Timeline(measures = emptyList(), treatments = emptyList())
        val response = domain.toResponse()

        assertEquals(0, response.measures.size)
        assertEquals(0, response.treatments.size)
        assertEquals(emptyList(), response.errors)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Hba1cResult.toResponse()
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should map Hba1cResult with all fields`() {
        val tir = TirBreakdown(
            veryLowCount = 2,
            belowCount = 5,
            inRangeCount = 80,
            aboveCount = 10,
            highCount = 3,
            totalCount = 100,
        )
        val domain = Hba1cResult(
            hba1c = 7.2,
            meanGlucose = 162.0,
            readingCount = 288,
            tir = tir,
            warnings = listOf("low-reading-count"),
        )

        val response = domain.toResponse()

        assertEquals(7.2, response.hba1c)
        assertEquals(162.0, response.meanGlucose)
        assertEquals(288, response.readingCount)
        assertEquals(listOf("low-reading-count"), response.warnings)
        assertEquals(2, response.tir.veryLowCount)
        assertEquals(5, response.tir.belowCount)
        assertEquals(80, response.tir.inRangeCount)
        assertEquals(10, response.tir.aboveCount)
        assertEquals(3, response.tir.highCount)
        assertEquals(100, response.tir.totalCount)
    }

    @Test
    fun `should map Hba1cResult with null hba1c`() {
        val domain = Hba1cResult(
            hba1c = null,
            meanGlucose = 0.0,
            readingCount = 0,
            tir = TirBreakdown(),
        )

        val response = domain.toResponse()

        assertNull(response.hba1c)
        assertEquals(emptyList(), response.warnings)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // AgpResult.toResponse()
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should map AgpResult with bucket data`() {
        val bucket1 = AgpBucketData(
            minuteOfDay = 0,
            p10 = 70.0,
            p25 = 80.0,
            median = 100.0,
            p75 = 140.0,
            p90 = 180.0,
            count = 30,
        )
        val bucket2 = AgpBucketData(
            minuteOfDay = 60,
            p10 = null,
            p25 = null,
            median = null,
            p75 = null,
            p90 = null,
            count = 0,
        )
        val domain = AgpResult(
            bucketData = listOf(bucket1, bucket2),
            totalReadingCount = 576,
            sensorWearDays = 30,
            warnings = emptyList(),
        )

        val response = domain.toResponse()

        assertEquals(576, response.totalReadingCount)
        assertEquals(30, response.sensorWearDays)
        assertEquals(2, response.bucketData.size)

        val b1 = response.bucketData[0]
        assertEquals(0, b1.minuteOfDay)
        assertEquals(70.0, b1.p10)
        assertEquals(80.0, b1.p25)
        assertEquals(100.0, b1.median)
        assertEquals(140.0, b1.p75)
        assertEquals(180.0, b1.p90)
        assertEquals(30, b1.count)

        val b2 = response.bucketData[1]
        assertEquals(60, b2.minuteOfDay)
        assertNull(b2.p10)
        assertNull(b2.median)
        assertEquals(0, b2.count)
    }

    @Test
    fun `should map empty AgpResult`() {
        val domain = AgpResult(bucketData = emptyList())
        val response = domain.toResponse()

        assertEquals(0, response.bucketData.size)
        assertEquals(0, response.totalReadingCount)
        assertEquals(0, response.sensorWearDays)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ProfilesResult.toResponse()
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should map ProfilesResult with full profile including segments`() {
        val profile = ProfileSummary(
            id = "profile-1",
            userId = "user-1",
            status = "ACTIVE",
            name = "Default Profile",
            createdAt = "2024-01-01T00:00:00Z",
            validFrom = "2024-01-02T00:00:00Z",
            previousProfileId = "profile-0",
            activatedAt = "2024-01-02T10:00:00Z",
            archivedAt = null,
            insulinType = "NovoRapid",
            durationOfAction = 240,
            basal = listOf(BasalSegment("00:00", 0.8), BasalSegment("06:00", 1.2)),
            icr = listOf(RatioSegment("00:00", 10.0)),
            isf = listOf(RatioSegment("00:00", 50.0)),
            targets = listOf(TargetSegment("00:00", 80.0, 130.0)),
        )
        val domain = ProfilesResult(profiles = listOf(profile))

        val response = domain.toResponse()

        assertEquals(1, response.profiles.size)
        val dto = response.profiles[0]
        assertEquals("profile-1", dto.id)
        assertEquals("user-1", dto.userId)
        assertEquals("ACTIVE", dto.status)
        assertEquals("Default Profile", dto.name)
        assertEquals("2024-01-01T00:00:00Z", dto.createdAt)
        assertEquals("2024-01-02T00:00:00Z", dto.validFrom)
        assertEquals("profile-0", dto.previousProfileId)
        assertEquals("2024-01-02T10:00:00Z", dto.activatedAt)
        assertNull(dto.archivedAt)
        assertEquals("NovoRapid", dto.insulinType)
        assertEquals(240, dto.durationOfAction)

        assertNotNull(dto.basal)
        assertEquals(2, dto.basal?.size)
        assertEquals("00:00", dto.basal?.get(0)?.startTime)
        assertEquals(0.8, dto.basal?.get(0)?.value)
        assertEquals("06:00", dto.basal?.get(1)?.startTime)
        assertEquals(1.2, dto.basal?.get(1)?.value)

        assertNotNull(dto.icr)
        assertEquals(1, dto.icr?.size)
        assertEquals("00:00", dto.icr?.get(0)?.startTime)
        assertEquals(10.0, dto.icr?.get(0)?.value)

        assertNotNull(dto.isf)
        assertEquals(1, dto.isf?.size)
        assertEquals(50.0, dto.isf?.get(0)?.value)

        assertNotNull(dto.targets)
        assertEquals(1, dto.targets?.size)
        assertEquals("00:00", dto.targets?.get(0)?.startTime)
        assertEquals(80.0, dto.targets?.get(0)?.low)
        assertEquals(130.0, dto.targets?.get(0)?.high)
    }

    @Test
    fun `should map ProfilesResult with null segment lists`() {
        val profile = ProfileSummary(
            id = "profile-2",
            userId = "user-2",
            status = "ARCHIVED",
            name = "Old Profile",
            createdAt = null,
            validFrom = null,
            previousProfileId = null,
            basal = null,
            icr = null,
            isf = null,
            targets = null,
        )
        val domain = ProfilesResult(profiles = listOf(profile))

        val response = domain.toResponse()

        val dto = response.profiles[0]
        assertNull(dto.basal)
        assertNull(dto.icr)
        assertNull(dto.isf)
        assertNull(dto.targets)
        assertNull(dto.createdAt)
        assertNull(dto.validFrom)
        assertNull(dto.previousProfileId)
    }

    @Test
    fun `should map empty ProfilesResult`() {
        val domain = ProfilesResult(profiles = emptyList())
        val response = domain.toResponse()
        assertEquals(0, response.profiles.size)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // DeviceAge.toResponse()
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should map DeviceAge with all timestamps`() {
        val domain = DeviceAge(
            catheterChangedAt = "2024-01-10T09:00:00Z",
            reservoirChangedAt = "2024-01-11T09:00:00Z",
            sensorInsertedAt = "2024-01-12T09:00:00Z",
            batteryChangedAt = "2024-01-13T09:00:00Z",
        )

        val response = domain.toResponse()

        assertEquals("2024-01-10T09:00:00Z", response.catheterChangedAt)
        assertEquals("2024-01-11T09:00:00Z", response.reservoirChangedAt)
        assertEquals("2024-01-12T09:00:00Z", response.sensorInsertedAt)
        assertEquals("2024-01-13T09:00:00Z", response.batteryChangedAt)
    }

    @Test
    fun `should map DeviceAge with all null timestamps`() {
        val domain = DeviceAge(
            catheterChangedAt = null,
            reservoirChangedAt = null,
            sensorInsertedAt = null,
            batteryChangedAt = null,
        )

        val response = domain.toResponse()

        assertNull(response.catheterChangedAt)
        assertNull(response.reservoirChangedAt)
        assertNull(response.sensorInsertedAt)
        assertNull(response.batteryChangedAt)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // DeviceStatus.toResponse()
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should map DeviceStatus with all fields`() {
        val domain = DeviceStatus(
            id = "status-1",
            userId = "user-1",
            recordedAt = "2024-01-15T10:00:00Z",
            device = "PUMP",
            pumpName = "Medtronic 670G",
            reservoirUnits = 180.5,
            batteryLevel = 85,
            pumpConnected = true,
        )

        val response = domain.toResponse()

        assertEquals("status-1", response.id)
        assertEquals("user-1", response.userId)
        assertEquals("2024-01-15T10:00:00Z", response.recordedAt)
        assertEquals("PUMP", response.device)
        assertEquals("Medtronic 670G", response.pumpName)
        assertEquals(180.5, response.reservoirUnits)
        assertEquals(85, response.batteryLevel)
        assertEquals(true, response.pumpConnected)
    }

    @Test
    fun `should map DeviceStatus with nullable fields as null`() {
        val domain = DeviceStatus(
            id = "status-2",
            userId = "user-2",
            recordedAt = "2024-01-15T10:00:00Z",
            device = "CGM",
            pumpName = null,
            reservoirUnits = null,
            batteryLevel = null,
            pumpConnected = null,
        )

        val response = domain.toResponse()

        assertNull(response.pumpName)
        assertNull(response.reservoirUnits)
        assertNull(response.batteryLevel)
        assertNull(response.pumpConnected)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // DeviceUsageResult.toResponse()
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should map DeviceUsageResult with all values`() {
        val domain = DeviceUsageResult(
            userId = "user-1",
            avgSensorDays = 10.2,
            stddevSensorDays = 1.5,
            avgCatheterDays = 3.1,
            stddevCatheterDays = 0.4,
            avgReservoirDays = 3.0,
            stddevReservoirDays = 0.3,
            avgBatteryDays = 90.0,
            stddevBatteryDays = 5.0,
        )

        val response = domain.toResponse()

        assertEquals("user-1", response.userId)
        assertEquals(10.2, response.avgSensorDays)
        assertEquals(1.5, response.stddevSensorDays)
        assertEquals(3.1, response.avgCatheterDays)
        assertEquals(0.4, response.stddevCatheterDays)
        assertEquals(3.0, response.avgReservoirDays)
        assertEquals(0.3, response.stddevReservoirDays)
        assertEquals(90.0, response.avgBatteryDays)
        assertEquals(5.0, response.stddevBatteryDays)
    }

    @Test
    fun `should map DeviceUsageResult with all null averages`() {
        val domain = DeviceUsageResult(
            userId = "user-1",
            avgSensorDays = null,
            stddevSensorDays = null,
            avgCatheterDays = null,
            stddevCatheterDays = null,
            avgReservoirDays = null,
            stddevReservoirDays = null,
            avgBatteryDays = null,
            stddevBatteryDays = null,
        )

        val response = domain.toResponse()

        assertNull(response.avgSensorDays)
        assertNull(response.stddevSensorDays)
        assertNull(response.avgCatheterDays)
        assertNull(response.stddevCatheterDays)
        assertNull(response.avgReservoirDays)
        assertNull(response.stddevReservoirDays)
        assertNull(response.avgBatteryDays)
        assertNull(response.stddevBatteryDays)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // DailyTrendResult.toResponse()
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should map DailyTrendResult with days and hours`() {
        val row = HourlyTrendRow(
            hour = 8,
            meanGlucose = 130.0,
            trendPercent = 5.0,
            trendZone = "rising",
            zone = "inRange",
            basalRateIePerH = 1.0,
            carbsG = 20.0,
        )
        val day = DailyTrendDay(
            date = "2024-01-15",
            hours = listOf(row),
        )
        val domain = DailyTrendResult(
            days = listOf(day),
            warnings = listOf("no-profile"),
        )

        val response = domain.toResponse()

        assertEquals(1, response.days.size)
        assertEquals(listOf("no-profile"), response.warnings)

        val dayDto = response.days[0]
        assertEquals("2024-01-15", dayDto.date)
        assertEquals(1, dayDto.hours.size)

        val hourDto = dayDto.hours[0]
        assertEquals(8, hourDto.hour)
        assertEquals(130.0, hourDto.meanGlucose)
        assertEquals(5.0, hourDto.trendPercent)
        assertEquals("rising", hourDto.trendZone)
        assertEquals("inRange", hourDto.zone)
        assertEquals(1.0, hourDto.basalRateIePerH)
        assertEquals(20.0, hourDto.carbsG)
    }

    @Test
    fun `should map DailyTrendResult with null hourly fields`() {
        val row = HourlyTrendRow(
            hour = 0,
            meanGlucose = null,
            trendPercent = null,
            trendZone = null,
            zone = null,
            basalRateIePerH = null,
            carbsG = 0.0,
        )
        val domain = DailyTrendResult(
            days = listOf(DailyTrendDay(date = "2024-01-15", hours = listOf(row))),
        )

        val response = domain.toResponse()

        val hourDto = response.days[0].hours[0]
        assertNull(hourDto.meanGlucose)
        assertNull(hourDto.trendPercent)
        assertNull(hourDto.trendZone)
        assertNull(hourDto.zone)
        assertNull(hourDto.basalRateIePerH)
        assertEquals(0.0, hourDto.carbsG)
    }

    @Test
    fun `should map empty DailyTrendResult`() {
        val domain = DailyTrendResult(days = emptyList())
        val response = domain.toResponse()
        assertEquals(0, response.days.size)
        assertEquals(emptyList(), response.warnings)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // DailyStatsResult.toResponse()
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should map DailyStatsResult with rows and summary`() {
        val row = DailyStatRow(
            date = "2024-01-15",
            cgmCount = 288,
            veryLowPercent = 1.0,
            lowPercent = 5.0,
            inRangePercent = 75.0,
            highPercent = 15.0,
            veryHighPercent = 4.0,
            p25 = 90.0,
            median = 115.0,
            p75 = 160.0,
            sd = 35.0,
            eHbA1c = 7.1,
        )
        val summary = DailyStatRow(
            date = "summary",
            cgmCount = 8640,
            veryLowPercent = 1.5,
            lowPercent = 4.5,
            inRangePercent = 72.0,
            highPercent = 18.0,
            veryHighPercent = 4.0,
            p25 = 95.0,
            median = 120.0,
            p75 = 165.0,
            sd = 40.0,
            eHbA1c = 7.3,
        )
        val domain = DailyStatsResult(
            rows = listOf(row),
            summary = summary,
            warnings = listOf("partial-data"),
        )

        val response = domain.toResponse()

        assertEquals(1, response.rows.size)
        assertEquals(listOf("partial-data"), response.warnings)

        val rowDto = response.rows[0]
        assertEquals("2024-01-15", rowDto.date)
        assertEquals(288, rowDto.cgmCount)
        assertEquals(1.0, rowDto.veryLowPercent)
        assertEquals(5.0, rowDto.lowPercent)
        assertEquals(75.0, rowDto.inRangePercent)
        assertEquals(15.0, rowDto.highPercent)
        assertEquals(4.0, rowDto.veryHighPercent)
        assertEquals(90.0, rowDto.p25)
        assertEquals(115.0, rowDto.median)
        assertEquals(160.0, rowDto.p75)
        assertEquals(35.0, rowDto.sd)
        assertEquals(7.1, rowDto.eHbA1c)

        val summaryDto = response.summary
        assertEquals("summary", summaryDto.date)
        assertEquals(8640, summaryDto.cgmCount)
        assertEquals(7.3, summaryDto.eHbA1c)
    }

    @Test
    fun `should map DailyStatsResult row with all null percentages`() {
        val row = DailyStatRow(
            date = "2024-01-15",
            cgmCount = 0,
            veryLowPercent = null,
            lowPercent = null,
            inRangePercent = null,
            highPercent = null,
            veryHighPercent = null,
            p25 = null,
            median = null,
            p75 = null,
            sd = null,
            eHbA1c = null,
        )
        val summary = DailyStatRow(
            date = "summary",
            cgmCount = 0,
            veryLowPercent = null,
            lowPercent = null,
            inRangePercent = null,
            highPercent = null,
            veryHighPercent = null,
            p25 = null,
            median = null,
            p75 = null,
            sd = null,
            eHbA1c = null,
        )
        val domain = DailyStatsResult(rows = listOf(row), summary = summary)

        val response = domain.toResponse()

        val rowDto = response.rows[0]
        assertNull(rowDto.veryLowPercent)
        assertNull(rowDto.lowPercent)
        assertNull(rowDto.inRangePercent)
        assertNull(rowDto.highPercent)
        assertNull(rowDto.veryHighPercent)
        assertNull(rowDto.p25)
        assertNull(rowDto.median)
        assertNull(rowDto.p75)
        assertNull(rowDto.sd)
        assertNull(rowDto.eHbA1c)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ReportSummaryResult.toResponse()
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should map ReportSummaryResult with all fields`() {
        val tirProfile = TirResult(
            veryLow = TirZone(count = 5, percent = 1.7),
            low = TirZone(count = 15, percent = 5.2),
            inRange = TirZone(count = 230, percent = 79.9),
            high = TirZone(count = 30, percent = 10.4),
            veryHigh = TirZone(count = 8, percent = 2.8),
            customTirFallback = false,
        )
        val tirStandard = TirResult(
            veryLow = TirZone(count = 6, percent = 2.1),
            low = TirZone(count = 20, percent = 6.9),
            inRange = TirZone(count = 215, percent = 74.7),
            high = TirZone(count = 40, percent = 13.9),
            veryHigh = TirZone(count = 7, percent = 2.4),
            customTirFallback = true,
        )
        val domain = ReportSummaryResult(
            displayName = "Sarah",
            daysAnalysed = 30,
            cgmReadingCount = 8640,
            cgmIntervalMinutes = 5,
            insulinTypes = listOf("NovoRapid"),
            insulinChanges = 10,
            avgDaysPerCartridge = 3.0,
            siteChanges = 10,
            avgDaysPerSite = 3.0,
            sensorInserts = 3,
            avgDaysPerSensor = 10.0,
            tirProfile = tirProfile,
            tirStandard = tirStandard,
            minGlucose = 52.0,
            maxGlucose = 298.0,
            meanGlucose = 145.0,
            sd = 38.0,
            gvi = 1.4,
            pgs = 56.0,
            gri = 35.0,
            griZone = "low",
            eHbA1c = 7.1,
            avgCarbsPerDayG = 150.0,
            avgBolusPerDayIe = 20.0,
            bolusPercent = 55.0,
            avgBasalPerDayIe = 16.0,
            basalPercent = 45.0,
            avgTotalInsulinPerDayIe = 36.0,
            warnings = listOf("limited-cgm-data"),
        )

        val response = domain.toResponse()

        assertEquals("Sarah", response.displayName)
        assertEquals(30, response.daysAnalysed)
        assertEquals(8640, response.cgmReadingCount)
        assertEquals(5, response.cgmIntervalMinutes)
        assertEquals(listOf("NovoRapid"), response.insulinTypes)
        assertEquals(10, response.insulinChanges)
        assertEquals(3.0, response.avgDaysPerCartridge)
        assertEquals(10, response.siteChanges)
        assertEquals(3.0, response.avgDaysPerSite)
        assertEquals(3, response.sensorInserts)
        assertEquals(10.0, response.avgDaysPerSensor)
        assertEquals(52.0, response.minGlucose)
        assertEquals(298.0, response.maxGlucose)
        assertEquals(145.0, response.meanGlucose)
        assertEquals(38.0, response.sd)
        assertEquals(1.4, response.gvi)
        assertEquals(56.0, response.pgs)
        assertEquals(35.0, response.gri)
        assertEquals("low", response.griZone)
        assertEquals(7.1, response.eHbA1c)
        assertEquals(150.0, response.avgCarbsPerDayG)
        assertEquals(20.0, response.avgBolusPerDayIe)
        assertEquals(55.0, response.bolusPercent)
        assertEquals(16.0, response.avgBasalPerDayIe)
        assertEquals(45.0, response.basalPercent)
        assertEquals(36.0, response.avgTotalInsulinPerDayIe)
        assertEquals(listOf("limited-cgm-data"), response.warnings)

        // tirProfile
        assertEquals(5, response.tirProfile.veryLow.count)
        assertEquals(1.7, response.tirProfile.veryLow.percent)
        assertEquals(230, response.tirProfile.inRange.count)
        assertEquals(false, response.tirProfile.customTirFallback)

        // tirStandard
        assertEquals(true, response.tirStandard.customTirFallback)
        assertEquals(215, response.tirStandard.inRange.count)
    }

    @Test
    fun `should map ReportSummaryResult with all nullable fields as null`() {
        val emptyTir = TirResult(
            veryLow = TirZone(0, 0.0),
            low = TirZone(0, 0.0),
            inRange = TirZone(0, 0.0),
            high = TirZone(0, 0.0),
            veryHigh = TirZone(0, 0.0),
        )
        val domain = ReportSummaryResult(
            displayName = "Mike",
            daysAnalysed = 0,
            cgmReadingCount = 0,
            cgmIntervalMinutes = 5,
            insulinTypes = emptyList(),
            insulinChanges = 0,
            avgDaysPerCartridge = null,
            siteChanges = 0,
            avgDaysPerSite = null,
            sensorInserts = 0,
            avgDaysPerSensor = null,
            tirProfile = emptyTir,
            tirStandard = emptyTir,
            minGlucose = null,
            maxGlucose = null,
            meanGlucose = null,
            sd = null,
            gvi = null,
            pgs = null,
            gri = null,
            griZone = null,
            eHbA1c = null,
            avgCarbsPerDayG = null,
            avgBolusPerDayIe = null,
            bolusPercent = null,
            avgBasalPerDayIe = null,
            basalPercent = null,
            avgTotalInsulinPerDayIe = null,
        )

        val response = domain.toResponse()

        assertNull(response.avgDaysPerCartridge)
        assertNull(response.avgDaysPerSite)
        assertNull(response.avgDaysPerSensor)
        assertNull(response.minGlucose)
        assertNull(response.maxGlucose)
        assertNull(response.meanGlucose)
        assertNull(response.sd)
        assertNull(response.gvi)
        assertNull(response.pgs)
        assertNull(response.gri)
        assertNull(response.griZone)
        assertNull(response.eHbA1c)
        assertNull(response.avgCarbsPerDayG)
        assertNull(response.avgBolusPerDayIe)
        assertNull(response.bolusPercent)
        assertNull(response.avgBasalPerDayIe)
        assertNull(response.basalPercent)
        assertNull(response.avgTotalInsulinPerDayIe)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // GlucoseDistributionResult.toResponse()
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should map GlucoseDistributionResult`() {
        val buckets = listOf(
            GlucoseBucket(lowerBound = 0.0, upperBound = 54.0, count = 10, percent = 3.5, zone = "veryLow"),
            GlucoseBucket(lowerBound = 54.0, upperBound = 70.0, count = 15, percent = 5.2, zone = "low"),
            GlucoseBucket(lowerBound = 70.0, upperBound = 180.0, count = 230, percent = 79.9, zone = "inRange"),
        )
        val zonePercents = ZonePercents(
            veryLow = 3.5,
            low = 5.2,
            inRange = 79.9,
            high = 8.7,
            veryHigh = 2.7,
        )
        val domain = GlucoseDistributionResult(
            buckets = buckets,
            zonePercents = zonePercents,
            unit = "mg/dL",
            totalCount = 288,
            warnings = emptyList(),
        )

        val response = domain.toResponse()

        assertEquals(3, response.buckets.size)
        assertEquals("mg/dL", response.unit)
        assertEquals(288, response.totalCount)

        val b0 = response.buckets[0]
        assertEquals(0.0, b0.lowerBound)
        assertEquals(54.0, b0.upperBound)
        assertEquals(10, b0.count)
        assertEquals(3.5, b0.percent)
        assertEquals("veryLow", b0.zone)

        assertEquals(3.5, response.zonePercents.veryLow)
        assertEquals(5.2, response.zonePercents.low)
        assertEquals(79.9, response.zonePercents.inRange)
        assertEquals(8.7, response.zonePercents.high)
        assertEquals(2.7, response.zonePercents.veryHigh)
    }

    @Test
    fun `should map empty GlucoseDistributionResult`() {
        val domain = GlucoseDistributionResult(
            buckets = emptyList(),
            zonePercents = ZonePercents(0.0, 0.0, 0.0, 0.0, 0.0),
            unit = "mmol/L",
            totalCount = 0,
        )

        val response = domain.toResponse()

        assertEquals(0, response.buckets.size)
        assertEquals("mmol/L", response.unit)
        assertEquals(0, response.totalCount)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // CgpResult.toResponse()
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should map CgpResult with all fields`() {
        val domain = CgpResult(
            tor = 180.0,
            varK = 35.0,
            hypoIntensity = 12.5,
            hyperIntensity = 25.0,
            meanGlucose = 145.0,
            normTor = 0.75,
            normVarK = 0.65,
            normHypo = 0.80,
            normHyper = 0.70,
            normMeanGlucose = 0.85,
            refTor = 0.0,
            refVarK = 20.0,
            refHypo = 0.0,
            refHyper = 0.0,
            refMeanGlucose = 90.0,
            pgr = 3.5,
            pgrRisk = "moderate",
            warnings = listOf("insufficient-data"),
        )

        val response = domain.toResponse()

        assertEquals(180.0, response.tor)
        assertEquals(35.0, response.varK)
        assertEquals(12.5, response.hypoIntensity)
        assertEquals(25.0, response.hyperIntensity)
        assertEquals(145.0, response.meanGlucose)
        assertEquals(0.75, response.normTor)
        assertEquals(0.65, response.normVarK)
        assertEquals(0.80, response.normHypo)
        assertEquals(0.70, response.normHyper)
        assertEquals(0.85, response.normMeanGlucose)
        assertEquals(0.0, response.refTor)
        assertEquals(20.0, response.refVarK)
        assertEquals(0.0, response.refHypo)
        assertEquals(0.0, response.refHyper)
        assertEquals(90.0, response.refMeanGlucose)
        assertEquals(3.5, response.pgr)
        assertEquals("moderate", response.pgrRisk)
        assertEquals(listOf("insufficient-data"), response.warnings)
    }

    @Test
    fun `should map CgpResult with empty warnings`() {
        val domain = CgpResult(
            tor = 0.0,
            varK = 0.0,
            hypoIntensity = 0.0,
            hyperIntensity = 0.0,
            meanGlucose = 90.0,
            normTor = 1.0,
            normVarK = 1.0,
            normHypo = 1.0,
            normHyper = 1.0,
            normMeanGlucose = 1.0,
            refTor = 0.0,
            refVarK = 20.0,
            refHypo = 0.0,
            refHyper = 0.0,
            refMeanGlucose = 90.0,
            pgr = 5.0,
            pgrRisk = "very_low",
        )

        val response = domain.toResponse()

        assertEquals("very_low", response.pgrRisk)
        assertEquals(5.0, response.pgr)
        assertEquals(emptyList(), response.warnings)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Multiple measures / treatments ordering
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should preserve order of measures in Timeline response`() {
        val ids = (1..3).map { Uuid.random() }
        val measures = ids.map { id ->
            TimelineMeasure(
                id = id,
                userId = Uuid.random(),
                measuredAt = Instant.parse("2024-01-15T08:00:00Z"),
                type = "CGM",
                source = null,
                data = buildJsonObject { },
                status = "VALID",
            )
        }
        val domain = Timeline(measures = measures, treatments = emptyList())
        val response = domain.toResponse()

        assertEquals(3, response.measures.size)
        assertEquals(ids[0].toString(), response.measures[0].id)
        assertEquals(ids[1].toString(), response.measures[1].id)
        assertEquals(ids[2].toString(), response.measures[2].id)
    }

    @Test
    fun `should map multiple AGP buckets preserving order`() {
        val buckets = (0 until 5).map { i ->
            AgpBucketData(
                minuteOfDay = i * 30, p10 = i * 10.0, p25 = null,
                median = null, p75 = null, p90 = null, count = i,
            )
        }
        val domain = AgpResult(bucketData = buckets)
        val response = domain.toResponse()

        assertEquals(5, response.bucketData.size)
        for (i in 0 until 5) {
            assertEquals(i * 30, response.bucketData[i].minuteOfDay)
        }
    }

    @Test
    fun `should map multiple profiles in ProfilesResult preserving order`() {
        val profiles = (1..3).map { i ->
            ProfileSummary(
                id = "profile-$i",
                userId = "user-1",
                status = if (i == 3) "ACTIVE" else "ARCHIVED",
                name = "Profile $i",
                createdAt = null,
                validFrom = null,
                previousProfileId = null,
            )
        }
        val domain = ProfilesResult(profiles = profiles)
        val response = domain.toResponse()

        assertEquals(3, response.profiles.size)
        assertEquals("profile-1", response.profiles[0].id)
        assertEquals("profile-2", response.profiles[1].id)
        assertEquals("profile-3", response.profiles[2].id)
        assertEquals("ACTIVE", response.profiles[2].status)
    }
}

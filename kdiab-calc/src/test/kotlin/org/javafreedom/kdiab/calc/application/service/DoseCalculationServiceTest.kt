package org.javafreedom.kdiab.calc.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.calc.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.IcrSegment
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.IsfSegment
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.Profile
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.TargetSegment
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.calc.domain.model.CgmTrend
import org.javafreedom.kdiab.calc.domain.model.DoseRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DoseCalculationServiceTest {

    private val profilesClient = mockk<ProfilesClient>()
    private val service = DoseCalculationService(profilesClient)

    private val testProfile = Profile(
        id = "profile-123",
        userId = "user-123",
        name = "Test Profile",
        insulinType = "rapid",
        durationOfAction = 180,
        status = Profile.Status.ACTIVE,
        isf = listOf(IsfSegment(startTime = "00:00", `value` = 50.0)),
        icr = listOf(IcrSegment(startTime = "00:00", `value` = 15.0)),
        targets = listOf(TargetSegment(startTime = "00:00", low = 100.0, high = 120.0)),
    )

    @Test
    fun `calculateDose returns correct breakdown for mgdL input`() = runTest {
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        val request = DoseRequest(
            currentBg = 200.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.FLAT,
            carbsGrams = 45.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        // correction = (200 - 110) / 50 = 1.8
        assertEquals(1.8, result.correctionDose)
        // carb = 45 / 15 = 3.0
        assertEquals(3.0, result.carbDose)
        // trend = 0.0
        assertEquals(0.0, result.trendAdjustment)
        // total = 1.8 + 3.0 + 0.0 = 4.8
        assertEquals(4.8, result.totalRecommended)
        assertEquals(200.0, result.breakdown.currentBgMgDl)
        assertEquals(110.0, result.breakdown.targetBgMgDl)
        assertEquals(50.0, result.breakdown.isf)
        assertEquals(15.0, result.breakdown.icr)
        assertEquals(45.0, result.breakdown.carbsGrams)
        assertEquals("profile-123", result.profileId)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `calculateDose converts mmolL to mgdL correctly`() = runTest {
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        val request = DoseRequest(
            currentBg = 11.1,
            glucoseUnit = "mmol/L",
            trend = CgmTrend.FLAT,
            carbsGrams = 0.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        // 11.1 * 18 = 199.8
        assertEquals(199.8, result.breakdown.currentBgMgDl)
    }

    @Test
    fun `calculateDose clamps total to 0 when BG below target and no carbs`() = runTest {
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        val request = DoseRequest(
            currentBg = 80.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.FLAT,
            carbsGrams = 0.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        // rawCorrection = (80 - 110) / 50 = -0.6; floored to 0 by maxOf(0.0, ...) logic
        assertEquals(0.0, result.correctionDose)
        assertEquals(0.0, result.totalRecommended)
    }

    @Test
    fun `calculateDose adds hypoglycemia warning when BG below 70 mgdL`() = runTest {
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        val request = DoseRequest(
            currentBg = 55.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.DOUBLE_DOWN,
            carbsGrams = 0.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        assertTrue(result.warnings.any { it.contains("hypoglycemic") })
        assertEquals(0.0, result.totalRecommended)
    }

    @Test
    fun `calculateDose suppresses carbDose and total to zero during hypoglycemia even when carbs entered`() = runTest {
        // Safety-critical: carbs consumed to treat a hypo must NOT receive an insulin dose.
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        val request = DoseRequest(
            currentBg = 60.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.FLAT,
            carbsGrams = 30.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        assertEquals(0.0, result.carbDose, "carbDose must be 0 during hypo — carbs are rescue treatment")
        assertEquals(0.0, result.correctionDose)
        assertEquals(0.0, result.trendAdjustment)
        assertEquals(0.0, result.totalRecommended)
        assertTrue(result.warnings.any { it.contains("hypoglycemic") })
    }

    @Test
    fun `calculateDose suppresses trend adjustment and total to zero during hypoglycemia`() = runTest {
        // DOUBLE_DOWN trend during hypo must not produce a dose recommendation.
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        val request = DoseRequest(
            currentBg = 65.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.DOUBLE_DOWN,
            carbsGrams = 20.0,
            activeIob = 2.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        assertEquals(0.0, result.carbDose)
        assertEquals(0.0, result.correctionDose)
        assertEquals(0.0, result.trendAdjustment)
        assertEquals(0.0, result.totalRecommended)
        assertTrue(result.warnings.any { it.contains("hypoglycemic") })
    }

    @Test
    fun `calculateDose applies SINGLE_UP trend adjustment scaled by ISF`() = runTest {
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        val request = DoseRequest(
            currentBg = 110.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.SINGLE_UP,
            carbsGrams = 0.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        // correction = (110 - 110) / 50 = 0, trend = 20.0 / 50 = 0.4, total = 0.4
        assertEquals(0.4, result.trendAdjustment)
        assertEquals(0.4, result.totalRecommended)
    }

    @Test
    fun `calculateDose trend adjustments are proportional to 1 over ISF`() = runTest {
        val isfValue = 40.0
        val lowIsfProfile = testProfile.copy(
            isf = listOf(IsfSegment(startTime = "00:00", `value` = isfValue)),
        )
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns lowIsfProfile

        // BG at target so correction = 0; only trend contributes
        val request = DoseRequest(
            currentBg = 110.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.DOUBLE_UP,
            carbsGrams = 0.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        // DOUBLE_UP offset = 30 mg/dL / ISF — proportional to 1/isf
        val expected = 30.0 / isfValue
        assertEquals(expected, result.trendAdjustment)
    }

    @Test
    fun `calculateDose negative trends reduce dose proportionally to ISF`() = runTest {
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        // BG at target + slightly above so correction is small positive; SINGLE_DOWN should reduce
        val request = DoseRequest(
            currentBg = 160.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.SINGLE_DOWN,
            carbsGrams = 0.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        // trend = -20.0 / 50 = -0.4
        assertEquals(-0.4, result.trendAdjustment)
    }

    @Test
    fun `calculateDose does not produce NaN when ISF is very small but positive`() = runTest {
        val tinyIsfProfile = testProfile.copy(
            isf = listOf(IsfSegment(startTime = "00:00", `value` = 0.1)),
        )
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns tinyIsfProfile

        val request = DoseRequest(
            currentBg = 110.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.SINGLE_UP,
            carbsGrams = 0.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        assertTrue(!result.trendAdjustment.isNaN())
        assertTrue(!result.trendAdjustment.isInfinite())
    }

    @Test
    fun `calculateDose throws ResourceNotFoundException when no active profile`() = runTest {
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns null

        val request = DoseRequest(
            currentBg = 150.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.FLAT,
        )

        assertFailsWith<ResourceNotFoundException> {
            service.calculateDose("user-123", request, "Bearer token", "corr-id")
        }
    }

    @Test
    fun `calculateDose selects correct segment for time of day`() = runTest {
        // Profile with different ISF/ICR per time-of-day segment:
        //   00:00 – 07:59: ISF 60, ICR 20, target 100–120
        //   08:00 – 23:59: ISF 40, ICR 12, target 110–130
        val multiSegmentProfile = testProfile.copy(
            isf = listOf(
                IsfSegment(startTime = "00:00", `value` = 60.0),
                IsfSegment(startTime = "08:00", `value` = 40.0),
            ),
            icr = listOf(
                IcrSegment(startTime = "00:00", `value` = 20.0),
                IcrSegment(startTime = "08:00", `value` = 12.0),
            ),
            targets = listOf(
                TargetSegment(startTime = "00:00", low = 100.0, high = 120.0),
                TargetSegment(startTime = "08:00", low = 110.0, high = 130.0),
            ),
        )
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns multiSegmentProfile

        // Request pinned to 14:00 UTC — falls in the 08:00 segment (ISF 40, ICR 12, target 120)
        val request = DoseRequest(
            currentBg = 180.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.FLAT,
            carbsGrams = 24.0,
            useProfileTime = "2026-01-01T14:00:00Z",
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        // correction = (180 - 120) / 40 = 1.5
        assertEquals(1.5, result.correctionDose)
        // carb = 24 / 12 = 2.0
        assertEquals(2.0, result.carbDose)
        // ISF and ICR from the 08:00 segment must be reflected in the breakdown
        assertEquals(40.0, result.breakdown.isf)
        assertEquals(12.0, result.breakdown.icr)
        assertEquals(120.0, result.breakdown.targetBgMgDl)
    }

    @Test
    fun `calculateDose uses last segment when request time is before all segment start times`() = runTest {
        // Only segment starts at 06:00; a request at 02:00 has no segment with startTime <= refTime,
        // so the service must fall back to segments.last() (the 06:00 segment).
        val lateStartProfile = testProfile.copy(
            isf = listOf(IsfSegment(startTime = "06:00", `value` = 45.0)),
            icr = listOf(IcrSegment(startTime = "06:00", `value` = 10.0)),
            targets = listOf(TargetSegment(startTime = "06:00", low = 90.0, high = 110.0)),
        )
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns lateStartProfile

        // Request pinned to 02:00 UTC — before the single segment at 06:00
        val request = DoseRequest(
            currentBg = 190.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.FLAT,
            carbsGrams = 0.0,
            useProfileTime = "2026-01-01T02:00:00Z",
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        // Falls back to last() == 06:00 segment; ISF 45, target midpoint 100
        // correction = (190 - 100) / 45 = 2.0
        assertEquals(2.0, result.correctionDose)
        assertEquals(45.0, result.breakdown.isf)
    }

    @Test
    fun `calculateDose applies high-dose warning when total recommended exceeds threshold`() = runTest {
        // Force a very high dose: BG = 1100 mg/dL, ISF = 50, target = 110 => correction = (1100-110)/50 = 19.8
        // Add carbs = 30, ICR = 1 => carbDose = 30.0; total = 49.8 which is > 20 (HIGH_DOSE_THRESHOLD)
        val lowIcrProfile = testProfile.copy(
            icr = listOf(IcrSegment(startTime = "00:00", `value` = 1.0)),
        )
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns lowIcrProfile

        val request = DoseRequest(
            currentBg = 1100.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.FLAT,
            carbsGrams = 30.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        assertTrue(result.totalRecommended > 20.0)
        assertTrue(result.warnings.any { it.contains("unusually high") })
    }

    @Test
    fun `calculateDose applies FORTY_FIVE_UP trend adjustment increasing dose`() = runTest {
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        val request = DoseRequest(
            currentBg = 110.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.FORTY_FIVE_UP,
            carbsGrams = 0.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        // correction = (110 - 110) / 50 = 0, trend = 10.0 / 50 = 0.2
        assertEquals(0.2, result.trendAdjustment)
        assertEquals(0.2, result.totalRecommended)
    }

    @Test
    fun `calculateDose applies FORTY_FIVE_DOWN trend adjustment decreasing dose`() = runTest {
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        val request = DoseRequest(
            currentBg = 160.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.FORTY_FIVE_DOWN,
            carbsGrams = 0.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        // correction = (160 - 110) / 50 = 1.0, trend = -10.0 / 50 = -0.2, total = 0.8
        assertEquals(-0.2, result.trendAdjustment)
        assertEquals(0.8, result.totalRecommended)
    }

    @Test
    fun `calculateDose throws BusinessValidationException when ISF segment value is zero`() = runTest {
        val zeroIsfProfile = testProfile.copy(
            isf = listOf(IsfSegment(startTime = "00:00", `value` = 0.0)),
        )
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns zeroIsfProfile

        val request = DoseRequest(
            currentBg = 200.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.FLAT,
        )

        assertFailsWith<BusinessValidationException> {
            service.calculateDose("user-123", request, "Bearer token", "corr-id")
        }
    }

    @Test
    fun `calculateDose throws BusinessValidationException when ICR segment value is zero and carbs entered`() = runTest {
        val zeroIcrProfile = testProfile.copy(
            icr = listOf(IcrSegment(startTime = "00:00", `value` = 0.0)),
        )
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns zeroIcrProfile

        val request = DoseRequest(
            currentBg = 150.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.FLAT,
            carbsGrams = 30.0,
        )

        assertFailsWith<BusinessValidationException> {
            service.calculateDose("user-123", request, "Bearer token", "corr-id")
        }
    }

    @Test
    fun `calculateDose converts 10 mmolL to 180 mgdL before calculation`() = runTest {
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        val request = DoseRequest(
            currentBg = 10.0,
            glucoseUnit = "mmol/L",
            trend = CgmTrend.FLAT,
            carbsGrams = 0.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        // 10.0 mmol/L * 18 = 180 mg/dL
        assertEquals(180.0, result.breakdown.currentBgMgDl)
        // correction = (180 - 110) / 50 = 1.4
        assertEquals(1.4, result.correctionDose)
    }

    @Test
    fun `calculateDose subtracts activeIob from raw correction dose`() = runTest {
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        // rawCorrection = (200 - 110) / 50 = 1.8; IOB = 1.0 => correctionDose = 0.8
        val request = DoseRequest(
            currentBg = 200.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.FLAT,
            carbsGrams = 0.0,
            activeIob = 1.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        assertEquals(0.8, result.correctionDose)
        assertEquals(0.8, result.totalRecommended)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `calculateDose floors correctionDose at 0 when activeIob exceeds raw correction`() = runTest {
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        // rawCorrection = (200 - 110) / 50 = 1.8; IOB = 3.0 => correctionDose = max(0, -1.2) = 0
        val request = DoseRequest(
            currentBg = 200.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.FLAT,
            carbsGrams = 0.0,
            activeIob = 3.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        assertEquals(0.0, result.correctionDose)
        assertEquals(0.0, result.totalRecommended)
        assertTrue(result.warnings.any { it.contains("IOB covers the full correction") })
    }
}

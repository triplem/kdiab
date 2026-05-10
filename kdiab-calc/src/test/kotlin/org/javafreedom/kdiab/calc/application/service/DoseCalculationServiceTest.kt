package org.javafreedom.kdiab.calc.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.calc.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.IcrSegment
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.IsfSegment
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.Profile
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.TargetSegment
import org.javafreedom.kdiab.calc.domain.exception.ResourceNotFoundException
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

        // correction = (80 - 110) / 50 = -0.6, clamp to 0
        assertEquals(0.0, result.totalRecommended)
        assertTrue(result.correctionDose < 0)
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
    fun `calculateDose applies SINGLE_UP trend adjustment of 1_0 units`() = runTest {
        coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

        val request = DoseRequest(
            currentBg = 110.0,
            glucoseUnit = "mg/dL",
            trend = CgmTrend.SINGLE_UP,
            carbsGrams = 0.0,
        )

        val result = service.calculateDose("user-123", request, "Bearer token", "corr-id")

        // correction = (110 - 110) / 50 = 0, trend = +1.0, total = 1.0
        assertEquals(1.0, result.trendAdjustment)
        assertEquals(1.0, result.totalRecommended)
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
}

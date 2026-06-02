package org.javafreedom.kdiab.calc.adapters.inbound.web

import org.javafreedom.kdiab.calc.domain.model.CgmTrend
import org.javafreedom.kdiab.calc.domain.model.DoseBreakdown
import org.javafreedom.kdiab.calc.domain.model.DoseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalcMapperTest {

    // ──────────────────────────────────────────────────────────────────────────
    // DoseRequestDto.toDomain()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `toDomain maps all fields correctly for FLAT trend in mgdL`() {
        val dto = DoseRequestDto(
            currentBg = 120.0,
            glucoseUnit = "mg/dL",
            trend = "FLAT",
            carbsGrams = 30.0,
            activeIob = 1.5,
            useProfileTime = "2026-01-01T08:00:00Z",
        )

        val domain = dto.toDomain()

        assertEquals(120.0, domain.currentBg)
        assertEquals("mg/dL", domain.glucoseUnit)
        assertEquals(CgmTrend.FLAT, domain.trend)
        assertEquals(30.0, domain.carbsGrams)
        assertEquals(1.5, domain.activeIob)
        assertEquals("2026-01-01T08:00:00Z", domain.useProfileTime)
    }

    @Test
    fun `toDomain maps DOUBLE_UP trend correctly`() {
        val dto = DoseRequestDto(currentBg = 180.0, glucoseUnit = "mg/dL", trend = "DOUBLE_UP")
        assertEquals(CgmTrend.DOUBLE_UP, dto.toDomain().trend)
    }

    @Test
    fun `toDomain maps DOUBLE_DOWN trend correctly`() {
        val dto = DoseRequestDto(currentBg = 60.0, glucoseUnit = "mg/dL", trend = "DOUBLE_DOWN")
        assertEquals(CgmTrend.DOUBLE_DOWN, dto.toDomain().trend)
    }

    @Test
    fun `toDomain maps SINGLE_UP trend correctly`() {
        val dto = DoseRequestDto(currentBg = 160.0, glucoseUnit = "mg/dL", trend = "SINGLE_UP")
        assertEquals(CgmTrend.SINGLE_UP, dto.toDomain().trend)
    }

    @Test
    fun `toDomain maps SINGLE_DOWN trend correctly`() {
        val dto = DoseRequestDto(currentBg = 100.0, glucoseUnit = "mg/dL", trend = "SINGLE_DOWN")
        assertEquals(CgmTrend.SINGLE_DOWN, dto.toDomain().trend)
    }

    @Test
    fun `toDomain maps FORTY_FIVE_UP trend correctly`() {
        val dto = DoseRequestDto(currentBg = 140.0, glucoseUnit = "mg/dL", trend = "FORTY_FIVE_UP")
        assertEquals(CgmTrend.FORTY_FIVE_UP, dto.toDomain().trend)
    }

    @Test
    fun `toDomain maps FORTY_FIVE_DOWN trend correctly`() {
        val dto = DoseRequestDto(currentBg = 130.0, glucoseUnit = "mg/dL", trend = "FORTY_FIVE_DOWN")
        assertEquals(CgmTrend.FORTY_FIVE_DOWN, dto.toDomain().trend)
    }

    @Test
    fun `toDomain maps NONE trend correctly`() {
        val dto = DoseRequestDto(currentBg = 100.0, glucoseUnit = "mg/dL", trend = "NONE")
        assertEquals(CgmTrend.NONE, dto.toDomain().trend)
    }

    @Test
    fun `toDomain applies default values for optional fields`() {
        val dto = DoseRequestDto(currentBg = 110.0, glucoseUnit = "mmol/L", trend = "FLAT")

        val domain = dto.toDomain()

        assertEquals(0.0, domain.carbsGrams)
        assertEquals(0.0, domain.activeIob)
        assertNull(domain.useProfileTime)
    }

    @Test
    fun `toDomain maps mmolL glucoseUnit through unchanged`() {
        val dto = DoseRequestDto(currentBg = 6.5, glucoseUnit = "mmol/L", trend = "FLAT")

        val domain = dto.toDomain()

        assertEquals("mmol/L", domain.glucoseUnit)
        assertEquals(6.5, domain.currentBg)
    }

    @Test
    fun `toDomain preserves null useProfileTime`() {
        val dto = DoseRequestDto(
            currentBg = 100.0, glucoseUnit = "mg/dL", trend = "FLAT", useProfileTime = null,
        )
        assertNull(dto.toDomain().useProfileTime)
    }

    @Test
    fun `toDomain throws IllegalArgumentException for unknown trend string`() {
        val dto = DoseRequestDto(currentBg = 100.0, glucoseUnit = "mg/dL", trend = "RISING_FAST")

        val ex = assertFailsWith<IllegalArgumentException> { dto.toDomain() }
        assertTrue(ex.message!!.contains("Unknown CGM trend: RISING_FAST"))
    }

    @Test
    fun `toDomain throws IllegalArgumentException for empty trend string`() {
        val dto = DoseRequestDto(currentBg = 100.0, glucoseUnit = "mg/dL", trend = "")
        assertFailsWith<IllegalArgumentException> { dto.toDomain() }
    }

    @Test
    fun `toDomain throws IllegalArgumentException for lowercase trend string`() {
        val dto = DoseRequestDto(currentBg = 100.0, glucoseUnit = "mg/dL", trend = "flat")
        assertFailsWith<IllegalArgumentException> { dto.toDomain() }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DoseResult.toDto()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `toDto maps all DoseResult fields correctly`() {
        val result = DoseResult(
            correctionDose = 1.8,
            carbDose = 3.0,
            trendAdjustment = 0.0,
            totalRecommended = 4.8,
            breakdown = buildBreakdown(trend = CgmTrend.FLAT, carbsGrams = 45.0),
            profileId = "profile-abc",
            warnings = listOf("unusually high dose"),
            recommendedWaitMinutes = 20,
        )

        val dto = result.toDto()

        assertEquals(1.8, dto.correctionDose)
        assertEquals(3.0, dto.carbDose)
        assertEquals(0.0, dto.trendAdjustment)
        assertEquals(4.8, dto.totalRecommended)
        assertEquals("profile-abc", dto.profileId)
        assertEquals(listOf("unusually high dose"), dto.warnings)
        assertEquals(20, dto.recommendedWaitMinutes)
    }

    @Test
    fun `toDto maps empty warnings list`() {
        val dto = buildDoseResult().toDto()
        assertTrue(dto.warnings.isEmpty())
    }

    @Test
    fun `toDto maps multiple warnings`() {
        val result = buildDoseResult(warnings = listOf("unusually high dose", "Capped at 30 U"))

        assertEquals(2, result.toDto().warnings.size)
        assertTrue(result.toDto().warnings.contains("unusually high dose"))
        assertTrue(result.toDto().warnings.contains("Capped at 30 U"))
    }

    @Test
    fun `toDto maps null recommendedWaitMinutes`() {
        val dto = buildDoseResult(recommendedWaitMinutes = null).toDto()
        assertNull(dto.recommendedWaitMinutes)
    }

    @Test
    fun `toDto maps integer recommendedWaitMinutes`() {
        val dto = buildDoseResult(recommendedWaitMinutes = 15).toDto()
        assertEquals(15, dto.recommendedWaitMinutes)
    }

    @Test
    fun `toDto maps zero doses correctly`() {
        val result = DoseResult(
            correctionDose = 0.0,
            carbDose = 0.0,
            trendAdjustment = 0.0,
            totalRecommended = 0.0,
            breakdown = buildBreakdown(),
            profileId = "profile-xyz",
            warnings = emptyList(),
            recommendedWaitMinutes = null,
        )

        val dto = result.toDto()

        assertEquals(0.0, dto.correctionDose)
        assertEquals(0.0, dto.carbDose)
        assertEquals(0.0, dto.trendAdjustment)
        assertEquals(0.0, dto.totalRecommended)
    }

    @Test
    fun `toDto maps negative trendAdjustment`() {
        val result = buildDoseResult(trendAdjustment = -0.4, totalRecommended = 0.6)

        assertEquals(-0.4, result.toDto().trendAdjustment)
        assertEquals(0.6, result.toDto().totalRecommended)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DoseBreakdown.toDto()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `breakdown toDto maps all fields including trend name`() {
        val breakdown = DoseBreakdown(
            currentBgMgDl = 180.0,
            targetBgMgDl = 110.0,
            isf = 40.0,
            icr = 12.0,
            trend = CgmTrend.SINGLE_UP,
            carbsGrams = 30.0,
        )

        val dto = breakdown.toDto()

        assertEquals(180.0, dto.currentBgMgDl)
        assertEquals(110.0, dto.targetBgMgDl)
        assertEquals(40.0, dto.isf)
        assertEquals(12.0, dto.icr)
        assertEquals("SINGLE_UP", dto.trend)
        assertEquals(30.0, dto.carbsGrams)
    }

    @Test
    fun `breakdown toDto serialises DOUBLE_DOWN trend as string name`() {
        assertEquals("DOUBLE_DOWN", buildBreakdown(trend = CgmTrend.DOUBLE_DOWN).toDto().trend)
    }

    @Test
    fun `breakdown toDto serialises FORTY_FIVE_UP trend as string name`() {
        assertEquals("FORTY_FIVE_UP", buildBreakdown(trend = CgmTrend.FORTY_FIVE_UP).toDto().trend)
    }

    @Test
    fun `breakdown toDto serialises NONE trend as string name`() {
        assertEquals("NONE", buildBreakdown(trend = CgmTrend.NONE).toDto().trend)
    }

    @Test
    fun `breakdown toDto maps zero carbsGrams`() {
        val breakdown = DoseBreakdown(
            currentBgMgDl = 100.0,
            targetBgMgDl = 110.0,
            isf = 50.0,
            icr = 15.0,
            trend = CgmTrend.FLAT,
            carbsGrams = 0.0,
        )
        assertEquals(0.0, breakdown.toDto().carbsGrams)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Round-trip: DoseResult → DoseResponseDto verifies breakdown delegation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `toDto delegates breakdown conversion so all breakdown fields are present`() {
        val breakdown = DoseBreakdown(
            currentBgMgDl = 160.0,
            targetBgMgDl = 100.0,
            isf = 35.0,
            icr = 10.0,
            trend = CgmTrend.FLAT,
            carbsGrams = 20.0,
        )
        val result = DoseResult(
            correctionDose = 1.714,
            carbDose = 2.0,
            trendAdjustment = 0.0,
            totalRecommended = 3.714,
            breakdown = breakdown,
            profileId = "p1",
            warnings = emptyList(),
        )

        val dto = result.toDto()

        assertEquals(160.0, dto.breakdown.currentBgMgDl)
        assertEquals(100.0, dto.breakdown.targetBgMgDl)
        assertEquals(35.0, dto.breakdown.isf)
        assertEquals(10.0, dto.breakdown.icr)
        assertEquals("FLAT", dto.breakdown.trend)
        assertEquals(20.0, dto.breakdown.carbsGrams)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun buildBreakdown(
        trend: CgmTrend = CgmTrend.FLAT,
        carbsGrams: Double = 0.0,
    ) = DoseBreakdown(
        currentBgMgDl = 120.0,
        targetBgMgDl = 110.0,
        isf = 50.0,
        icr = 15.0,
        trend = trend,
        carbsGrams = carbsGrams,
    )

    private fun buildDoseResult(
        trendAdjustment: Double = 0.0,
        totalRecommended: Double = 1.0,
        warnings: List<String> = emptyList(),
        recommendedWaitMinutes: Int? = null,
    ) = DoseResult(
        correctionDose = 1.0,
        carbDose = 0.0,
        trendAdjustment = trendAdjustment,
        totalRecommended = totalRecommended,
        breakdown = buildBreakdown(),
        profileId = "profile-test",
        warnings = warnings,
        recommendedWaitMinutes = recommendedWaitMinutes,
    )
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.domain.model

import kotlinx.datetime.LocalTime
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class ProfileValidationTest {

    @Test
    fun `should pass validation for valid profile`() {
        val profile = createValidProfile()
        profile.validate()
    }

    @Test
    fun `should fail when basal values are negative`() {
        val profile = createValidProfile().copy(
            basal = listOf(BasalSegment(LocalTime(0, 0), -0.5))
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should fail when basal list is empty`() {
        val profile = createValidProfile().copy(
            basal = emptyList()
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should fail when basal does not start at 00-00`() {
        val profile = createValidProfile().copy(
            basal = listOf(BasalSegment(LocalTime(1, 0), 0.5))
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should fail when icr values are negative`() {
        val profile = createValidProfile().copy(
            icr = listOf(IcrSegment(LocalTime(0, 0), -10.0))
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should fail when isf values are negative`() {
        val profile = createValidProfile().copy(
            isf = listOf(IsfSegment(LocalTime(0, 0), -20.0))
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should fail when targets are negative`() {
        val profile = createValidProfile().copy(
            targets = listOf(TargetSegment(LocalTime(0, 0), -80.0, 100.0))
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should fail when total daily basal exceeds 150 U per day`() {
        // 24 hours * 7 U/h = 168 U/day -- exceeds the 150 U/day clinical safety limit
        val profile = createValidProfile().copy(
            basal = listOf(BasalSegment(LocalTime(0, 0), 7.0))
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should pass validation when total daily basal is exactly 150 U per day`() {
        // 24 hours * 6.25 U/h = 150.0 U/day -- exactly at the boundary, must pass
        val profile = createValidProfile().copy(
            basal = listOf(BasalSegment(LocalTime(0, 0), 6.25))
        )
        profile.validate() // must not throw
    }

    @Test
    fun `should fail when multi-segment basal sums to more than 150 U per day`() {
        // 12 h * 10 U/h + 12 h * 3 U/h = 120 + 36 = 156 U/day -- exceeds limit
        val profile = createValidProfile().copy(
            basal = listOf(
                BasalSegment(LocalTime(0, 0), 10.0),
                BasalSegment(LocalTime(12, 0), 3.0)
            )
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should throw when ICR is zero`() {
        val profile = createValidProfile().copy(
            icr = listOf(IcrSegment(LocalTime(0, 0), 0.0))
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should throw when ISF is zero`() {
        val profile = createValidProfile().copy(
            isf = listOf(IsfSegment(LocalTime(0, 0), 0.0))
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should throw when DIA is below 120 minutes`() {
        val profile = createValidProfile().copy(durationOfAction = 119)
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should throw when DIA exceeds 480 minutes`() {
        val profile = createValidProfile().copy(durationOfAction = 481)
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should pass validation when DIA is exactly at minimum boundary of 120 minutes`() {
        val profile = createValidProfile().copy(durationOfAction = 120)
        profile.validate()
    }

    @Test
    fun `should pass validation when DIA is exactly at maximum boundary of 480 minutes`() {
        val profile = createValidProfile().copy(durationOfAction = 480)
        profile.validate()
    }

    @Test
    fun `should fail when carbAbsorptionRateGPerHour is zero`() {
        val profile = createValidProfile().copy(carbAbsorptionRateGPerHour = 0.0)
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should fail when carbAbsorptionRateGPerHour is negative`() {
        val profile = createValidProfile().copy(carbAbsorptionRateGPerHour = -5.0)
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should fail when carbAbsorptionRateGPerHour exceeds maximum`() {
        val profile = createValidProfile().copy(carbAbsorptionRateGPerHour = 101.0)
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should pass validation when carbAbsorptionRateGPerHour is within bounds`() {
        listOf(1.0, 20.0, 40.0, 100.0).forEach { rate ->
            createValidProfile().copy(carbAbsorptionRateGPerHour = rate).validate()
        }
    }

    @Test
    fun `should pass validation when carbAbsorptionRateGPerHour is null`() {
        val profile = createValidProfile().copy(carbAbsorptionRateGPerHour = null)
        profile.validate()
    }

    // --- analysisLow / analysisHigh TIR threshold validation ---

    @Test
    fun `should pass validation when analysisLow and analysisHigh are null`() {
        val profile = createValidProfile().copy(analysisLow = null, analysisHigh = null)
        profile.validate()
    }

    @Test
    fun `should pass validation for valid analysisLow and analysisHigh`() {
        val profile = createValidProfile().copy(analysisLow = 70.0, analysisHigh = 180.0)
        profile.validate()
    }

    @Test
    fun `should pass validation when analysisLow is at minimum boundary`() {
        val profile = createValidProfile().copy(analysisLow = 54.0, analysisHigh = 180.0)
        profile.validate()
    }

    @Test
    fun `should pass validation when analysisHigh is at maximum boundary`() {
        val profile = createValidProfile().copy(analysisLow = 70.0, analysisHigh = 400.0)
        profile.validate()
    }

    @Test
    fun `should fail when analysisLow is below minimum of 54`() {
        val profile = createValidProfile().copy(analysisLow = 53.9, analysisHigh = 180.0)
        val ex = assertFailsWith<BusinessValidationException> { profile.validate() }
        assert(ex.message?.contains("analysisLow") == true)
    }

    @Test
    fun `should fail when analysisLow is far below minimum`() {
        val profile = createValidProfile().copy(analysisLow = -999.0, analysisHigh = 180.0)
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should fail when analysisLow exceeds maximum of 180`() {
        val profile = createValidProfile().copy(analysisLow = 180.1, analysisHigh = 300.0)
        val ex = assertFailsWith<BusinessValidationException> { profile.validate() }
        assert(ex.message?.contains("analysisLow") == true)
    }

    @Test
    fun `should fail when analysisHigh is below minimum of 120`() {
        val profile = createValidProfile().copy(analysisLow = 70.0, analysisHigh = 119.9)
        val ex = assertFailsWith<BusinessValidationException> { profile.validate() }
        assert(ex.message?.contains("analysisHigh") == true)
    }

    @Test
    fun `should fail when analysisHigh exceeds maximum of 400`() {
        val profile = createValidProfile().copy(analysisLow = 70.0, analysisHigh = 5000.0)
        val ex = assertFailsWith<BusinessValidationException> { profile.validate() }
        assert(ex.message?.contains("analysisHigh") == true)
    }

    @Test
    fun `should fail when analysisLow equals analysisHigh`() {
        val profile = createValidProfile().copy(analysisLow = 150.0, analysisHigh = 150.0)
        val ex = assertFailsWith<BusinessValidationException> { profile.validate() }
        assert(ex.message?.contains("analysisLow") == true)
    }

    @Test
    fun `should fail when analysisLow is greater than analysisHigh`() {
        val profile = createValidProfile().copy(analysisLow = 160.0, analysisHigh = 130.0)
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should pass validation when only analysisLow is set and within range`() {
        val profile = createValidProfile().copy(analysisLow = 70.0, analysisHigh = null)
        profile.validate()
    }

    @Test
    fun `should pass validation when only analysisHigh is set and within range`() {
        val profile = createValidProfile().copy(analysisLow = null, analysisHigh = 180.0)
        profile.validate()
    }

    private fun createValidProfile(): Profile {
        return Profile(
            userId = Uuid.random(),
            name = "Valid Profile",
            insulinType = "Fiasp",
            durationOfAction = 180,
            status = ProfileStatus.DRAFT,
            basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
            icr = listOf(IcrSegment(LocalTime(0, 0), 15.0)),
            isf = listOf(IsfSegment(LocalTime(0, 0), 30.0)),
            targets = listOf(TargetSegment(LocalTime(0, 0), 100.0, 120.0))
        )
    }
}

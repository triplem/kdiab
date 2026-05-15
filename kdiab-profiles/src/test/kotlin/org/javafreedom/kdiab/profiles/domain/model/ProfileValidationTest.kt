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
        // 24 hours * 7 U/h = 168 U/day — exceeds the 150 U/day clinical safety limit
        val profile = createValidProfile().copy(
            basal = listOf(BasalSegment(LocalTime(0, 0), 7.0))
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should pass validation when total daily basal is exactly 150 U per day`() {
        // 24 hours * 6.25 U/h = 150.0 U/day — exactly at the boundary, must pass
        val profile = createValidProfile().copy(
            basal = listOf(BasalSegment(LocalTime(0, 0), 6.25))
        )
        profile.validate() // must not throw
    }

    @Test
    fun `should fail when multi-segment basal sums to more than 150 U per day`() {
        // 12 h * 10 U/h + 12 h * 3 U/h = 120 + 36 = 156 U/day — exceeds limit
        val profile = createValidProfile().copy(
            basal = listOf(
                BasalSegment(LocalTime(0, 0), 10.0),
                BasalSegment(LocalTime(12, 0), 3.0)
            )
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
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

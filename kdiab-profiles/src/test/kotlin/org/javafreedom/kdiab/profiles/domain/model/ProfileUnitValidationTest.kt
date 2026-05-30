@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.domain.model

import kotlinx.datetime.LocalTime
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/**
 * Tests for ICR range validation.
 *
 * Note: the units field (mg/dL / mmol/L) was removed from the domain model in the
 * #1141 refactor. Unit heuristics that relied on the units string are no longer
 * present. This test class now covers the ICR range check that replaces those heuristics.
 */
class ProfileUnitValidationTest {

    @Test
    fun `should pass validation for ICR within acceptable range`() {
        createValidProfile().copy(
            schedule = createValidProfile().schedule.copy(
                icr = listOf(IcrSegment(LocalTime(0, 0), 10.0))
            )
        ).validate()
    }

    @Test
    fun `should fail when ICR is below minimum of 1`() {
        val profile = createValidProfile().copy(
            schedule = createValidProfile().schedule.copy(
                icr = listOf(IcrSegment(LocalTime(0, 0), 0.5))
            )
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should fail when ICR exceeds maximum of 50`() {
        val profile = createValidProfile().copy(
            schedule = createValidProfile().schedule.copy(
                icr = listOf(IcrSegment(LocalTime(0, 0), 51.0))
            )
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should pass when ICR is at minimum boundary of 1`() {
        createValidProfile().copy(
            schedule = createValidProfile().schedule.copy(
                icr = listOf(IcrSegment(LocalTime(0, 0), 1.0))
            )
        ).validate()
    }

    @Test
    fun `should pass when ICR is at maximum boundary of 50`() {
        createValidProfile().copy(
            schedule = createValidProfile().schedule.copy(
                icr = listOf(IcrSegment(LocalTime(0, 0), 50.0))
            )
        ).validate()
    }

    private fun createValidProfile(): Profile {
        return Profile(
            userId = Uuid.random(),
            name = "Test Profile",
            status = ProfileStatus.DRAFT,
            settings = InsulinSettings(insulinType = "Fiasp", durationOfAction = 180),
            schedule = ProfileSchedule(
                basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                icr = listOf(IcrSegment(LocalTime(0, 0), 15.0)),
                isf = listOf(IsfSegment(LocalTime(0, 0), 30.0)),
                targets = listOf(TargetSegment(LocalTime(0, 0), 100.0, 120.0))
            )
        )
    }
}

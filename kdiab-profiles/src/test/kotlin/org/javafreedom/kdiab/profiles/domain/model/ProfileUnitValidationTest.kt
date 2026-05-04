@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.domain.model

import kotlinx.datetime.LocalTime
import org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class ProfileUnitValidationTest {

    @Test
    fun `should pass validation for valid mg-dl profile`() {
        val profile = createValidProfile("mg/dl").copy(
            isf = listOf(IsfSegment(LocalTime(0, 0), 40.0)),
            targets = listOf(TargetSegment(LocalTime(0, 0), 100.0, 120.0))
        )
        profile.validate()
    }

    @Test
    fun `should pass validation for valid mmol-l profile`() {
        val profile = createValidProfile("mmol/l").copy(
            isf = listOf(IsfSegment(LocalTime(0, 0), 2.2)),
            targets = listOf(TargetSegment(LocalTime(0, 0), 5.5, 6.7))
        )
        profile.validate()
    }

    @Test
    fun `should fail when using mg-dl values in mmol-l profile`() {
        val profile = createValidProfile("mmol/l").copy(
            isf = listOf(IsfSegment(LocalTime(0, 0), 2.2)),
            targets = listOf(TargetSegment(LocalTime(0, 0), 100.0, 120.0)) // 100 mmol/L is deadly
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should fail when using mmol-l targets in mg-dl profile`() {
        val profile = createValidProfile("mg/dl").copy(
            isf = listOf(IsfSegment(LocalTime(0, 0), 40.0)),
            targets = listOf(TargetSegment(LocalTime(0, 0), 5.5, 6.7)) // 5.5 mg/dL is deadly
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    @Test
    fun `should fail when using mmol-l ISF in mg-dl profile`() {
        // An ISF of 2.0 mg/dL means 1U drops BG by 2 mg/dL.
        // This is incredibly resistant (hundreds of units for a meal), likely a typo for 2.0 mmol/L (36 mg/dL).
        val profile = createValidProfile("mg/dl").copy(
            isf = listOf(IsfSegment(LocalTime(0, 0), 2.0)),
            targets = listOf(TargetSegment(LocalTime(0, 0), 100.0, 120.0))
        )
        assertFailsWith<BusinessValidationException> { profile.validate() }
    }

    private fun createValidProfile(units: String): Profile {
        return Profile(
            userId = Uuid.random(),
            name = "Test Profile",
            insulinType = "Fiasp",
            units = units,
            durationOfAction = 180,
            status = ProfileStatus.DRAFT,
            basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
            icr = listOf(IcrSegment(LocalTime(0, 0), 15.0)),
            isf = listOf(IsfSegment(LocalTime(0, 0), 30.0)), // Default safe-ish value
            targets = listOf(TargetSegment(LocalTime(0, 0), 100.0, 120.0)) // Default mg/dL
        )
    }
}

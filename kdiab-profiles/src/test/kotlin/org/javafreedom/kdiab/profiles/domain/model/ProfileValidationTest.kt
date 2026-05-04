@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.domain.model

import kotlinx.datetime.LocalTime
import org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException
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

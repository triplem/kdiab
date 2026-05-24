package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import org.javafreedom.kdiab.nightscout.api.upstream.profiles.models.BasalSegment
import org.javafreedom.kdiab.nightscout.api.upstream.profiles.models.Profile
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileMapperTest {

    private fun buildProfile(
        id: String = "profile-abc",
        userId: String = "user-123",
        name: String = "My Profile",
        insulinType: String = "Novorapid",
        durationOfAction: Int = 240,
        status: Profile.Status = Profile.Status.ACTIVE,
        createdAt: String? = "2024-01-01T00:00:00Z",
        basal: List<BasalSegment>? = null,
    ) = Profile(
        id = id,
        userId = userId,
        name = name,
        insulinType = insulinType,
        durationOfAction = durationOfAction,
        status = status,
        createdAt = createdAt,
        basal = basal,
    )

    // ─── Profile.toNs3Profile() ───────────────────────────────────────────────

    @Test
    fun `toNs3Profile maps id to identifier`() {
        val profile = buildProfile(id = "profile-xyz")
        val result = profile.toNs3Profile()
        assertEquals("profile-xyz", result.identifier)
    }

    @Test
    fun `toNs3Profile maps name to defaultProfile`() {
        val profile = buildProfile(name = "Basal Profile A")
        val result = profile.toNs3Profile()
        assertEquals("Basal Profile A", result.defaultProfile)
    }

    @Test
    fun `toNs3Profile converts durationOfAction minutes to dia hours`() {
        // 240 minutes → 4.0 hours
        val profile = buildProfile(durationOfAction = 240)
        val result = profile.toNs3Profile()
        assertEquals(4.0, result.dia)
    }

    @Test
    fun `toNs3Profile converts non-round durationOfAction correctly`() {
        // 270 minutes → 4.5 hours
        val profile = buildProfile(durationOfAction = 270)
        val result = profile.toNs3Profile()
        assertEquals(4.5, result.dia)
    }

    @Test
    fun `toNs3Profile sets units to mg-dl`() {
        val profile = buildProfile()
        val result = profile.toNs3Profile()
        assertEquals("mg/dl", result.units)
    }

    @Test
    fun `toNs3Profile sets timeZone to UTC when not specified`() {
        val profile = buildProfile()
        val result = profile.toNs3Profile()
        assertEquals("UTC", result.timeZone)
    }

    @Test
    fun `toNs3Profile uses createdAt as startDate`() {
        val profile = buildProfile(createdAt = "2024-06-15T10:00:00Z")
        val result = profile.toNs3Profile()
        assertEquals("2024-06-15T10:00:00Z", result.startDate)
    }

    @Test
    fun `toNs3Profile uses current time as startDate when createdAt is null`() {
        val profile = buildProfile(createdAt = null)
        val result = profile.toNs3Profile()
        assertNotNull(result.startDate)
        assertTrue(result.startDate.isNotBlank())
    }

    @Test
    fun `toNs3Profile maps basal segments`() {
        val profile = buildProfile(
            basal = listOf(
                BasalSegment(startTime = "00:00", value = 0.9),
                BasalSegment(startTime = "06:00", value = 1.2),
            ),
        )
        val result = profile.toNs3Profile()
        assertEquals(2, result.basalSegments.size)
        assertEquals("00:00", result.basalSegments[0].time)
        assertEquals(0.9, result.basalSegments[0].value)
        assertEquals("06:00", result.basalSegments[1].time)
        assertEquals(1.2, result.basalSegments[1].value)
    }

    @Test
    fun `toNs3Profile returns empty basalSegments when basal is null`() {
        val profile = buildProfile(basal = null)
        val result = profile.toNs3Profile()
        assertTrue(result.basalSegments.isEmpty())
    }

    @Test
    fun `toNs3Profile returns empty carbratio and sens`() {
        val profile = buildProfile()
        val result = profile.toNs3Profile()
        assertTrue(result.carbratio.isEmpty())
        assertTrue(result.sens.isEmpty())
    }

    // ─── Ns3Profile.toCreateProfileRequest() ─────────────────────────────────

    @Test
    fun `toCreateProfileRequest maps defaultProfile to name`() {
        val ns3 = buildNs3Profile(defaultProfile = "Evening Profile")
        val request = ns3.toCreateProfileRequest()
        assertEquals("Evening Profile", request.name)
    }

    @Test
    fun `toCreateProfileRequest converts dia hours to durationOfAction minutes`() {
        // 4.0 hours → 240 minutes
        val ns3 = buildNs3Profile(dia = 4.0)
        val request = ns3.toCreateProfileRequest()
        assertEquals(240, request.durationOfAction)
    }

    @Test
    fun `toCreateProfileRequest converts fractional dia correctly`() {
        // 4.5 hours → 270 minutes
        val ns3 = buildNs3Profile(dia = 4.5)
        val request = ns3.toCreateProfileRequest()
        assertEquals(270, request.durationOfAction)
    }

    @Test
    fun `toCreateProfileRequest sets insulinType to Unknown`() {
        val ns3 = buildNs3Profile()
        val request = ns3.toCreateProfileRequest()
        assertEquals("Unknown", request.insulinType)
    }

    // ─── Round-trip: dia conversion ───────────────────────────────────────────

    @Test
    fun `dia conversion round-trips correctly for 240 minutes`() {
        val profile = buildProfile(durationOfAction = 240)
        val ns3 = profile.toNs3Profile()
        val request = ns3.toCreateProfileRequest()
        assertEquals(240, request.durationOfAction)
    }

    @Test
    fun `dia conversion round-trips correctly for 300 minutes`() {
        val profile = buildProfile(durationOfAction = 300)
        val ns3 = profile.toNs3Profile()
        val request = ns3.toCreateProfileRequest()
        assertEquals(300, request.durationOfAction)
    }

    private fun buildNs3Profile(
        identifier: String = "profile-abc",
        defaultProfile: String = "My Profile",
        startDate: String = "2024-01-01T00:00:00Z",
        units: String = "mg/dl",
        dia: Double = 4.0,
    ) = Ns3Profile(
        identifier = identifier,
        defaultProfile = defaultProfile,
        startDate = startDate,
        units = units,
        dia = dia,
        basalSegments = emptyList(),
        carbratio = emptyList(),
        sens = emptyList(),
    )
}

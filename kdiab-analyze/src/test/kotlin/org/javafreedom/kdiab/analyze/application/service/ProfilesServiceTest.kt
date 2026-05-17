package org.javafreedom.kdiab.analyze.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.analyze.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.analyze.api.upstream.profiles.models.BasalSegment
import org.javafreedom.kdiab.analyze.api.upstream.profiles.models.IcrSegment
import org.javafreedom.kdiab.analyze.api.upstream.profiles.models.IsfSegment
import org.javafreedom.kdiab.analyze.api.upstream.profiles.models.Profile
import org.javafreedom.kdiab.analyze.api.upstream.profiles.models.TargetSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfilesServiceTest {

    private val profilesClient = mockk<ProfilesClient>()
    private val service = ProfilesService(profilesClient)

    private val userId = "user-1"
    private val auth = "Bearer token"
    private val from = "2024-01-01T00:00:00Z"
    private val to = "2024-01-31T23:59:59Z"

    private fun profile(id: String, status: Profile.Status) = Profile(
        id = id, userId = userId, status = status,
        name = "Profile $id", insulinType = "rapid", durationOfAction = 180,
        createdAt = "2024-01-01T00:00:00Z",
        validFrom = "2024-01-01T00:00:00Z",
    )

    @Test
    fun `getProfiles returns empty list when no profiles`() = runTest {
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns emptyList()
        val result = service.getProfiles(userId, from, to, auth, "")
        assertTrue(result.profiles.isEmpty())
    }

    @Test
    fun `getProfiles includes ACTIVE profiles`() = runTest {
        // Upstream already filters to ACTIVE+ARCHIVED, so mock returns only those
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns listOf(
            profile("p1", Profile.Status.ACTIVE),
        )
        val result = service.getProfiles(userId, from, to, auth, "")
        assertEquals(1, result.profiles.size)
        assertEquals("ACTIVE", result.profiles.first().status)
    }

    @Test
    fun `getProfiles includes ARCHIVED profiles`() = runTest {
        // Upstream already filters to ACTIVE+ARCHIVED, so mock returns only those
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns listOf(
            profile("p1", Profile.Status.ARCHIVED),
        )
        val result = service.getProfiles(userId, from, to, auth, "")
        assertEquals(1, result.profiles.size)
        assertEquals("ARCHIVED", result.profiles.first().status)
    }

    @Test
    fun `getProfiles maps profile fields correctly`() = runTest {
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns listOf(
            Profile(
                id = "p-xyz", userId = userId, status = Profile.Status.ACTIVE,
                name = "Basal A", insulinType = "rapid", durationOfAction = 180,
                createdAt = "2024-01-10T08:00:00Z",
                validFrom = "2024-01-10T09:00:00Z",
                previousProfileId = "p-prev",
            )
        )
        val result = service.getProfiles(userId, from, to, auth, "")
        val p = result.profiles.first()
        assertEquals("p-xyz", p.id)
        assertEquals("ACTIVE", p.status)
        assertEquals("Basal A", p.name)
        assertEquals("2024-01-10T08:00:00Z", p.createdAt)
        assertEquals("2024-01-10T09:00:00Z", p.validFrom)
        assertEquals("p-prev", p.previousProfileId)
    }

    @Test
    fun `getProfiles returns both ACTIVE and ARCHIVED when mixed`() = runTest {
        // Upstream already filters to ACTIVE+ARCHIVED — mock reflects that
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns listOf(
            profile("p1", Profile.Status.ARCHIVED),
            profile("p2", Profile.Status.ACTIVE),
        )
        val result = service.getProfiles(userId, from, to, auth, "")
        assertEquals(2, result.profiles.size)
        assertTrue(result.profiles.any { it.id == "p1" })
        assertTrue(result.profiles.any { it.id == "p2" })
    }

    @Test
    fun `getProfiles maps clinical fields including basal icr isf and targets`() = runTest {
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns listOf(
            Profile(
                id = "p-clinical", userId = userId, status = Profile.Status.ACTIVE,
                name = "Clinical Profile", insulinType = "NovoRapid", durationOfAction = 240,
                createdAt = "2024-01-01T00:00:00Z",
                basal = listOf(BasalSegment(startTime = "00:00", value = 0.8), BasalSegment(startTime = "06:00", value = 1.0)),
                icr = listOf(IcrSegment(startTime = "00:00", value = 12.0)),
                isf = listOf(IsfSegment(startTime = "00:00", value = 50.0)),
                targets = listOf(TargetSegment(startTime = "00:00", low = 72.0, high = 126.0)),
            )
        )
        val result = service.getProfiles(userId, from, to, auth, "")
        val p = result.profiles.first()
        assertEquals("NovoRapid", p.insulinType)
        assertEquals(240, p.durationOfAction)
        val basal = assertNotNull(p.basal)
        assertEquals(2, basal.size)
        assertEquals("00:00", basal[0].startTime)
        assertEquals(0.8, basal[0].value)
        assertEquals("06:00", basal[1].startTime)
        assertEquals(1.0, basal[1].value)
        val icr = assertNotNull(p.icr)
        assertEquals(1, icr.size)
        assertEquals(12.0, icr[0].value)
        val isf = assertNotNull(p.isf)
        assertEquals(50.0, isf[0].value)
        val targets = assertNotNull(p.targets)
        assertEquals(72.0, targets[0].low)
        assertEquals(126.0, targets[0].high)
    }
}

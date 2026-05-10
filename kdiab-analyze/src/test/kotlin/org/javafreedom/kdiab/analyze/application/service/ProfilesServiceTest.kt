package org.javafreedom.kdiab.analyze.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.analyze.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.analyze.api.upstream.profiles.models.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
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
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns listOf(
            profile("p1", Profile.Status.ACTIVE),
        )
        val result = service.getProfiles(userId, from, to, auth, "")
        assertEquals(1, result.profiles.size)
        assertEquals("ACTIVE", result.profiles.first().status)
    }

    @Test
    fun `getProfiles includes ARCHIVED profiles`() = runTest {
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns listOf(
            profile("p1", Profile.Status.ARCHIVED),
        )
        val result = service.getProfiles(userId, from, to, auth, "")
        assertEquals(1, result.profiles.size)
        assertEquals("ARCHIVED", result.profiles.first().status)
    }

    @Test
    fun `getProfiles excludes DRAFT profiles`() = runTest {
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns listOf(
            profile("p1", Profile.Status.DRAFT),
            profile("p2", Profile.Status.ACTIVE),
        )
        val result = service.getProfiles(userId, from, to, auth, "")
        assertEquals(1, result.profiles.size)
        assertEquals("p2", result.profiles.first().id)
    }

    @Test
    fun `getProfiles excludes PROPOSED profiles`() = runTest {
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns listOf(
            profile("p1", Profile.Status.PROPOSED),
            profile("p2", Profile.Status.ACTIVE),
        )
        val result = service.getProfiles(userId, from, to, auth, "")
        assertEquals(1, result.profiles.size)
        assertEquals("p2", result.profiles.first().id)
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
        coEvery { profilesClient.getProfiles(userId, auth, any()) } returns listOf(
            profile("p1", Profile.Status.ARCHIVED),
            profile("p2", Profile.Status.ACTIVE),
            profile("p3", Profile.Status.DRAFT),
            profile("p4", Profile.Status.PROPOSED),
        )
        val result = service.getProfiles(userId, from, to, auth, "")
        assertEquals(2, result.profiles.size)
        assertTrue(result.profiles.any { it.id == "p1" })
        assertTrue(result.profiles.any { it.id == "p2" })
    }
}

package org.javafreedom.kdiab.bff.application.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.bff.adapters.outbound.http.ProfileDto
import org.javafreedom.kdiab.bff.adapters.outbound.http.ProfilesClient
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

    private fun profile(id: String, status: String) = ProfileDto(
        id = id, userId = userId, status = status,
        name = "Profile $id", createdAt = "2024-01-01T00:00:00Z",
    )

    @Test
    fun `getProfiles returns empty list when no profiles`() = runTest {
        coEvery { profilesClient.getProfiles(userId, auth) } returns emptyList()
        val result = service.getProfiles(userId, from, to, auth)
        assertTrue(result.profiles.isEmpty())
    }

    @Test
    fun `getProfiles includes ACTIVE profiles`() = runTest {
        coEvery { profilesClient.getProfiles(userId, auth) } returns listOf(
            profile("p1", "ACTIVE"),
        )
        val result = service.getProfiles(userId, from, to, auth)
        assertEquals(1, result.profiles.size)
        assertEquals("ACTIVE", result.profiles.first().status)
    }

    @Test
    fun `getProfiles includes ARCHIVED profiles`() = runTest {
        coEvery { profilesClient.getProfiles(userId, auth) } returns listOf(
            profile("p1", "ARCHIVED"),
        )
        val result = service.getProfiles(userId, from, to, auth)
        assertEquals(1, result.profiles.size)
        assertEquals("ARCHIVED", result.profiles.first().status)
    }

    @Test
    fun `getProfiles excludes DRAFT profiles`() = runTest {
        coEvery { profilesClient.getProfiles(userId, auth) } returns listOf(
            profile("p1", "DRAFT"),
            profile("p2", "ACTIVE"),
        )
        val result = service.getProfiles(userId, from, to, auth)
        assertEquals(1, result.profiles.size)
        assertEquals("p2", result.profiles.first().id)
    }

    @Test
    fun `getProfiles excludes PROPOSED profiles`() = runTest {
        coEvery { profilesClient.getProfiles(userId, auth) } returns listOf(
            profile("p1", "PROPOSED"),
            profile("p2", "ACTIVE"),
        )
        val result = service.getProfiles(userId, from, to, auth)
        assertEquals(1, result.profiles.size)
        assertEquals("p2", result.profiles.first().id)
    }

    @Test
    fun `getProfiles maps profile fields correctly`() = runTest {
        coEvery { profilesClient.getProfiles(userId, auth) } returns listOf(
            ProfileDto(
                id = "p-xyz", userId = userId, status = "ACTIVE",
                name = "Basal A", createdAt = "2024-01-10T08:00:00Z",
                previousProfileId = "p-prev",
            )
        )
        val result = service.getProfiles(userId, from, to, auth)
        val p = result.profiles.first()
        assertEquals("p-xyz", p.id)
        assertEquals("ACTIVE", p.status)
        assertEquals("Basal A", p.name)
        assertEquals("2024-01-10T08:00:00Z", p.createdAt)
        assertEquals("p-prev", p.previousProfileId)
    }

    @Test
    fun `getProfiles returns both ACTIVE and ARCHIVED when mixed`() = runTest {
        coEvery { profilesClient.getProfiles(userId, auth) } returns listOf(
            profile("p1", "ARCHIVED"),
            profile("p2", "ACTIVE"),
            profile("p3", "DRAFT"),
            profile("p4", "PROPOSED"),
        )
        val result = service.getProfiles(userId, from, to, auth)
        assertEquals(2, result.profiles.size)
        assertTrue(result.profiles.any { it.id == "p1" })
        assertTrue(result.profiles.any { it.id == "p2" })
    }
}

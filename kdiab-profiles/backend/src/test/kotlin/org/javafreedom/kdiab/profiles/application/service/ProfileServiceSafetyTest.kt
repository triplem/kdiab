@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.javafreedom.kdiab.profiles.domain.model.ProfileStatus
import org.javafreedom.kdiab.profiles.domain.repository.ProfileRepository
import kotlin.test.Test
import kotlin.uuid.Uuid

class ProfileServiceSafetyTest {

    private val repository = mockk<ProfileRepository>(relaxed = true)
    private val service = ProfileService(repository)

    @Test
    fun `deleteAllProfiles should only delete DRAFT profiles`() = runBlocking {
        val userId = Uuid.random()

        coEvery { repository.deleteByUserIdAndStatus(any(), any()) } returns true

        service.deleteAllProfiles(userId)

        // Verify it only calls deleteByUserIdAndStatus with DRAFT status
        coVerify(exactly = 1) {
            repository.deleteByUserIdAndStatus(userId, ProfileStatus.DRAFT)
        }
    }
}

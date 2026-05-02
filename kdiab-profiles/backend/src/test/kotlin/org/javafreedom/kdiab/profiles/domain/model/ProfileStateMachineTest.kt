@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.domain.model

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.javafreedom.kdiab.profiles.domain.repository.ProfileRepository
import org.javafreedom.kdiab.profiles.application.service.ProfileService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class ProfileStateMachineTest {

    private val repository = mockk<ProfileRepository>(relaxed = true)
    private val service = ProfileService(repository)
    private val userId = Uuid.random()

    @Test
    fun `activateProfile should archive old active profile and activate new one`() = runBlocking {
        // Given
        val oldActiveProfile = createProfile(status = ProfileStatus.ACTIVE)
        val newDraftProfile = createProfile(status = ProfileStatus.DRAFT)
        
        coEvery { repository.findActiveByUserId(userId) } returns oldActiveProfile
        coEvery { repository.findById(newDraftProfile.id) } returns newDraftProfile
        coEvery { repository.activateProfile(any(), any()) } returns newDraftProfile.copy(status = ProfileStatus.ACTIVE)

        // When
        service.activateProfile(userId, newDraftProfile.id)

        // Then
        val oldProfileSlot = io.mockk.slot<Profile>()
        val newProfileSlot = io.mockk.slot<Profile>()
        coVerify { 
            repository.activateProfile(
                capture(oldProfileSlot),
                capture(newProfileSlot)
            )
        }
        
        assertEquals(oldActiveProfile.id, oldProfileSlot.captured.id)
        assertEquals(newDraftProfile.id, newProfileSlot.captured.id)
        assertEquals(ProfileStatus.ACTIVE, newProfileSlot.captured.status)
    }

    @Test
    fun `updateProfile should archive existing active profile and create new active version`() = runBlocking {
        // Given
        val existingActive = createProfile(status = ProfileStatus.ACTIVE, name = "Old Name")
        val updateData = existingActive.copy(name = "New Name")
        
        coEvery { repository.findById(existingActive.id) } returns existingActive
        coEvery { repository.updateActiveProfile(any(), any()) } returns updateData // return mock doesn't matter much for verification

        // When
        service.updateProfile(updateData)

        // Then
        val oldProfileSlot = io.mockk.slot<Profile>()
        val newProfileSlot = io.mockk.slot<Profile>()

        coVerify {
            repository.updateActiveProfile(
                capture(oldProfileSlot),
                capture(newProfileSlot)
            )
        }
        
        assertEquals(existingActive.id, oldProfileSlot.captured.id)
        
        val capturedNew = newProfileSlot.captured
        assertEquals("New Name", capturedNew.name)
        assertEquals(ProfileStatus.ACTIVE, capturedNew.status)
        assertEquals(existingActive.id, capturedNew.previousProfileId)
    }

    private fun createProfile(
        status: ProfileStatus = ProfileStatus.DRAFT, 
        name: String = "Test"
    ): Profile {
        return Profile(
            userId = userId,
            name = name,
            insulinType = "Fiasp",
            durationOfAction = 180,
            status = status,
            basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
            icr = listOf(IcrSegment(LocalTime(0, 0), 10.0)),
            isf = listOf(IsfSegment(LocalTime(0, 0), 30.0)),
            targets = listOf(TargetSegment(LocalTime(0, 0), 100.0, 120.0))
        )
    }
}

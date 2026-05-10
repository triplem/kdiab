@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.javafreedom.kdiab.profiles.domain.model.BasalSegment
import org.javafreedom.kdiab.profiles.domain.model.IcrSegment
import org.javafreedom.kdiab.profiles.domain.model.IsfSegment
import org.javafreedom.kdiab.profiles.domain.model.PagedProfiles
import org.javafreedom.kdiab.profiles.domain.model.Profile
import org.javafreedom.kdiab.profiles.domain.model.ProfileStatus
import org.javafreedom.kdiab.profiles.domain.model.TargetSegment
import org.javafreedom.kdiab.profiles.domain.repository.ProfileRepository

class ProfileServiceTest {

        private val repository = mockk<ProfileRepository>()
        private val service = ProfileService(repository)

        @Test
        fun `createProfile should save and return profile`() = runBlocking {
                val userId = Uuid.random()
                val profile =
                        Profile(
                                userId = userId,
                                name = "Test Profile",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.DRAFT,
                                basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.existsActiveOrDraftWithName(userId, profile.name) } returns false
                coEvery { repository.save(any()) } returns profile

                val result = service.createProfile(profile)

                assertEquals(profile, result)
                coVerify(exactly = 1) { repository.save(profile) }
        }

        @Test
        fun `getProfile should return profile when found`() = runBlocking {
                val profileId = Uuid.random()
                val profile =
                        Profile(
                                id = profileId,
                                userId = Uuid.random(),
                                name = "Test Profile",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.DRAFT,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile

                val result = service.getProfile(profileId)

                assertEquals(profile, result)
        }

        @Test
        fun `getProfiles should return paginated profiles`() = runBlocking {
                val userId = Uuid.random()
                val profiles =
                        listOf(
                                Profile(
                                        userId = userId,
                                        name = "Profile 1",
                                        insulinType = "Fiasp",
                                        durationOfAction = 180,
                                        status = ProfileStatus.DRAFT,
                                        basal = emptyList(),
                                        icr = emptyList(),
                                        isf = emptyList(),
                                        targets = emptyList()
                                )
                        )

                coEvery { repository.findAllByUserId(userId, 0, 50) } returns profiles
                coEvery { repository.countByUserId(userId) } returns 1L

                val result = service.getProfiles(userId)

                assertEquals(PagedProfiles(items = profiles, page = 0, size = 50, totalCount = 1L), result)
        }

        @Test
        fun `getProfiles page beyond total returns empty items`() = runBlocking {
                val userId = Uuid.random()

                coEvery { repository.findAllByUserId(userId, 5, 50) } returns emptyList()
                coEvery { repository.countByUserId(userId) } returns 3L

                val result = service.getProfiles(userId, page = 5, size = 50)

                assertEquals(0, result.items.size)
                assertEquals(3L, result.totalCount)
                assertEquals(5, result.page)
        }

        @Test
        fun `getProfiles with statuses delegates to findByStatuses and countByStatuses`() = runBlocking {
                val userId = Uuid.random()
                val statuses = listOf(ProfileStatus.ACTIVE, ProfileStatus.ARCHIVED)
                val profiles = listOf(
                        Profile(
                                userId = userId,
                                name = "Active Profile",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.ACTIVE,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )
                )

                coEvery { repository.findByStatuses(userId, statuses, 0, 50) } returns profiles
                coEvery { repository.countByStatuses(userId, statuses) } returns 1L

                val result = service.getProfiles(userId, page = 0, size = 50, statuses = statuses)

                assertEquals(PagedProfiles(items = profiles, page = 0, size = 50, totalCount = 1L), result)
                coVerify(exactly = 1) { repository.findByStatuses(userId, statuses, 0, 50) }
                coVerify(exactly = 1) { repository.countByStatuses(userId, statuses) }
                coVerify(exactly = 0) { repository.findAllByUserId(any(), any(), any()) }
                coVerify(exactly = 0) { repository.countByUserId(any()) }
        }

        @Test
        fun `getActiveProfile should return active profile`() = runBlocking {
                val userId = Uuid.random()
                val profile =
                        Profile(
                                userId = userId,
                                name = "Active Profile",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.ACTIVE,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findActiveByUserId(userId) } returns profile

                val result = service.getActiveProfile(userId)

                assertEquals(profile, result)
        }

        @Test
        fun `activateProfile should archive current active and activate new profile`() =
                runBlocking {
                        val userId = Uuid.random()
                        val oldActiveId = Uuid.random()
                        val newActiveId = Uuid.random()

                        val oldActive =
                                Profile(
                                        id = oldActiveId,
                                        userId = userId,
                                        name = "Old Active",
                                        insulinType = "Fiasp",
                                        durationOfAction = 180,
                                        status = ProfileStatus.ACTIVE,
                                        basal = emptyList(),
                                        icr = emptyList(),
                                        isf = emptyList(),
                                        targets = emptyList()
                                )

                        val newProfile =
                                Profile(
                                        id = newActiveId,
                                        userId = userId,
                                        name = "New Profile",
                                        insulinType = "Fiasp",
                                        durationOfAction = 180,
                                        status = ProfileStatus.DRAFT,
                                        basal = emptyList(),
                                        icr = emptyList(),
                                        isf = emptyList(),
                                        targets = emptyList()
                                )

                        coEvery { repository.findById(newActiveId) } returns newProfile
                        coEvery { repository.findActiveByUserId(userId) } returns oldActive

                        val expectedNewActive = newProfile.copy(status = ProfileStatus.ACTIVE)
                        coEvery { repository.activateProfile(any(), any()) } returns
                                expectedNewActive

                        val result = service.activateProfile(userId, newActiveId)

                        assertNotNull(result)
                        assertEquals(ProfileStatus.ACTIVE, result.status)

                        coVerify(exactly = 1) {
                                repository.activateProfile(
                                        match {
                                                it.id == oldActiveId &&
                                                        it.status == ProfileStatus.ARCHIVED
                                        },
                                        match {
                                                it.id == newActiveId &&
                                                        it.status == ProfileStatus.ACTIVE
                                        }
                                )
                        }
                }

        @Test
        fun `activateProfile converts ExposedSQLException to ConflictException`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Draft",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.DRAFT,
                                basal = listOf(BasalSegment(kotlinx.datetime.LocalTime(0, 0), 1.0)),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.findActiveByUserId(userId) } returns null
                // Simulate the DB unique-index violation that occurs on a concurrent activation
                coEvery { repository.activateProfile(any(), any()) } throws
                        io.mockk.mockk<org.jetbrains.exposed.v1.exceptions.ExposedSQLException>(
                                relaxed = true
                        )

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.ConflictException> {
                        service.activateProfile(userId, profileId)
                }
        }

        @Test
        fun `acceptProposedProfile converts ExposedSQLException to ConflictException`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Proposed",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.PROPOSED,
                                basal = listOf(BasalSegment(kotlinx.datetime.LocalTime(0, 0), 1.0)),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.findActiveByUserId(userId) } returns null
                coEvery { repository.activateProfile(any(), any()) } throws
                        io.mockk.mockk<org.jetbrains.exposed.v1.exceptions.ExposedSQLException>(
                                relaxed = true
                        )

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.ConflictException> {
                        service.acceptProposedProfile(userId, profileId)
                }
        }

        @Test
        fun `activateProfile throws exception if profile not found`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()

                coEvery { repository.findById(profileId) } returns null

                assertFailsWith<
                        org.javafreedom.kdiab.profiles.domain.exception.ResourceNotFoundException> {
                        service.activateProfile(userId, profileId)
                }
        }

        @Test
        fun `activateProfile throws exception if profile belongs to other user`() = runBlocking {
                val userId = Uuid.random()
                val otherUser = Uuid.random()
                val profileId = Uuid.random()
                val profile =
                        Profile(
                                id = profileId,
                                userId = otherUser,
                                name = "Test",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.DRAFT,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile

                assertFailsWith<
                        org.javafreedom.kdiab.profiles.domain.exception.AuthorizationException> {
                        service.activateProfile(userId, profileId)
                }
        }

	@Test
	fun `activateProfile should clone archived profile when activated`() = runBlocking {
                val userId = Uuid.random()
                val archivedId = Uuid.random()
                val archivedProfile = Profile(
                        id = archivedId,
                        userId = userId,
                        name = "Archived",
                        status = ProfileStatus.ARCHIVED,
                        basal = emptyList(), icr = emptyList(), isf = emptyList(), targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )

                coEvery { repository.findById(archivedId) } returns archivedProfile
                coEvery { repository.findActiveByUserId(userId) } returns null
                coEvery { repository.activateProfile(any(), any()) } answers { secondArg() }

                val result = service.activateProfile(userId, archivedId)

                assertEquals(ProfileStatus.ACTIVE, result.status)
                assert(result.id != archivedId)
                assertEquals(archivedId, result.previousProfileId)
                coVerify { repository.activateProfile(null, match { it.previousProfileId == archivedId }) }
        }

        @Test
        fun `activateProfile returns immediately if already active`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Test",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.ACTIVE,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile

                val result = service.activateProfile(userId, profileId)
                assertEquals(profile, result)
                coVerify(exactly = 0) { repository.activateProfile(any(), any()) }
        }

        @Test
        fun `acceptProposedProfile happy path`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Proposed", status = ProfileStatus.PROPOSED,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(),
                        isf = emptyList(),
                        targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.findActiveByUserId(userId) } returns null
                coEvery { repository.activateProfile(any(), any()) } answers { secondArg() }

                val result = service.acceptProposedProfile(userId, profileId)

                assertEquals(ProfileStatus.ACTIVE, result.status)
                assertEquals(profileId, result.id)
                coVerify {
                        repository.activateProfile(null, match {
                                it.id == profileId && it.status == ProfileStatus.ACTIVE
                        })
                }
        }

        @Test
        fun `acceptProposedProfile throws if not proposed`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Draft", status = ProfileStatus.DRAFT,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(),
                        isf = emptyList(),
                        targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profileId) } returns profile

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException> {
                        service.acceptProposedProfile(userId, profileId)
                }
        }

        @Test
        fun `rejectProposedProfile happy path`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Proposed", status = ProfileStatus.PROPOSED,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(),
                        isf = emptyList(),
                        targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.update(any()) } answers { firstArg() }

                val result = service.rejectProposedProfile(userId, profileId)

                assertEquals(ProfileStatus.ARCHIVED, result.status)
                coVerify { repository.update(match { it.id == profileId && it.status == ProfileStatus.ARCHIVED }) }
        }

        @Test
        fun `rejectProposedProfile throws if not proposed`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Draft", status = ProfileStatus.DRAFT,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(),
                        isf = emptyList(),
                        targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profileId) } returns profile

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException> {
                        service.rejectProposedProfile(userId, profileId)
                }
        }

        @Test
        fun `deleteProfile happy path`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Test",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.DRAFT,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.delete(profileId) } returns true

                val result = service.deleteProfile(userId, profileId)
                assert(result)
                coVerify(exactly = 1) { repository.delete(profileId) }
        }

        @Test
        fun `deleteProfile throws if active or archived`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Active", status = ProfileStatus.ACTIVE,
                        basal = emptyList(), icr = emptyList(), isf = emptyList(), targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profileId) } returns profile

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException> {
                        service.deleteProfile(userId, profileId)
                }
        }

        @Test
        fun `deleteProfile throws if not found`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                coEvery { repository.findById(profileId) } returns null
                assertFailsWith<
                        org.javafreedom.kdiab.profiles.domain.exception.ResourceNotFoundException> {
                        service.deleteProfile(userId, profileId)
                }
        }

        @Test
        fun `deleteSegment logic works`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val noonTime = LocalTime(12, 0)
                val basal = listOf(BasalSegment(LocalTime(0, 0), 1.0), BasalSegment(noonTime, 0.8))
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Test",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.DRAFT,
                                basal = basal,
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.update(any()) } answers { firstArg() }

                val result = service.deleteSegment(userId, profileId, "basal", noonTime)
                assertEquals(1, result.basal.size)
        }

        @Test
        fun `deleteSegment logic works with copy-on-write for ACTIVE profile`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val noonTime = LocalTime(12, 0)
                val basal = listOf(BasalSegment(LocalTime(0, 0), 1.0), BasalSegment(noonTime, 0.8))
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Active",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.ACTIVE,
                                basal = basal,
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.updateActiveProfile(any(), any()) } answers { secondArg() }

                val result = service.deleteSegment(userId, profileId, "basal", noonTime)
                assertEquals(1, result.basal.size)
                assertEquals(ProfileStatus.ACTIVE, result.status)
                assert(result.id != profileId)
                coVerify { repository.updateActiveProfile(match { it.status == ProfileStatus.ARCHIVED }, any()) }
        }

        @Test
        fun `deleteSegment throws if archived`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Archived", status = ProfileStatus.ARCHIVED,
                        basal = emptyList(), icr = emptyList(), isf = emptyList(), targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profileId) } returns profile

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException> {
                        service.deleteSegment(userId, profileId, "basal", LocalTime(0, 0))
                }
        }

        @Test
        fun `deleteSegment throws when segment not found at given time`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val existingTime = LocalTime(8, 0)
                val missingTime = LocalTime(14, 0)
                val basalSegment = org.javafreedom.kdiab.profiles.domain.model.BasalSegment(
                        startTime = existingTime, value = 0.8
                )
                val profile = Profile(
                        id = profileId, userId = userId, name = "Test", status = ProfileStatus.DRAFT,
                        basal = listOf(basalSegment), icr = emptyList(), isf = emptyList(),
                        targets = emptyList(), insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profileId) } returns profile

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException> {
                        service.deleteSegment(userId, profileId, "basal", missingTime)
                }
        }

        @Test
        fun `updateProfile delegates to repository`() = runBlocking {
                val userId = Uuid.random()
                val profile =
                        Profile(
                                userId = userId,
                                name = "Test Profile",
                                insulinType = "Fiasp",
                                durationOfAction = 180,
                                status = ProfileStatus.DRAFT,
                                basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList()
                        )

                coEvery { repository.findById(profile.id) } returns profile
                coEvery { repository.existsActiveOrDraftWithName(userId, profile.name, profile.id) } returns false
                coEvery { repository.update(profile) } returns profile
                val result = service.updateProfile(profile)
                assertEquals(profile, result)
        }

        @Test
        fun `updateProfile throws if archived`() = runBlocking {
                val profile = Profile(
                        userId = Uuid.random(), name = "Archived", status = ProfileStatus.ARCHIVED,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(),
                        isf = emptyList(),
                        targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profile.id) } returns profile

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException> {
                        service.updateProfile(profile)
                }
        }

        @Test
        fun `updateProfile copy-on-write if active`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile = Profile(
                        id = profileId, userId = userId, name = "Active", status = ProfileStatus.ACTIVE,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(),
                        isf = emptyList(),
                        targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.existsActiveOrDraftWithName(userId, "Updated Active", profileId) } returns false
                coEvery { repository.updateActiveProfile(any(), any()) } answers { secondArg() }

                val updated = profile.copy(name = "Updated Active")
                val result = service.updateProfile(updated)

                assertEquals("Updated Active", result.name)
                assertEquals(ProfileStatus.ACTIVE, result.status)
                assert(result.id != profileId)
                coVerify { repository.updateActiveProfile(match { it.status == ProfileStatus.ARCHIVED }, any()) }
        }

        @Test
        fun `deleteAllProfiles delegates to repository`() = runBlocking {
                val userId = Uuid.random()
                coEvery { repository.deleteByUserIdAndStatus(userId, ProfileStatus.DRAFT) } returns true
                val result = service.deleteAllProfiles(userId)
                assert(result)
        }

        @Test
        fun `deleteSegment unknown type throws exception`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Test",
                                status = ProfileStatus.DRAFT,
                                basal = emptyList(),
                                icr = emptyList(),
                                isf = emptyList(),
                                targets = emptyList(),
                                insulinType = "Fiasp",
                                durationOfAction = 180
                        )
                coEvery { repository.findById(profileId) } returns profile

                assertFailsWith<IllegalArgumentException> {
                        service.deleteSegment(userId, profileId, "unknown", LocalTime(0, 0))
                }
        }

        @Test
        fun `deleteSegment helper covers other types`() = runBlocking {
                val userId = Uuid.random()
                val profileId = Uuid.random()
                val time = LocalTime(0, 0)
                val profile =
                        Profile(
                                id = profileId,
                                userId = userId,
                                name = "Test",
                                status = ProfileStatus.DRAFT,
                                basal = listOf(BasalSegment(time, 1.0)),
                                icr = listOf(IcrSegment(time, 10.0)),
                                isf = listOf(IsfSegment(time, 20.0)),
                                targets = listOf(TargetSegment(time, 100.0, 100.0)),
                                insulinType = "Fiasp",
                                durationOfAction = 180
                        )

                coEvery { repository.findById(profileId) } returns profile
                coEvery { repository.update(any()) } answers { firstArg() }

                // ICR
                val res1 = service.deleteSegment(userId, profileId, "icr", time)
                assert(res1.icr.isEmpty())

                // ISF
                val res2 = service.deleteSegment(userId, profileId, "isf", time)
                assert(res2.isf.isEmpty())

                // Targets
                val res3 = service.deleteSegment(userId, profileId, "targets", time)
                assert(res3.targets.isEmpty())
        }

        // ── Name uniqueness ───────────────────────────────────────────────────────

        @Test
        fun `createProfile throws ConflictException when name already used by non-archived profile`() = runBlocking {
                val userId = Uuid.random()
                val existingDraft = Profile(
                        userId = userId, name = "Basal Plan",
                        status = ProfileStatus.DRAFT,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(), isf = emptyList(), targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                val newProfile = Profile(
                        userId = userId, name = "Basal Plan",
                        status = ProfileStatus.DRAFT,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.5)),
                        icr = emptyList(), isf = emptyList(), targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.existsActiveOrDraftWithName(userId, "Basal Plan") } returns true

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.ConflictException> {
                        service.createProfile(newProfile)
                }
                coVerify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `createProfile allows name used only by archived profile`() = runBlocking {
                val userId = Uuid.random()
                val archived = Profile(
                        userId = userId, name = "Old Plan",
                        status = ProfileStatus.ARCHIVED,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(), isf = emptyList(), targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                val newProfile = Profile(
                        userId = userId, name = "Old Plan",
                        status = ProfileStatus.DRAFT,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.5)),
                        icr = emptyList(), isf = emptyList(), targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.existsActiveOrDraftWithName(userId, "Old Plan") } returns false
                coEvery { repository.save(any()) } returns newProfile

                val result = service.createProfile(newProfile)

                assertEquals("Old Plan", result.name)
                coVerify(exactly = 1) { repository.save(any()) }
        }

        @Test
        fun `updateProfile DRAFT throws ConflictException when renaming to existing name`() = runBlocking {
                val userId = Uuid.random()
                val existingDraft = Profile(
                        userId = userId, name = "Morning Plan",
                        status = ProfileStatus.DRAFT,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(), isf = emptyList(), targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                val profileBeingUpdated = Profile(
                        userId = userId, name = "Night Plan",
                        status = ProfileStatus.DRAFT,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 0.8)),
                        icr = emptyList(), isf = emptyList(), targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                // User tries to rename profileBeingUpdated to "Morning Plan" which is taken
                val renamed = profileBeingUpdated.copy(name = "Morning Plan")
                coEvery { repository.findById(profileBeingUpdated.id) } returns profileBeingUpdated
                coEvery { repository.existsActiveOrDraftWithName(userId, "Morning Plan", profileBeingUpdated.id) } returns true

                assertFailsWith<org.javafreedom.kdiab.profiles.domain.exception.ConflictException> {
                        service.updateProfile(renamed)
                }
        }

        @Test
        fun `updateProfile DRAFT allows keeping same name`() = runBlocking {
                val userId = Uuid.random()
                val profile = Profile(
                        userId = userId, name = "My Plan",
                        status = ProfileStatus.DRAFT,
                        basal = listOf(BasalSegment(LocalTime(0, 0), 1.0)),
                        icr = emptyList(), isf = emptyList(), targets = emptyList(),
                        insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findById(profile.id) } returns profile
                // Self is excluded from the name check — must return false
                coEvery { repository.existsActiveOrDraftWithName(userId, "My Plan", profile.id) } returns false
                coEvery { repository.update(any()) } answers { firstArg() }

                val result = service.updateProfile(profile)

                assertEquals("My Plan", result.name)
        }

        // ── getHistory ────────────────────────────────────────────────────────────

        @Test
        fun `getHistory delegates to repository with correct params`() = runBlocking {
                val userId = Uuid.random()
                val from = kotlin.time.Instant.parse("2024-01-01T00:00:00Z")
                val to = kotlin.time.Instant.parse("2024-01-31T23:59:59Z")
                val profile = Profile(
                        userId = userId, name = "Historical",
                        status = ProfileStatus.ARCHIVED,
                        basal = emptyList(), icr = emptyList(), isf = emptyList(),
                        targets = emptyList(), insulinType = "Fiasp", durationOfAction = 180
                )
                coEvery { repository.findHistory(userId, from, to) } returns listOf(profile)

                val result = service.getHistory(userId, from, to)

                assertEquals(listOf(profile), result)
                coVerify(exactly = 1) { repository.findHistory(userId, from, to) }
        }

        @Test
        fun `getHistory returns empty list when no profiles in range`() = runBlocking {
                val userId = Uuid.random()
                val from = kotlin.time.Instant.parse("2020-01-01T00:00:00Z")
                val to = kotlin.time.Instant.parse("2020-01-02T00:00:00Z")
                coEvery { repository.findHistory(userId, from, to) } returns emptyList()

                val result = service.getHistory(userId, from, to)

                assert(result.isEmpty())
                coVerify(exactly = 1) { repository.findHistory(userId, from, to) }
        }
}

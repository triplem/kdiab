@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.application.service

import kotlin.uuid.Uuid
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.profiles.domain.model.PagedProfiles
import org.javafreedom.kdiab.profiles.domain.model.Profile
import org.javafreedom.kdiab.profiles.domain.model.ProfileStatus
import org.javafreedom.kdiab.profiles.domain.repository.ProfileRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class ProfileService(private val profileRepository: ProfileRepository) {

        suspend fun createProfile(profile: Profile): Profile {
                profile.validate()
                assertUniqueNameForUser(profile.userId, profile.name)
                logger.debug { "Saving new profile ${profile.id} for user ${profile.userId}" }
                return profileRepository.save(profile)
        }

        suspend fun getProfile(id: Uuid): Profile? = profileRepository.findById(id)

        suspend fun getProfiles(userId: Uuid, page: Int = 0, size: Int = 50): PagedProfiles {
                val items = profileRepository.findAllByUserId(userId, page, size)
                val totalCount = profileRepository.countByUserId(userId)
                return PagedProfiles(items = items, page = page, size = size, totalCount = totalCount)
        }

        suspend fun getHistory(
                userId: Uuid,
                from: Instant,
                to: Instant
        ): List<Profile> = profileRepository.findHistory(userId, from, to)

        suspend fun getActiveProfile(userId: Uuid): Profile? =
                profileRepository.findActiveByUserId(userId)

        suspend fun activateProfile(userId: Uuid, profileId: Uuid): Profile {
                val profile = getProfileOrThrow(profileId)
                checkOwnership(profile, userId)

                if (profile.status == ProfileStatus.ACTIVE) return profile

                // If activating an ARCHIVED profile, clone it to preserve the historical record
                val now = Clock.System.now()
                val profileToActivate = if (profile.status == ProfileStatus.ARCHIVED) {
                        profile.copy(
                                id = Uuid.random(),
                                status = ProfileStatus.ACTIVE,
                                createdAt = now,
                                previousProfileId = profile.id,
                                activatedAt = now
                        )
                } else {
                        profile.copy(status = ProfileStatus.ACTIVE, activatedAt = now)
                }

                // Atomic activation (archives old if exists, activates new).
                // The DB enforces IDX_PROFILES_USER_ACTIVE (unique partial index on ACTIVE per user).
                // If two concurrent requests race here, the second will hit the constraint —
                // ExposedProfileRepository translates that to a ConflictException (409).
                val currentActive = profileRepository.findActiveByUserId(userId)
                val oldActive = currentActive?.copy(status = ProfileStatus.ARCHIVED, archivedAt = now)

                return profileRepository.activateProfile(oldActive, profileToActivate)
        }

        suspend fun acceptProposedProfile(userId: Uuid, profileId: Uuid): Profile {
                val profile = getProfileOrThrow(profileId)
                checkOwnership(profile, userId)

                if (profile.status != ProfileStatus.PROPOSED) {
                        throw BusinessValidationException("Only PROPOSED profiles can be accepted")
                }

                profile.validate()

                val now = Clock.System.now()
                val currentActive = profileRepository.findActiveByUserId(userId)
                val oldActive = currentActive?.copy(status = ProfileStatus.ARCHIVED, archivedAt = now)
                val newActive = profile.copy(status = ProfileStatus.ACTIVE, activatedAt = now)

                return profileRepository.activateProfile(oldActive, newActive)
        }

        suspend fun rejectProposedProfile(userId: Uuid, profileId: Uuid, reason: String? = null): Profile {
                val profile = getProfileOrThrow(profileId)
                checkOwnership(profile, userId)

                if (profile.status != ProfileStatus.PROPOSED) {
                        throw BusinessValidationException("Only PROPOSED profiles can be rejected")
                }

                val rejected = profile.copy(
                        status = ProfileStatus.ARCHIVED,
                        archivedAt = Clock.System.now(),
                        rejectionReason = reason
                )
                return profileRepository.update(rejected)
        }

        suspend fun updateProfile(profile: Profile): Profile {
                profile.validate()
                val existing = getProfileOrThrow(profile.id)
                checkOwnership(existing, profile.userId)

                if (existing.status == ProfileStatus.ARCHIVED) {
                        throw BusinessValidationException("Cannot update an archived profile")
                }

                if (existing.status == ProfileStatus.PROPOSED) {
                        throw BusinessValidationException(
                                "Cannot directly update a proposed profile — use accept or reject"
                        )
                }

                if (existing.status == ProfileStatus.ACTIVE) {
                        // Copy-on-Write: Archive existing, save new active version.
                        // Exclude the current profile from the name check — it is about to be archived.
                        assertUniqueNameForUser(profile.userId, profile.name, excludeProfileId = existing.id)
                        val now = Clock.System.now()
                        val archived = existing.copy(status = ProfileStatus.ARCHIVED, archivedAt = now)
                        val newVersion =
                                profile.copy(
                                        id = Uuid.random(),
                                        status = ProfileStatus.ACTIVE,
                                        createdAt = now,
                                        previousProfileId = existing.id,
                                        activatedAt = now
                                )
                        logger.debug {
                                "Copy-on-write UPDATE: Archived ${existing.id}, " +
                                "created new active ${newVersion.id}"
                        }
                        return profileRepository.updateActiveProfile(archived, newVersion)
                }

                // DRAFT update: exclude self from the uniqueness check
                assertUniqueNameForUser(profile.userId, profile.name, excludeProfileId = existing.id)
                logger.debug { "Standard update for profile ${profile.id}" }
                return profileRepository.update(profile)
        }

        suspend fun deleteProfile(userId: Uuid, profileId: Uuid): Boolean {
                val profile = getProfileOrThrow(profileId)
                checkOwnership(profile, userId)

                if (profile.status == ProfileStatus.ACTIVE ||
                                profile.status == ProfileStatus.ARCHIVED
                ) {
                        throw BusinessValidationException(
                                        "Cannot delete an active or archived profile"
                                )
                }

                return profileRepository.delete(profileId)
        }

        suspend fun deleteAllProfiles(userId: Uuid): Boolean =
                profileRepository.deleteByUserIdAndStatus(userId, ProfileStatus.DRAFT)

        suspend fun deleteSegment(
                userId: Uuid,
                profileId: Uuid,
                segmentType: String,
                startTime: kotlinx.datetime.LocalTime
        ): Profile {
                val profile = getProfileOrThrow(profileId)
                checkOwnership(profile, userId)

                if (profile.status == ProfileStatus.ARCHIVED) {
                        throw BusinessValidationException(
                                        "Cannot modify segments of an archived profile"
                                )
                }

                val originalSize = when (segmentType.lowercase()) {
                        "basal" -> profile.basal.size
                        "icr" -> profile.icr.size
                        "isf" -> profile.isf.size
                        "targets" -> profile.targets.size
                        else -> throw IllegalArgumentException("Unknown segment type: $segmentType")
                }

                val updatedProfile =
                        when (segmentType.lowercase()) {
                                "basal" ->
                                        profile.copy(
                                                basal =
                                                        profile.basal.filterNot {
                                                                it.startTime == startTime
                                                        }
                                        )
                                "icr" ->
                                        profile.copy(
                                                icr =
                                                        profile.icr.filterNot {
                                                                it.startTime == startTime
                                                        }
                                        )
                                "isf" ->
                                        profile.copy(
                                                isf =
                                                        profile.isf.filterNot {
                                                                it.startTime == startTime
                                                        }
                                        )
                                "targets" ->
                                        profile.copy(
                                                targets =
                                                        profile.targets.filterNot {
                                                                it.startTime == startTime
                                                        }
                                        )
                                else ->
                                        throw IllegalArgumentException(
                                                "Unknown segment type: $segmentType"
                                        )
                        }

                val newSize = when (segmentType.lowercase()) {
                        "basal" -> updatedProfile.basal.size
                        "icr" -> updatedProfile.icr.size
                        "isf" -> updatedProfile.isf.size
                        else -> updatedProfile.targets.size
                }

                if (newSize == originalSize) {
                        throw BusinessValidationException(
                                        "No $segmentType segment found at $startTime"
                                )
                }

                updatedProfile.validate()

                if (profile.status == ProfileStatus.ACTIVE) {
                        // Copy-on-Write: Archive existing, save new active version
                        val now = Clock.System.now()
                        val archived = profile.copy(status = ProfileStatus.ARCHIVED, archivedAt = now)
                        val newVersion = updatedProfile.copy(
                                id = Uuid.random(),
                                status = ProfileStatus.ACTIVE,
                                createdAt = now,
                                previousProfileId = profile.id,
                                activatedAt = now
                        )
                        return profileRepository.updateActiveProfile(archived, newVersion)
                }

                return profileRepository.update(updatedProfile)
        }

        private suspend fun getProfileOrThrow(id: Uuid): Profile =
                profileRepository.findById(id)
                        ?: throw ResourceNotFoundException("Profile not found")

        private fun checkOwnership(profile: Profile, userId: Uuid) {
                if (profile.userId != userId) {
                        throw AuthorizationException("Profile does not belong to user")
                }
        }

        /**
         * Ensures no non-archived profile belonging to [userId] already uses [name].
         * [excludeProfileId] is excluded from the search — pass the current profile's ID when
         * updating so that keeping the same name is allowed.
         */
        private suspend fun assertUniqueNameForUser(userId: Uuid, name: String, excludeProfileId: Uuid? = null) {
                if (profileRepository.existsActiveOrDraftWithName(userId, name, excludeProfileId)) {
                        throw ConflictException("A profile named '$name' already exists for this user")
                }
        }
}

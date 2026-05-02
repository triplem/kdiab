@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.domain.repository

import kotlin.uuid.Uuid
import kotlin.time.Instant
import org.javafreedom.kdiab.profiles.domain.model.Profile
import org.javafreedom.kdiab.profiles.domain.model.ProfileStatus

interface ProfileRepository {
    suspend fun save(profile: Profile): Profile
    suspend fun findById(id: Uuid): Profile?
    suspend fun findAllByUserId(userId: Uuid): List<Profile>
    suspend fun findActiveByUserId(userId: Uuid): Profile?
    suspend fun update(profile: Profile): Profile
    suspend fun findHistory(
            userId: Uuid,
            from: Instant,
            to: Instant
    ): List<Profile>
    suspend fun delete(id: Uuid): Boolean
    suspend fun deleteByUserIdAndStatus(
        userId: Uuid,
        status: ProfileStatus
    ): Boolean

    // Atomic operations
    suspend fun updateActiveProfile(oldProfile: Profile, newProfile: Profile): Profile
    suspend fun activateProfile(oldActive: Profile?, newActive: Profile): Profile
}

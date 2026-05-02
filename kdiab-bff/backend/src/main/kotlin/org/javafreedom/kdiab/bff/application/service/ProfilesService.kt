package org.javafreedom.kdiab.bff.application.service

import org.javafreedom.kdiab.bff.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.bff.domain.model.ProfileSummary
import org.javafreedom.kdiab.bff.domain.model.ProfilesResult

class ProfilesService(
    private val profilesClient: ProfilesClient,
) {
    @Suppress("UnusedParameter")
    suspend fun getProfiles(
        userId: String,
        from: String,
        to: String,
        authorization: String,
    ): ProfilesResult {
        val allProfiles = profilesClient.getProfiles(userId, authorization)

        // Return ACTIVE and ARCHIVED profiles (exclude DRAFTs and PROPOSED).
        // The upstream profiles API does not expose activation timestamps so
        // timeframe-based filtering is not possible; callers see the full history.
        val relevant = allProfiles
            .filter { dto -> dto.status == "ACTIVE" || dto.status == "ARCHIVED" }
            .map { dto ->
                ProfileSummary(
                    id = dto.id,
                    userId = dto.userId,
                    status = dto.status,
                    name = dto.name,
                    createdAt = dto.createdAt,
                    previousProfileId = dto.previousProfileId,
                )
            }

        return ProfilesResult(profiles = relevant)
    }
}

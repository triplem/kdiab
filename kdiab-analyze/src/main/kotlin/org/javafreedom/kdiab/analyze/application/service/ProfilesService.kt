package org.javafreedom.kdiab.analyze.application.service

import org.javafreedom.kdiab.analyze.application.port.outbound.ProfilesPort
import org.javafreedom.kdiab.analyze.domain.model.ProfileSummary
import org.javafreedom.kdiab.analyze.domain.model.ProfilesResult

class ProfilesService(
    private val profilesClient: ProfilesPort,
) {
    @Suppress("UnusedParameter")
    suspend fun getProfiles(
        userId: String,
        from: String,
        to: String,
        authorization: String,
        correlationId: String,
    ): ProfilesResult {
        // Upstream call already filters to ACTIVE and ARCHIVED via the status query param.
        // No in-memory filtering needed.
        val profiles = profilesClient.getProfiles(userId, authorization, correlationId)
            .map { dto ->
                ProfileSummary(
                    id = dto.id,
                    userId = dto.userId,
                    status = dto.status.value,
                    name = dto.name,
                    createdAt = dto.createdAt,
                    validFrom = dto.validFrom,
                    previousProfileId = dto.previousProfileId,
                    activatedAt = dto.activatedAt,
                    archivedAt = dto.archivedAt,
                )
            }

        return ProfilesResult(profiles = profiles)
    }
}

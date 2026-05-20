package org.javafreedom.kdiab.analyze.application.service

import org.javafreedom.kdiab.analyze.application.port.outbound.ProfilesPort
import org.javafreedom.kdiab.analyze.domain.model.ProfileSummary
import org.javafreedom.kdiab.analyze.domain.model.ProfilesResult

class ProfilesService(
    private val profilesPort: ProfilesPort,
) : ProfilesOperation {
    // The upstream kdiab-profiles service does not support date-range filtering on its list
    // endpoint, so `from` and `to` are not accepted here. Callers that receive profiles
    // outside the requested window should filter client-side if needed.
    override suspend fun getProfiles(
        userId: String,
        authorization: String,
        correlationId: String,
    ): ProfilesResult {
        // Upstream call already filters to ACTIVE and ARCHIVED via the status query param.
        // No in-memory filtering needed.
        val profiles = profilesPort.getProfiles(userId, authorization, correlationId)
            .map { dto ->
                ProfileSummary(
                    id = dto.id,
                    userId = dto.userId,
                    status = dto.status,
                    name = dto.name,
                    createdAt = dto.createdAt,
                    validFrom = dto.validFrom,
                    previousProfileId = dto.previousProfileId,
                    activatedAt = dto.activatedAt,
                    archivedAt = dto.archivedAt,
                    insulinType = dto.insulinType,
                    durationOfAction = dto.durationOfAction,
                    basal = dto.basal,
                    icr = dto.icr,
                    isf = dto.isf,
                    targets = dto.targets,
                )
            }

        return ProfilesResult(profiles = profiles)
    }
}

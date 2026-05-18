package org.javafreedom.kdiab.analyze.application.service

import org.javafreedom.kdiab.analyze.domain.model.ProfilesResult

interface ProfilesOperation {
    // The upstream kdiab-profiles service does not expose date-range filtering, so this
    // operation returns all ACTIVE and ARCHIVED profiles for the user without a time window.
    suspend fun getProfiles(
        userId: String,
        authorization: String,
        correlationId: String,
    ): ProfilesResult
}

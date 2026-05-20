package org.javafreedom.kdiab.analyze.application.port.outbound

import org.javafreedom.kdiab.analyze.domain.model.UpstreamProfile

interface ProfilesPort {
    suspend fun getProfiles(
        userId: String,
        authorization: String,
        correlationId: String,
    ): List<UpstreamProfile>
}

package org.javafreedom.kdiab.analyze.application.port.outbound

import org.javafreedom.kdiab.analyze.api.upstream.profiles.models.Profile

interface ProfilesPort {
    suspend fun getProfiles(
        userId: String,
        authorization: String,
        correlationId: String,
    ): List<Profile>
}

package org.javafreedom.kdiab.calc.domain.repository

import org.javafreedom.kdiab.calc.domain.model.ActiveProfile

interface ProfilesPort {
    suspend fun getActiveProfile(userId: String, authorization: String, correlationId: String): ActiveProfile?
}

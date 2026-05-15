@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.domain.repository

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.users.domain.model.UserSettings

interface UserSettingsRepository {
    suspend fun findByUserId(userId: Uuid): UserSettings?
    suspend fun save(settings: UserSettings): UserSettings
    suspend fun delete(userId: Uuid)
}

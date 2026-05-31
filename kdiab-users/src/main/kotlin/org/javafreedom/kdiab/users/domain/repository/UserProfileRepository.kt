@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.domain.repository

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate

interface UserProfileRepository {
    suspend fun findBirthdayByUserId(userId: Uuid): LocalDate?
    suspend fun saveBirthday(userId: Uuid, birthday: LocalDate?)
    suspend fun delete(userId: Uuid)
}

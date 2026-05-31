@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.domain.model

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import org.javafreedom.kdiab.common.domain.model.Role

data class User(
    val userId: Uuid,
    val email: String,
    val displayName: String,
    val roles: Set<Role>,
    val birthday: LocalDate? = null,         // PII — never log this field
    val settings: UserSettings?,
)

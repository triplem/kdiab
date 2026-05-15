@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.domain.model

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.model.Role

data class User(
    val userId: Uuid,
    val email: String,
    val displayName: String,
    val roles: Set<Role>,
    val settings: UserSettings?,
)

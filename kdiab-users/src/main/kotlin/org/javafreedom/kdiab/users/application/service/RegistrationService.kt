@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.users.domain.model.User
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakAdminClient
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakCredential
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakUser
import org.javafreedom.kdiab.users.infrastructure.keycloak.toKeycloakName

private val logger = KotlinLogging.logger {}

class RegistrationService(
    private val keycloak: KeycloakAdminClient,
    private val settingsRepo: UserSettingsRepository,
    private val requiresApproval: Boolean,
) {
    suspend fun register(
        email: String,
        displayName: String,
        password: String,
    ): Uuid {
        val existing = keycloak.listUsers(search = email, max = 1)
        if (existing.any { it.email == email }) {
            throw BusinessValidationException("Email already registered")
        }
        val firstName = displayName.substringBefore(" ").ifBlank { displayName }
        val lastName = displayName.substringAfter(" ", "").ifBlank { null }
        val kcUser = KeycloakUser(
            username = email,
            email = email,
            firstName = firstName,
            lastName = lastName,
            enabled = true,
            emailVerified = false,
            credentials = listOf(KeycloakCredential(value = password, temporary = false)),
        )
        val userId = keycloak.createUser(kcUser)

        if (!requiresApproval) {
            val roleRep = keycloak.getRealmRole(Role.PATIENT.toKeycloakName())
            keycloak.assignRoles(userId, listOf(roleRep))
        }

        val now = Clock.System.now()
        settingsRepo.save(
            org.javafreedom.kdiab.users.domain.model.UserSettings(
                userId = userId,
                createdAt = now,
                updatedAt = now,
            )
        )
        logger.info { "self_registration userId=$userId requiresApproval=$requiresApproval" }
        return userId
    }
}

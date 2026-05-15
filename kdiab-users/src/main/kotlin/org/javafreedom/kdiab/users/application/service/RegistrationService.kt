@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.users.domain.model.UserSettings
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

        // No pre-check for existing email: checking first and returning a distinct error
        // response leaks whether an address is registered (user enumeration). Instead we
        // attempt the create and silently succeed on 409 Conflict so callers cannot
        // distinguish a new registration from a duplicate one.
        val userId = try {
            keycloak.createUser(kcUser)
        } catch (@Suppress("SwallowedException") e: ConflictException) {
            // Intentional: returning a random UUID gives the caller a 201 identical to
            // a real registration, preventing user enumeration via response differences.
            logger.debug { "self_registration duplicate_suppressed email=<redacted>" }
            return Uuid.random()
        }

        if (!requiresApproval) {
            val roleRep = keycloak.getRealmRole(Role.PATIENT.toKeycloakName())
            keycloak.assignRoles(userId, listOf(roleRep))
        }

        val now = Clock.System.now()
        try {
            settingsRepo.save(UserSettings(userId = userId, createdAt = now, updatedAt = now))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error(e) { "self_registration db_fail rolling_back userId=$userId" }
            runCatching { keycloak.deleteUser(userId) }.onFailure { re ->
                logger.error(re) { "self_registration rollback_failed userId=$userId" }
            }
            throw e
        }
        logger.info { "self_registration userId=$userId requiresApproval=$requiresApproval" }
        return userId
    }
}

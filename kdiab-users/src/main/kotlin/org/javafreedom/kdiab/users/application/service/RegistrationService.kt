@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.users.domain.model.UserSettings
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort
import org.javafreedom.kdiab.users.domain.repository.IdentityUserProfile
import org.javafreedom.kdiab.users.domain.repository.PasswordCredential
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository

private val logger = KotlinLogging.logger {}

class RegistrationService(
    private val identityProvider: IdentityProviderPort,
    private val settingsRepo: UserSettingsRepository,
    private val requiresApproval: Boolean,
) {
    suspend fun register(
        email: String,
        displayName: String,
        password: String,
    ): Uuid {
        val (firstName, lastName) = splitDisplayName(displayName)
        val profile = IdentityUserProfile(
            username = email,
            email = email,
            firstName = firstName,
            lastName = lastName,
            enabled = true,
            emailVerified = false,
            passwordCredential = PasswordCredential(value = password, temporary = false),
        )

        // No pre-check for existing email: checking first and returning a distinct error
        // response leaks whether an address is registered (user enumeration). Instead we
        // attempt the create and silently succeed on 409 Conflict so callers cannot
        // distinguish a new registration from a duplicate one.
        val userId = try {
            identityProvider.createUser(profile)
        } catch (@Suppress("SwallowedException") e: ConflictException) {
            // Intentional: returning a random UUID gives the caller a 201 identical to
            // a real registration, preventing user enumeration via response differences.
            logger.debug { "self_registration duplicate_suppressed email=<redacted>" }
            return Uuid.random()
        }

        if (!requiresApproval) {
            identityProvider.assignRoles(userId, setOf(Role.PATIENT))
        }

        val now = Clock.System.now()
        try {
            settingsRepo.save(UserSettings(userId = userId, createdAt = now, updatedAt = now))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error(e) { "self_registration db_fail rolling_back userId=$userId" }
            runCatching { identityProvider.deleteUser(userId) }.onFailure { re ->
                logger.error(re) { "self_registration rollback_failed userId=$userId" }
            }
            throw e
        }
        logger.info { "self_registration userId=$userId requiresApproval=$requiresApproval" }
        return userId
    }
}

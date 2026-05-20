@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.domain.model.User
import org.javafreedom.kdiab.users.domain.model.UserSettings
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakAdminClient
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakCredential
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakRole
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakUser
import org.javafreedom.kdiab.users.infrastructure.keycloak.toKeycloakName

private val logger = KotlinLogging.logger {}

private val VALID_GLUCOSE_UNITS = setOf("mg/dL", "mmol/L")
private val VALID_WEIGHT_UNITS  = setOf("kg", "lbs")

class UserService(
    private val keycloak: KeycloakAdminClient,
    private val settingsRepo: UserSettingsRepository,
    private val doctorPatientRepo: DoctorPatientRepository,
) {
    suspend fun getMe(principal: UserPrincipal): User {
        val kcUser = keycloak.getUser(principal.userId)
        val settings = settingsRepo.findByUserId(principal.userId)
            ?: defaultSettings(principal.userId).also { settingsRepo.save(it) }
        return kcUser.toDomain(settings, principal.roles)
    }

    suspend fun updateMySettings(
        principal: UserPrincipal,
        patch: SettingsPatch,
    ): UserSettings {
        patch.glucoseUnit?.let {
            if (it !in VALID_GLUCOSE_UNITS)
                throw BusinessValidationException(
                    "Invalid glucose unit '$it'. Allowed: ${VALID_GLUCOSE_UNITS.joinToString()}"
                )
        }
        patch.weightUnit?.let {
            if (it !in VALID_WEIGHT_UNITS)
                throw BusinessValidationException(
                    "Invalid weight unit '$it'. Allowed: ${VALID_WEIGHT_UNITS.joinToString()}"
                )
        }
        val existing = settingsRepo.findByUserId(principal.userId)
            ?: defaultSettings(principal.userId)
        val now = Clock.System.now()
        val updated = existing.copy(
            timezone = patch.timezone ?: existing.timezone,
            language = patch.language ?: existing.language,
            timeFormat = patch.timeFormat ?: existing.timeFormat,
            glucoseUnit = patch.glucoseUnit ?: existing.glucoseUnit,
            weightUnit = patch.weightUnit ?: existing.weightUnit,
            alarmUrgentHigh = patch.alarmUrgentHigh ?: existing.alarmUrgentHigh,
            alarmHigh = patch.alarmHigh ?: existing.alarmHigh,
            alarmLow = patch.alarmLow ?: existing.alarmLow,
            alarmUrgentLow = patch.alarmUrgentLow ?: existing.alarmUrgentLow,
            sensorDurationHours = patch.sensorDurationHours ?: existing.sensorDurationHours,
            updatedAt = now,
        )

        settingsRepo.save(updated)
        return updated
    }

    suspend fun listUsers(principal: UserPrincipal, search: String?, page: Int, size: Int): List<User> {
        requireAdmin(principal)
        val kcUsers = keycloak.listUsers(search, first = page * size, max = size)
        val validUsers = kcUsers.mapNotNull { kcUser ->
            kcUser.id?.let {
                runCatching { Uuid.parse(it) to kcUser }.getOrElse {
                    logger.warn { "listUsers skipping user with unparseable id='${kcUser.id}'" }
                    null
                }
            }
        }
        return coroutineScope {
            validUsers.map { (userId, kcUser) ->
                async {
                    val roles = keycloak.getUserRoles(userId).toDomainRoles()
                    val settings = settingsRepo.findByUserId(userId)
                    kcUser.toDomain(settings, roles)
                }
            }.awaitAll()
        }
    }

    suspend fun createUser(
        principal: UserPrincipal,
        email: String,
        displayName: String,
        password: String,
        role: Role,
    ): User {
        requireAdmin(principal)
        val (firstName, lastName) = splitDisplayName(displayName)
        val kcUser = KeycloakUser(
            username = email,
            email = email,
            firstName = firstName,
            lastName = lastName,
            enabled = true,
            emailVerified = true,
            credentials = listOf(KeycloakCredential(value = password, temporary = false)),
        )
        val newUserId = keycloak.createUser(kcUser)
        val roleRep = keycloak.getRealmRole(role.toKeycloakName())
        keycloak.assignRoles(newUserId, listOf(roleRep))
        val now = Clock.System.now()
        val settings = defaultSettings(newUserId, now)
        try {
            settingsRepo.save(settings)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error(e) { "admin_create_user db_fail rolling_back userId=$newUserId" }
            runCatching { keycloak.deleteUser(newUserId) }.onFailure { re ->
                logger.error(re) { "admin_create_user rollback_failed userId=$newUserId" }
            }
            throw e
        }
        logger.info { "admin_create_user admin=${principal.userId} newUser=$newUserId role=${role.name}" }
        return User(
            userId = newUserId, email = email, displayName = displayName,
            roles = setOf(role), settings = settings,
        )
    }

    suspend fun getUser(principal: UserPrincipal, targetUserId: Uuid): User {
        if (!principal.canAccess(targetUserId)) throw AuthorizationException("Access denied")
        val kcUser = keycloak.getUser(targetUserId)
        val roles = keycloak.getUserRoles(targetUserId).toDomainRoles()
        val settings = settingsRepo.findByUserId(targetUserId)
        return kcUser.toDomain(settings, roles)
    }

    suspend fun updateUser(
        principal: UserPrincipal,
        targetUserId: Uuid,
        displayName: String?,
        role: Role?,
    ): User {
        requireAdmin(principal)
        val existing = keycloak.getUser(targetUserId)
        if (displayName != null) {
            val (firstName, lastName) = splitDisplayName(displayName)
            keycloak.updateUser(targetUserId, existing.copy(firstName = firstName, lastName = lastName))
        }
        val updatedRoles: Set<Role>
        if (role != null) {
            val currentRoles = keycloak.getUserRoles(targetUserId)
            if (currentRoles.isNotEmpty()) keycloak.removeRoles(targetUserId, currentRoles)
            val newRole = keycloak.getRealmRole(role.toKeycloakName())
            keycloak.assignRoles(targetUserId, listOf(newRole))
            updatedRoles = setOf(role)
        } else {
            updatedRoles = keycloak.getUserRoles(targetUserId).toDomainRoles()
        }
        val updated = keycloak.getUser(targetUserId)
        val settings = settingsRepo.findByUserId(targetUserId)
        return updated.toDomain(settings, updatedRoles)
    }

    suspend fun deleteUser(principal: UserPrincipal, targetUserId: Uuid) {
        requireAdmin(principal)
        keycloak.deleteUser(targetUserId)
        settingsRepo.delete(targetUserId)
        doctorPatientRepo.deleteByUserId(targetUserId)
        logger.info { "admin_delete_user admin=${principal.userId} deletedUser=$targetUserId" }
    }

    private fun requireAdmin(principal: UserPrincipal) {
        if (!principal.isAdmin()) throw AuthorizationException("Admin role required")
    }

    private fun defaultSettings(userId: Uuid, now: Instant = Clock.System.now()) = UserSettings(
        userId = userId,
        createdAt = now,
        updatedAt = now,
    )
}

data class SettingsPatch(
    val timezone: String? = null,
    val language: String? = null,
    val timeFormat: Int? = null,
    val glucoseUnit: String? = null,
    val weightUnit: String? = null,
    val alarmUrgentHigh: Int? = null,
    val alarmHigh: Int? = null,
    val alarmLow: Int? = null,
    val alarmUrgentLow: Int? = null,
    val sensorDurationHours: Int? = null,
)

private fun KeycloakUser.toDomain(settings: UserSettings?, roles: Set<Role>): User {
    val userId = Uuid.parse(requireNotNull(id) { "Keycloak user missing id" })
    val displayName = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { username ?: email.orEmpty() }
    return User(userId = userId, email = email.orEmpty(), displayName = displayName, roles = roles, settings = settings)
}

private fun List<KeycloakRole>.toDomainRoles(): Set<Role> =
    mapNotNull { kcRole -> Role.entries.firstOrNull { it.toKeycloakName() == kcRole.name } }.toSet()

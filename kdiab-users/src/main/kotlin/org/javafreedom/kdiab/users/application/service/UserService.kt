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
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort
import org.javafreedom.kdiab.users.domain.repository.IdentityUserProfile
import org.javafreedom.kdiab.users.domain.repository.PasswordCredential
import org.javafreedom.kdiab.users.domain.repository.UserSettingsRepository

private val logger = KotlinLogging.logger {}

private val VALID_GLUCOSE_UNITS = setOf("mg/dL", "mmol/L")
private val VALID_WEIGHT_UNITS = setOf("kg", "lbs")

private const val ALARM_URGENT_HIGH_MAX = 400
private const val ALARM_URGENT_LOW_MIN = 40
private const val ALARM_THRESHOLD_COUNT = 4
private const val IDX_URGENT_HIGH = 0
private const val IDX_HIGH = 1
private const val IDX_LOW = 2
private const val IDX_URGENT_LOW = 3

class UserService(
    private val identityProvider: IdentityProviderPort,
    private val settingsRepo: UserSettingsRepository,
    private val doctorPatientRepo: DoctorPatientRepository,
) {
    suspend fun getMe(principal: UserPrincipal): User {
        val profile = identityProvider.getUserProfile(principal.userId)
        val settings = settingsRepo.findByUserId(principal.userId)
            ?: defaultSettings(principal.userId).also { settingsRepo.save(it) }
        return profile.toDomain(settings, principal.roles)
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

        validateAlarmThresholds(updated)
        settingsRepo.save(updated)
        return updated
    }

    suspend fun listUsers(principal: UserPrincipal, search: String?, page: Int, size: Int): List<User> {
        requireAdmin(principal)
        val profiles = identityProvider.listUserProfiles(search, first = page * size, max = size)
        val validProfiles = profiles.mapNotNull { profile ->
            profile.id?.let {
                runCatching { Uuid.parse(it) to profile }.getOrElse {
                    logger.warn { "listUsers skipping user with unparseable id='${profile.id}'" }
                    null
                }
            }
        }
        return coroutineScope {
            validProfiles.map { (userId, profile) ->
                async {
                    val roles = identityProvider.getUserRoles(userId)
                    val settings = settingsRepo.findByUserId(userId)
                    profile.toDomain(settings, roles)
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
        val profile = IdentityUserProfile(
            username = email,
            email = email,
            firstName = firstName,
            lastName = lastName,
            enabled = true,
            emailVerified = true,
            passwordCredential = PasswordCredential(value = password, temporary = false),
        )
        val newUserId = identityProvider.createUser(profile)
        identityProvider.assignRoles(newUserId, setOf(role))
        val now = Clock.System.now()
        val settings = defaultSettings(newUserId, now)
        try {
            settingsRepo.save(settings)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error(e) { "admin_create_user db_fail rolling_back userId=$newUserId" }
            runCatching { identityProvider.deleteUser(newUserId) }.onFailure { re ->
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
        val profile = identityProvider.getUserProfile(targetUserId)
        val roles = identityProvider.getUserRoles(targetUserId)
        val settings = settingsRepo.findByUserId(targetUserId)
        return profile.toDomain(settings, roles)
    }

    suspend fun updateUser(
        principal: UserPrincipal,
        targetUserId: Uuid,
        displayName: String?,
        role: Role?,
    ): User {
        requireAdmin(principal)
        val existing = identityProvider.getUserProfile(targetUserId)
        if (displayName != null) {
            val (firstName, lastName) = splitDisplayName(displayName)
            identityProvider.updateUser(targetUserId, existing.copy(firstName = firstName, lastName = lastName))
        }
        val updatedRoles: Set<Role>
        if (role != null) {
            val currentRoles = identityProvider.getUserRoles(targetUserId)
            if (currentRoles.isNotEmpty()) identityProvider.removeRoles(targetUserId, currentRoles)
            identityProvider.assignRoles(targetUserId, setOf(role))
            updatedRoles = setOf(role)
        } else {
            updatedRoles = identityProvider.getUserRoles(targetUserId)
        }
        val updated = identityProvider.getUserProfile(targetUserId)
        val settings = settingsRepo.findByUserId(targetUserId)
        return updated.toDomain(settings, updatedRoles)
    }

    suspend fun deleteUser(principal: UserPrincipal, targetUserId: Uuid) {
        requireAdmin(principal)
        identityProvider.deleteUser(targetUserId)
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

    private fun validateAlarmThresholds(settings: UserSettings) {
        val thresholds = listOfNotNull(
            settings.alarmUrgentHigh,
            settings.alarmHigh,
            settings.alarmLow,
            settings.alarmUrgentLow,
        )
        if (thresholds.size < ALARM_THRESHOLD_COUNT) return
        val urgentHigh = thresholds[IDX_URGENT_HIGH]
        val high = thresholds[IDX_HIGH]
        val low = thresholds[IDX_LOW]
        val urgentLow = thresholds[IDX_URGENT_LOW]
        checkAlarmRange(urgentLow, urgentHigh)
        checkAlarmOrder(urgentHigh, high, low, urgentLow)
    }

    private fun checkAlarmRange(urgentLow: Int, urgentHigh: Int) {
        if (urgentLow < ALARM_URGENT_LOW_MIN)
            throw BusinessValidationException(
                "alarmUrgentLow must be at least $ALARM_URGENT_LOW_MIN mg/dL, got $urgentLow"
            )
        if (urgentHigh > ALARM_URGENT_HIGH_MAX)
            throw BusinessValidationException(
                "alarmUrgentHigh must be at most $ALARM_URGENT_HIGH_MAX mg/dL, got $urgentHigh"
            )
    }

    private fun checkAlarmOrder(urgentHigh: Int, high: Int, low: Int, urgentLow: Int) {
        val errorMessage = "Alarm thresholds must satisfy urgentHigh($urgentHigh) > " +
            "high($high) > low($low) > urgentLow($urgentLow)"
        if (urgentHigh <= high || high <= low)
            throw BusinessValidationException(errorMessage)
        if (low <= urgentLow)
            throw BusinessValidationException(errorMessage)
    }
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

private fun IdentityUserProfile.toDomain(settings: UserSettings?, roles: Set<Role>): User {
    val userId = Uuid.parse(requireNotNull(id) { "Identity provider user missing id" })
    val displayName = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { username ?: email.orEmpty() }
    return User(userId = userId, email = email.orEmpty(), displayName = displayName, roles = roles, settings = settings)
}

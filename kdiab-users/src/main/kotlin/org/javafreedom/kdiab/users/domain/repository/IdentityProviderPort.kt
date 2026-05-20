@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.domain.repository

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.model.Role

/**
 * Port interface for identity provider operations.
 * High-level services depend on this abstraction; the concrete Keycloak implementation
 * lives in the infrastructure layer.
 */
interface IdentityProviderPort {

    /**
     * Returns the raw user profile for [userId]. Throws [org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException]
     * if the user does not exist.
     */
    suspend fun getUserProfile(userId: Uuid): IdentityUserProfile

    /**
     * Returns a page of user profiles matching the optional [search] term.
     */
    suspend fun listUserProfiles(search: String? = null, first: Int = 0, max: Int = 100): List<IdentityUserProfile>

    /**
     * Creates a user and returns the new user's ID.
     * Throws [org.javafreedom.kdiab.common.domain.exception.ConflictException] if the email is already registered.
     */
    suspend fun createUser(profile: IdentityUserProfile): Uuid

    /**
     * Replaces the profile of [userId] with [profile].
     * Throws [org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException] if the user does not exist.
     */
    suspend fun updateUser(userId: Uuid, profile: IdentityUserProfile)

    /**
     * Deletes the user identified by [userId].
     * Throws [org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException] if the user does not exist.
     */
    suspend fun deleteUser(userId: Uuid)

    /**
     * Updates or merges the given [attributes] for [userId].
     * Throws [org.javafreedom.kdiab.common.domain.exception.BusinessValidationException] for protected attribute names.
     */
    suspend fun updateUserAttributes(userId: Uuid, attributes: Map<String, List<String>>)

    /**
     * Returns the [Role]s currently assigned to [userId] in the identity provider.
     */
    suspend fun getUserRoles(userId: Uuid): Set<Role>

    /**
     * Assigns [roles] to [userId] in the identity provider.
     */
    suspend fun assignRoles(userId: Uuid, roles: Set<Role>)

    /**
     * Removes [roles] from [userId] in the identity provider.
     */
    suspend fun removeRoles(userId: Uuid, roles: Set<Role>)
}

/**
 * Domain-neutral user profile record used by [IdentityProviderPort].
 * Callers do not need to know how the identity provider stores users.
 */
data class IdentityUserProfile(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val enabled: Boolean = true,
    val emailVerified: Boolean = false,
    val attributes: Map<String, List<String>>? = null,
    val passwordCredential: PasswordCredential? = null,
)

/**
 * A plain-text password to be set when creating or updating a user.
 */
data class PasswordCredential(
    val value: String,
    val temporary: Boolean = false,
)

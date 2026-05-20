@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.infrastructure.keycloak

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort
import org.javafreedom.kdiab.users.domain.repository.IdentityUserProfile
import org.javafreedom.kdiab.users.domain.repository.PasswordCredential

/**
 * Adapter that implements [IdentityProviderPort] by delegating to [KeycloakAdminClient].
 * Handles mapping between the domain-neutral port types and Keycloak-specific models.
 */
class KeycloakIdentityProviderAdapter(
    private val client: KeycloakAdminClient,
) : IdentityProviderPort {

    override suspend fun getUserProfile(userId: Uuid): IdentityUserProfile =
        client.getUser(userId).toIdentityProfile()

    override suspend fun listUserProfiles(
        search: String?,
        first: Int,
        max: Int,
    ): List<IdentityUserProfile> =
        client.listUsers(search, first, max).map { it.toIdentityProfile() }

    override suspend fun createUser(profile: IdentityUserProfile): Uuid =
        client.createUser(profile.toKeycloakUser())

    override suspend fun updateUser(userId: Uuid, profile: IdentityUserProfile) =
        client.updateUser(userId, profile.toKeycloakUser())

    override suspend fun deleteUser(userId: Uuid) =
        client.deleteUser(userId)

    override suspend fun updateUserAttributes(userId: Uuid, attributes: Map<String, List<String>>) =
        client.updateUserAttributes(userId, attributes)

    override suspend fun getUserRoles(userId: Uuid): Set<Role> =
        client.getUserRoles(userId).toDomainRoles()

    override suspend fun assignRoles(userId: Uuid, roles: Set<Role>) {
        val kcRoles = roles.map { client.getRealmRole(it.toKeycloakName()) }
        client.assignRoles(userId, kcRoles)
    }

    override suspend fun removeRoles(userId: Uuid, roles: Set<Role>) {
        val kcRoles = roles.map { client.getRealmRole(it.toKeycloakName()) }
        client.removeRoles(userId, kcRoles)
    }
}

private fun KeycloakUser.toIdentityProfile() = IdentityUserProfile(
    id = id,
    username = username,
    email = email,
    firstName = firstName,
    lastName = lastName,
    enabled = enabled,
    emailVerified = emailVerified,
    attributes = attributes,
)

private fun IdentityUserProfile.toKeycloakUser() = KeycloakUser(
    id = id,
    username = username,
    email = email,
    firstName = firstName,
    lastName = lastName,
    enabled = enabled,
    emailVerified = emailVerified,
    attributes = attributes,
    credentials = passwordCredential?.let {
        listOf(KeycloakCredential(value = it.value, temporary = it.temporary))
    },
)

private fun List<KeycloakRole>.toDomainRoles(): Set<Role> =
    mapNotNull { kcRole -> Role.entries.firstOrNull { it.toKeycloakName() == kcRole.name } }.toSet()

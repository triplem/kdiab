package org.javafreedom.kdiab.users.infrastructure.keycloak

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KeycloakUser(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    @SerialName("firstName") val firstName: String? = null,
    @SerialName("lastName") val lastName: String? = null,
    val enabled: Boolean = true,
    val emailVerified: Boolean = false,
    val attributes: Map<String, List<String>>? = null,
    val credentials: List<KeycloakCredential>? = null,
    @SerialName("realmRoles") val realmRoles: List<String>? = null,
)

@Serializable
data class KeycloakCredential(
    val type: String = "password",
    val value: String,
    val temporary: Boolean = false,
)

@Serializable
data class KeycloakRole(
    val id: String,
    val name: String,
)

@Serializable
data class KeycloakTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String,
)

package org.javafreedom.kdiab.users.infrastructure.keycloak

import kotlinx.serialization.Serializable

@Serializable
data class KeycloakUser(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val enabled: Boolean = true,
    val emailVerified: Boolean = false,
    val attributes: Map<String, List<String>>? = null,
    val credentials: List<KeycloakCredential>? = null,
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

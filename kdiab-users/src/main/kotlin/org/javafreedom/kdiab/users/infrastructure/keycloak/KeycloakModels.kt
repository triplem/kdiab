// Models aligned with Keycloak 22.0 REST API spec (api/keycloak-admin.json).
// Field names are camelCase as returned by the KC Admin API; no @SerialName needed.
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
) {
    override fun toString() = "KeycloakCredential(type=$type, value=***, temporary=$temporary)"
}

// KC 22 role-mapping POST accepts {id, name} but some versions also require composite/clientRole/containerId.
// Preserving all fields from the GET response ensures the POST payload is always complete.
@Serializable
data class KeycloakRole(
    val id: String,
    val name: String,
    val composite: Boolean? = null,
    val clientRole: Boolean? = null,
    val containerId: String? = null,
)

@Serializable
data class KeycloakServiceClient(
    val id: String? = null,
    val clientId: String? = null,
    val name: String? = null,
    val enabled: Boolean = true,
    val clientAuthenticatorType: String? = null,
    val serviceAccountsEnabled: Boolean = false,
    val standardFlowEnabled: Boolean = false,
    val directAccessGrantsEnabled: Boolean = false,
    val publicClient: Boolean = false,
    val attributes: Map<String, String>? = null,
)

@Serializable
data class KeycloakClientSecret(
    val type: String? = null,
    val value: String? = null,
)

data class KeycloakClientInfo(
    val id: String,
    val clientId: String,
    val secret: String,
    val name: String,
    val expiresAt: kotlinx.datetime.Instant?,
    val createdAt: kotlinx.datetime.Instant,
)

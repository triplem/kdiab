@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.domain.model.ApiKey
import org.javafreedom.kdiab.users.domain.model.ApiKeyCreated
import org.javafreedom.kdiab.users.domain.model.ApiKeyExpiry
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakAdminClient

private val logger = KotlinLogging.logger {}
private const val MAX_API_KEYS = 20

class ApiKeyService(
    private val keycloak: KeycloakAdminClient,
    private val keycloakTokenEndpoint: String,
) {
    suspend fun createApiKey(principal: UserPrincipal, name: String, expiry: ApiKeyExpiry): ApiKeyCreated {
        val userId = principal.userId.toString()
        val existing = keycloak.listServiceClients(userId)
        if (existing.size >= MAX_API_KEYS) {
            throw BusinessValidationException("Maximum of $MAX_API_KEYS API keys allowed per user")
        }

        val expiresAt = expiry.months?.let { months ->
            kotlinx.datetime.Clock.System.now()
                .plus(DateTimePeriod(months = months), TimeZone.UTC)
        }

        val info = keycloak.createServiceClient(userId, name, expiresAt)
        logger.info { "api_key action=create userId=$userId name=$name expiry=$expiry" }

        return ApiKeyCreated(
            apiKey = ApiKey(
                id = info.id,
                clientId = info.clientId,
                name = info.name,
                expiresAt = info.expiresAt,
                createdAt = info.createdAt,
            ),
            secret = info.secret,
            tokenEndpoint = keycloakTokenEndpoint,
        )
    }

    suspend fun listApiKeys(principal: UserPrincipal): List<ApiKey> {
        val userId = principal.userId.toString()
        return keycloak.listServiceClients(userId).map { info ->
            ApiKey(
                id = info.id,
                clientId = info.clientId,
                name = info.name,
                expiresAt = info.expiresAt,
                createdAt = info.createdAt,
            )
        }
    }

    suspend fun revokeApiKey(principal: UserPrincipal, keyId: String) {
        val userId = principal.userId.toString()
        keycloak.deleteServiceClient(keyId, userId)
        logger.info { "api_key action=revoke userId=$userId keyId=$keyId" }
    }
}

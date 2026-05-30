@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.adapters.inbound.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.application.service.ApiKeyService
import org.javafreedom.kdiab.users.domain.model.ApiKey
import org.javafreedom.kdiab.users.domain.model.ApiKeyCreated
import org.javafreedom.kdiab.users.domain.model.ApiKeyExpiry

@Serializable
data class CreateApiKeyRequest(
    val name: String,
    val expiry: String = "NO_EXPIRY",
)

@Serializable
data class ApiKeyResponse(
    val id: String,
    val clientId: String,
    val name: String,
    val expiresAt: String? = null,
    val createdAt: String,
)

@Serializable
data class ApiKeyCreatedResponse(
    val id: String,
    val clientId: String,
    val name: String,
    val secret: String,
    val tokenEndpoint: String,
    val expiresAt: String? = null,
    val createdAt: String,
)

fun ApiKey.toResponse() = ApiKeyResponse(
    id = id,
    clientId = clientId,
    name = name,
    expiresAt = expiresAt?.toString(),
    createdAt = createdAt.toString(),
)

fun ApiKeyCreated.toCreatedResponse() = ApiKeyCreatedResponse(
    id = apiKey.id,
    clientId = apiKey.clientId,
    name = apiKey.name,
    secret = secret,
    tokenEndpoint = tokenEndpoint,
    expiresAt = apiKey.expiresAt?.toString(),
    createdAt = apiKey.createdAt.toString(),
)

fun Route.apiKeyRoutes(apiKeyService: ApiKeyService) {
    authenticate("auth-jwt") {
        post("/users/me/api-keys") {
            val principal = call.principal<UserPrincipal>()!!
            val req = call.receive<CreateApiKeyRequest>()
            if (req.name.isBlank()) throw BusinessValidationException("API key name must not be blank")
            val expiry = runCatching { ApiKeyExpiry.valueOf(req.expiry.uppercase()) }.getOrElse {
                val valid = ApiKeyExpiry.entries.joinToString()
                throw BusinessValidationException("Invalid expiry value: ${req.expiry}. Valid values: $valid")
            }
            val created = apiKeyService.createApiKey(principal, req.name.trim(), expiry)
            call.response.header(HttpHeaders.Location, "/api/v1/users/me/api-keys/${created.apiKey.id}")
            call.respond(HttpStatusCode.Created, created.toCreatedResponse())
        }

        get("/users/me/api-keys") {
            val principal = call.principal<UserPrincipal>()!!
            val keys = apiKeyService.listApiKeys(principal)
            call.respond(keys.map { it.toResponse() })
        }

        delete("/users/me/api-keys/{keyId}") {
            val principal = call.principal<UserPrincipal>()!!
            val keyId = call.parameters["keyId"]
            if (keyId == null) throw BusinessValidationException("keyId is required")
            apiKeyService.revokeApiKey(principal, keyId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

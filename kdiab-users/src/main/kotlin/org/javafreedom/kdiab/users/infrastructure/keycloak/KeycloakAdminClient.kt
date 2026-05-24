@file:Suppress("TooManyFunctions")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.infrastructure.keycloak

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.CircuitBreaker

private val logger = KotlinLogging.logger {}

private val PROTECTED_KC_ATTRIBUTES = setOf("roles")
private const val RANDOM_SUFFIX_LENGTH = 8

private const val DEFAULT_FAILURE_THRESHOLD = 5
private const val DEFAULT_RESET_TIMEOUT_MS = 30_000L
private const val TOKEN_EXPIRY_BUFFER_SECONDS = 30
private const val MILLIS_PER_SECOND = 1000L

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
)

@Suppress("TooManyFunctions")
class KeycloakAdminClient(
    private val baseUrl: String,
    private val realm: String,
    private val clientId: String,
    private val clientSecret: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    private val closed = AtomicBoolean(false)
    private val tokenMutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0L

    private val circuitBreaker = CircuitBreaker(
        "keycloak-admin",
        DEFAULT_FAILURE_THRESHOLD,
        DEFAULT_RESET_TIMEOUT_MS,
        isInfrastructureFailure = { e -> e !is ResourceNotFoundException && e !is ConflictException },
    )

    private suspend fun token(): String = tokenMutex.withLock {
        val now = System.currentTimeMillis() / MILLIS_PER_SECOND
        val existing = cachedToken
        if (existing != null && now < tokenExpiresAt) {
            return@withLock existing
        }
        logger.debug { "keycloak_admin action=token_refresh" }
        val response = httpClient.post("$baseUrl/realms/$realm/protocol/openid-connect/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(FormDataContent(Parameters.build {
                append("grant_type", "client_credentials")
                append("client_id", clientId)
                append("client_secret", clientSecret)
            }))
        }
        check(response.status.isSuccess()) { "Keycloak token request failed: ${response.status}" }
        val tokenResponse = response.body<TokenResponse>()
        cachedToken = tokenResponse.accessToken
        // If KC issues a token shorter than the buffer, cache without subtracting the buffer
        // to avoid an immediately-expired cache entry that triggers a token fetch on every request.
        tokenExpiresAt = if (tokenResponse.expiresIn > TOKEN_EXPIRY_BUFFER_SECONDS) {
            now + tokenResponse.expiresIn - TOKEN_EXPIRY_BUFFER_SECONDS
        } else {
            val ttl = tokenResponse.expiresIn
            val buf = TOKEN_EXPIRY_BUFFER_SECONDS
            logger.warn { "keycloak_admin short_lived_token expiresIn=${ttl}s buffer=${buf}s" }
            now + tokenResponse.expiresIn
        }
        tokenResponse.accessToken
    }

    // Token is fetched before circuitBreaker.execute so token failures do not trip
    // the circuit breaker that guards KC Admin API availability.
    private suspend fun authHeader() = "Bearer ${token()}"

    private fun adminUrl(vararg segments: String) =
        "$baseUrl/admin/realms/$realm/${segments.joinToString("/")}"

    suspend fun listUsers(search: String? = null, first: Int = 0, max: Int = 100): List<KeycloakUser> {
        val auth = authHeader()
        return circuitBreaker.execute {
            val response = httpClient.get(adminUrl("users")) {
                header(HttpHeaders.Authorization, auth)
                parameter("first", first)
                parameter("max", max)
                if (search != null) parameter("search", search)
            }
            check(response.status.isSuccess()) { "listUsers failed: ${response.status}" }
            response.body()
        }
    }

    suspend fun getUser(userId: Uuid): KeycloakUser {
        val auth = authHeader()
        return circuitBreaker.execute {
            val response = httpClient.get(adminUrl("users", "$userId")) {
                header(HttpHeaders.Authorization, auth)
            }
            if (response.status == HttpStatusCode.NotFound) throw ResourceNotFoundException("User $userId not found")
            check(response.status.isSuccess()) { "getUser failed: ${response.status}" }
            response.body()
        }
    }

    suspend fun createUser(user: KeycloakUser): Uuid {
        val auth = authHeader()
        return circuitBreaker.execute {
            val response = httpClient.post(adminUrl("users")) {
                header(HttpHeaders.Authorization, auth)
                contentType(ContentType.Application.Json)
                setBody(user)
            }
            if (response.status == HttpStatusCode.Conflict) {
                throw ConflictException("Email already registered in identity provider")
            }
            check(response.status == HttpStatusCode.Created) { "createUser failed: ${response.status}" }
            val location = response.headers[HttpHeaders.Location]
                ?: error("Keycloak did not return Location header after user creation")
            Uuid.parse(location.substringAfterLast("/"))
        }
    }

    suspend fun updateUser(userId: Uuid, user: KeycloakUser) {
        val auth = authHeader()
        circuitBreaker.execute {
            val response = httpClient.put(adminUrl("users", "$userId")) {
                header(HttpHeaders.Authorization, auth)
                contentType(ContentType.Application.Json)
                setBody(user)
            }
            if (response.status == HttpStatusCode.NotFound) throw ResourceNotFoundException("User $userId not found")
            check(response.status.isSuccess()) { "updateUser failed: ${response.status}" }
        }
    }

    suspend fun deleteUser(userId: Uuid) {
        val auth = authHeader()
        circuitBreaker.execute {
            val response = httpClient.delete(adminUrl("users", "$userId")) {
                header(HttpHeaders.Authorization, auth)
            }
            if (response.status == HttpStatusCode.NotFound) throw ResourceNotFoundException("User $userId not found")
            check(response.status.isSuccess()) { "deleteUser failed: ${response.status}" }
        }
    }

    suspend fun updateUserAttributes(userId: Uuid, attributes: Map<String, List<String>>) {
        val protected = attributes.keys.intersect(PROTECTED_KC_ATTRIBUTES)
        if (protected.isNotEmpty()) {
            throw BusinessValidationException("Cannot modify protected KC attributes: $protected")
        }
        val existing = getUser(userId)
        val merged = (existing.attributes ?: emptyMap()) + attributes
        updateUser(userId, existing.copy(attributes = merged))
    }

    suspend fun getUserRoles(userId: Uuid): List<KeycloakRole> {
        val auth = authHeader()
        return circuitBreaker.execute {
            val response = httpClient.get(adminUrl("users", "$userId", "role-mappings", "realm")) {
                header(HttpHeaders.Authorization, auth)
            }
            check(response.status.isSuccess()) { "getUserRoles failed: ${response.status}" }
            response.body()
        }
    }

    suspend fun assignRoles(userId: Uuid, roles: List<KeycloakRole>) {
        val auth = authHeader()
        circuitBreaker.execute {
            val response = httpClient.post(adminUrl("users", "$userId", "role-mappings", "realm")) {
                header(HttpHeaders.Authorization, auth)
                contentType(ContentType.Application.Json)
                setBody(roles)
            }
            check(response.status.isSuccess()) { "assignRoles failed: ${response.status}" }
        }
    }

    suspend fun removeRoles(userId: Uuid, roles: List<KeycloakRole>) {
        val auth = authHeader()
        circuitBreaker.execute {
            val response = httpClient.delete(adminUrl("users", "$userId", "role-mappings", "realm")) {
                header(HttpHeaders.Authorization, auth)
                contentType(ContentType.Application.Json)
                setBody(roles)
            }
            check(response.status.isSuccess()) { "removeRoles failed: ${response.status}" }
        }
    }

    suspend fun getRealmRole(roleName: String): KeycloakRole {
        val auth = authHeader()
        return circuitBreaker.execute {
            val response = httpClient.get(adminUrl("roles", roleName)) {
                header(HttpHeaders.Authorization, auth)
            }
            if (response.status == HttpStatusCode.NotFound) {
                throw ResourceNotFoundException("Realm role '$roleName' not found")
            }
            check(response.status.isSuccess()) { "getRealmRole failed: ${response.status}" }
            response.body()
        }
    }

    suspend fun createServiceClient(
        userId: String,
        name: String,
        expiresAt: kotlinx.datetime.Instant?,
    ): KeycloakClientInfo {
        val randomSuffix = (1..RANDOM_SUFFIX_LENGTH).map { ('a'..'z').random() }.joinToString("")
        val clientId = "device-$userId-$randomSuffix"
        val createdAt = kotlinx.datetime.Clock.System.now()
        val auth = authHeader()

        val clientUuid = circuitBreaker.execute {
            val response = httpClient.post(adminUrl("clients")) {
                header(HttpHeaders.Authorization, auth)
                contentType(ContentType.Application.Json)
                setBody(KeycloakServiceClient(
                    clientId = clientId,
                    name = name,
                    enabled = true,
                    clientAuthenticatorType = "client-secret",
                    serviceAccountsEnabled = true,
                    standardFlowEnabled = false,
                    directAccessGrantsEnabled = false,
                    publicClient = false,
                    attributes = buildMap {
                        put("kdiab.owner.userId", userId)
                        put("kdiab.key.name", name)
                        put("kdiab.key.created_at", createdAt.toString())
                        if (expiresAt != null) put("kdiab.key.expires_at", expiresAt.toString())
                    },
                ))
            }
            check(response.status == HttpStatusCode.Created) { "createServiceClient failed: ${response.status}" }
            val location = response.headers[HttpHeaders.Location]
                ?: error("Keycloak did not return Location header after client creation")
            location.substringAfterLast("/")
        }

        try {
            val secretValue = circuitBreaker.execute {
                val response = httpClient.get(adminUrl("clients", clientUuid, "client-secret")) {
                    header(HttpHeaders.Authorization, auth)
                }
                check(response.status.isSuccess()) { "getClientSecret failed: ${response.status}" }
                response.body<KeycloakClientSecret>().value
                    ?: error("Keycloak returned no secret value for client $clientUuid")
            }

            val serviceAccountUserId = circuitBreaker.execute {
                val response = httpClient.get(adminUrl("clients", clientUuid, "service-account-user")) {
                    header(HttpHeaders.Authorization, auth)
                }
                check(response.status.isSuccess()) { "getServiceAccountUser failed: ${response.status}" }
                response.body<KeycloakUser>().id
                    ?: error("Keycloak returned no id for service account user of client $clientUuid")
            }

            val patientRole = getRealmRole("PATIENT")
            circuitBreaker.execute {
                val response = httpClient.post(adminUrl("users", serviceAccountUserId, "role-mappings", "realm")) {
                    header(HttpHeaders.Authorization, auth)
                    contentType(ContentType.Application.Json)
                    setBody(listOf(patientRole))
                }
                check(response.status.isSuccess()) { "assignRoles to service account failed: ${response.status}" }
            }

            logger.info { "keycloak_admin action=create_service_client clientUuid=$clientUuid userId=$userId" }
            return KeycloakClientInfo(
                id = clientUuid,
                clientId = clientId,
                secret = secretValue,
                name = name,
                expiresAt = expiresAt,
                createdAt = createdAt,
            )
        } catch (@Suppress("TooGenericExceptionCaught") ex: Exception) {
            val cause = ex.message
            logger.warn { "keycloak_admin action=create_service_client_rollback clientUuid=$clientUuid cause=$cause" }
            runCatching {
                httpClient.delete(adminUrl("clients", clientUuid)) {
                    header(HttpHeaders.Authorization, auth)
                }
            }
            throw ex
        }
    }

    suspend fun listServiceClients(userId: String): List<KeycloakClientInfo> {
        val auth = authHeader()
        val clients = circuitBreaker.execute {
            val response = httpClient.get(adminUrl("clients")) {
                header(HttpHeaders.Authorization, auth)
                parameter("clientId", "device-$userId-")
                parameter("search", "true")
            }
            check(response.status.isSuccess()) { "listServiceClients failed: ${response.status}" }
            response.body<List<KeycloakServiceClient>>()
        }
        return clients
            .filter { it.attributes?.get("kdiab.owner.userId") == userId }
            .mapNotNull { client ->
                val id = client.id ?: return@mapNotNull null
                val clientId = client.clientId ?: return@mapNotNull null
                val name = client.attributes?.get("kdiab.key.name") ?: client.name ?: return@mapNotNull null
                val expiresAtStr = client.attributes?.get("kdiab.key.expires_at")?.takeIf { it.isNotBlank() }
                val expiresAt = expiresAtStr?.let {
                    runCatching { kotlinx.datetime.Instant.parse(it) }.getOrNull()
                }
                val createdAtStr = client.attributes?.get("kdiab.key.created_at")?.takeIf { it.isNotBlank() }
                val createdAt = createdAtStr?.let {
                    runCatching { kotlinx.datetime.Instant.parse(it) }.getOrNull()
                } ?: kotlinx.datetime.Instant.DISTANT_PAST
                KeycloakClientInfo(
                    id = id,
                    clientId = clientId,
                    secret = "",
                    name = name,
                    expiresAt = expiresAt,
                    createdAt = createdAt,
                )
            }
    }

    suspend fun deleteServiceClient(clientUuid: String, ownerUserId: String) {
        val auth = authHeader()
        circuitBreaker.execute {
            val getResp = httpClient.get(adminUrl("clients", clientUuid)) {
                header(HttpHeaders.Authorization, auth)
            }
            if (getResp.status == HttpStatusCode.NotFound) {
                throw ResourceNotFoundException("API key $clientUuid not found")
            }
            check(getResp.status.isSuccess()) { "getClient failed: ${getResp.status}" }
            val client = getResp.body<KeycloakServiceClient>()
            if (client.attributes?.get("kdiab.owner.userId") != ownerUserId) {
                throw ResourceNotFoundException("API key $clientUuid not found")
            }
            val delResp = httpClient.delete(adminUrl("clients", clientUuid)) {
                header(HttpHeaders.Authorization, auth)
            }
            check(delResp.status.isSuccess()) { "deleteServiceClient failed: ${delResp.status}" }
        }
        logger.info { "keycloak_admin action=delete_service_client clientUuid=$clientUuid userId=$ownerUserId" }
    }

    fun close() {
        if (closed.compareAndSet(false, true)) httpClient.close()
    }
}

fun Role.toKeycloakName(): String = when (this) {
    Role.PATIENT -> "PATIENT"
    Role.DOCTOR -> "DOCTOR"
    Role.ADMIN -> "ADMIN"
}

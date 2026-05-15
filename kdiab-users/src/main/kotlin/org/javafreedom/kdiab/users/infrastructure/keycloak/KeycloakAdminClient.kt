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
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.common.domain.model.Role

private val logger = KotlinLogging.logger {}

private const val DEFAULT_FAILURE_THRESHOLD = 5
private const val DEFAULT_RESET_TIMEOUT_MS = 30_000L
private const val TOKEN_EXPIRY_BUFFER_SECONDS = 30
private const val MILLIS_PER_SECOND = 1000L

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

    private val tokenMutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0L

    private val circuitBreaker = CircuitBreaker("keycloak-admin", DEFAULT_FAILURE_THRESHOLD, DEFAULT_RESET_TIMEOUT_MS)

    private suspend fun token(): String = tokenMutex.withLock {
        val now = System.currentTimeMillis() / MILLIS_PER_SECOND
        val existing = cachedToken
        if (existing != null && now < tokenExpiresAt - TOKEN_EXPIRY_BUFFER_SECONDS) {
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
        val tokenResponse = response.body<KeycloakTokenResponse>()
        cachedToken = tokenResponse.accessToken
        tokenExpiresAt = now + tokenResponse.expiresIn
        tokenResponse.accessToken
    }

    private suspend fun authHeader() = "Bearer ${token()}"

    suspend fun listUsers(search: String? = null, first: Int = 0, max: Int = 100): List<KeycloakUser> =
        circuitBreaker.execute {
            val response = httpClient.get("$baseUrl/admin/realms/$realm/users") {
                header(HttpHeaders.Authorization, authHeader())
                parameter("first", first)
                parameter("max", max)
                if (search != null) parameter("search", search)
            }
            check(response.status.isSuccess()) { "listUsers failed: ${response.status}" }
            response.body()
        }

    suspend fun getUser(userId: Uuid): KeycloakUser =
        circuitBreaker.execute {
            val response = httpClient.get("$baseUrl/admin/realms/$realm/users/$userId") {
                header(HttpHeaders.Authorization, authHeader())
            }
            if (response.status == HttpStatusCode.NotFound) throw ResourceNotFoundException("User $userId not found")
            check(response.status.isSuccess()) { "getUser failed: ${response.status}" }
            response.body()
        }

    suspend fun createUser(user: KeycloakUser): Uuid =
        circuitBreaker.execute {
            val response = httpClient.post("$baseUrl/admin/realms/$realm/users") {
                header(HttpHeaders.Authorization, authHeader())
                contentType(ContentType.Application.Json)
                setBody(user)
            }
            check(response.status == HttpStatusCode.Created) { "createUser failed: ${response.status}" }
            val location = response.headers[HttpHeaders.Location]
                ?: error("Keycloak did not return Location header after user creation")
            Uuid.parse(location.substringAfterLast("/"))
        }

    suspend fun updateUser(userId: Uuid, user: KeycloakUser) =
        circuitBreaker.execute {
            val response = httpClient.put("$baseUrl/admin/realms/$realm/users/$userId") {
                header(HttpHeaders.Authorization, authHeader())
                contentType(ContentType.Application.Json)
                setBody(user)
            }
            if (response.status == HttpStatusCode.NotFound) throw ResourceNotFoundException("User $userId not found")
            check(response.status.isSuccess()) { "updateUser failed: ${response.status}" }
        }

    suspend fun deleteUser(userId: Uuid) =
        circuitBreaker.execute {
            val response = httpClient.delete("$baseUrl/admin/realms/$realm/users/$userId") {
                header(HttpHeaders.Authorization, authHeader())
            }
            if (response.status == HttpStatusCode.NotFound) throw ResourceNotFoundException("User $userId not found")
            check(response.status.isSuccess()) { "deleteUser failed: ${response.status}" }
        }

    suspend fun updateUserAttributes(userId: Uuid, attributes: Map<String, List<String>>) =
        circuitBreaker.execute {
            val existing = getUser(userId)
            val merged = (existing.attributes ?: emptyMap()) + attributes
            updateUser(userId, existing.copy(attributes = merged))
        }

    suspend fun getUserRoles(userId: Uuid): List<KeycloakRole> =
        circuitBreaker.execute {
            val response = httpClient.get("$baseUrl/admin/realms/$realm/users/$userId/role-mappings/realm") {
                header(HttpHeaders.Authorization, authHeader())
            }
            check(response.status.isSuccess()) { "getUserRoles failed: ${response.status}" }
            response.body()
        }

    suspend fun assignRoles(userId: Uuid, roles: List<KeycloakRole>) =
        circuitBreaker.execute {
            val response = httpClient.post("$baseUrl/admin/realms/$realm/users/$userId/role-mappings/realm") {
                header(HttpHeaders.Authorization, authHeader())
                contentType(ContentType.Application.Json)
                setBody(roles)
            }
            check(response.status.isSuccess()) { "assignRoles failed: ${response.status}" }
        }

    suspend fun removeRoles(userId: Uuid, roles: List<KeycloakRole>) =
        circuitBreaker.execute {
            val response = httpClient.delete("$baseUrl/admin/realms/$realm/users/$userId/role-mappings/realm") {
                header(HttpHeaders.Authorization, authHeader())
                contentType(ContentType.Application.Json)
                setBody(roles)
            }
            check(response.status.isSuccess()) { "removeRoles failed: ${response.status}" }
        }

    suspend fun getRealmRole(roleName: String): KeycloakRole =
        circuitBreaker.execute {
            val response = httpClient.get("$baseUrl/admin/realms/$realm/roles/$roleName") {
                header(HttpHeaders.Authorization, authHeader())
            }
            if (response.status == HttpStatusCode.NotFound) {
                throw ResourceNotFoundException("Realm role '$roleName' not found")
            }
            check(response.status.isSuccess()) { "getRealmRole failed: ${response.status}" }
            response.body()
        }

    fun close() = httpClient.close()
}

fun Role.toKeycloakName(): String = when (this) {
    Role.PATIENT -> "PATIENT"
    Role.DOCTOR -> "DOCTOR"
    Role.ADMIN -> "ADMIN"
}

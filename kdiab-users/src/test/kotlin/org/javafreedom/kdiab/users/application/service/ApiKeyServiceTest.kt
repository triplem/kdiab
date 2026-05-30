@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.domain.model.ApiKeyExpiry
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakAdminClient
import org.javafreedom.kdiab.users.infrastructure.keycloak.KeycloakClientInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class ApiKeyServiceTest {

    private val keycloak = mockk<KeycloakAdminClient>()
    private val tokenEndpoint = "http://localhost:8081/realms/kdiab/protocol/openid-connect/token"
    private val service = ApiKeyService(keycloak, tokenEndpoint)

    private val principal = UserPrincipal(
        userId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
        roles = setOf(Role.PATIENT),
        allowedPatients = emptySet(),
    )
    private val userId = principal.userId.toString()

    private fun makeClientInfo(id: String = "client-uuid-1", name: String = "My Device") = KeycloakClientInfo(
        id = id,
        clientId = "device-$userId-abcd1234",
        secret = "secret-value",
        name = name,
        expiresAt = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `createApiKey returns ApiKeyCreated with secret and tokenEndpoint`() = runTest {
        coEvery { keycloak.listServiceClients(userId) } returns emptyList()
        coEvery { keycloak.createServiceClient(userId, "My Device", null) } returns makeClientInfo()

        val result = service.createApiKey(principal, "My Device", ApiKeyExpiry.NO_EXPIRY)

        assertEquals("client-uuid-1", result.apiKey.id)
        assertEquals("secret-value", result.secret)
        assertEquals(tokenEndpoint, result.tokenEndpoint)
        assertNull(result.apiKey.expiresAt)
    }

    @Test
    fun `createApiKey computes expiresAt for THREE_MONTHS`() = runTest {
        coEvery { keycloak.listServiceClients(userId) } returns emptyList()
        coEvery { keycloak.createServiceClient(userId, "CGM", any()) } returns makeClientInfo()

        val result = service.createApiKey(principal, "CGM", ApiKeyExpiry.THREE_MONTHS)

        assertNotNull(result.apiKey)
        coVerify { keycloak.createServiceClient(userId, "CGM", any()) }
    }

    @Test
    fun `createApiKey throws when limit reached`() = runTest {
        val existing = (1..20).map { makeClientInfo("id-$it", "key-$it") }
        coEvery { keycloak.listServiceClients(userId) } returns existing

        assertFailsWith<BusinessValidationException> {
            service.createApiKey(principal, "One More", ApiKeyExpiry.NO_EXPIRY)
        }
    }

    @Test
    fun `listApiKeys maps KeycloakClientInfo to ApiKey`() = runTest {
        coEvery { keycloak.listServiceClients(userId) } returns listOf(
            makeClientInfo("id-1", "Key One"),
            makeClientInfo("id-2", "Key Two"),
        )

        val result = service.listApiKeys(principal)

        assertEquals(2, result.size)
        assertEquals("id-1", result[0].id)
        assertEquals("Key One", result[0].name)
        assertEquals("id-2", result[1].id)
    }

    @Test
    fun `revokeApiKey delegates to keycloak deleteServiceClient`() = runTest {
        coEvery { keycloak.deleteServiceClient("key-uuid", userId) } returns Unit

        service.revokeApiKey(principal, "key-uuid")

        coVerify { keycloak.deleteServiceClient("key-uuid", userId) }
    }
}

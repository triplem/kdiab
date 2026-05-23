package org.javafreedom.kdiab.calc.adapters.outbound.http

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.javafreedom.kdiab.calc.domain.exception.UpstreamException
import org.javafreedom.kdiab.common.plugins.CircuitBreaker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProfilesClientTest {

    private val auth = "Bearer test-token"
    private val correlationId = "test-cid"
    private val userId = "user-123"
    private val baseUrl = "http://profiles"

    @Test
    fun `should throw UpstreamException when circuit is open`() = runBlocking {
        // Pre-open the circuit breaker by reaching the failure threshold
        val openCircuitBreaker = CircuitBreaker(
            name = "profiles",
            failureThreshold = 1,
            resetTimeoutMs = 60_000L,
            isInfrastructureFailure = { true },
        )
        // Trip the breaker with one failure
        val engine = MockEngine { _ ->
            respond(content = "", status = HttpStatusCode.ServiceUnavailable)
        }
        val client = ProfilesClient(engine, baseUrl, openCircuitBreaker)
        // First call trips the breaker (503 → UpstreamException → counts as infrastructure failure)
        runCatching { client.getActiveProfile(userId, auth, correlationId) }

        // Second call should fast-fail with UpstreamException(statusCode=503)
        val ex = assertFailsWith<UpstreamException> {
            client.getActiveProfile(userId, auth, correlationId)
        }
        assertEquals(503, ex.statusCode)
        assertEquals("profiles", ex.service)
    }

    @Test
    fun `should throw UpstreamException with statusCode 503 on upstream error response`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(content = "", status = HttpStatusCode.ServiceUnavailable)
        }
        val client = ProfilesClient(engine, baseUrl)

        val ex = assertFailsWith<UpstreamException> {
            client.getActiveProfile(userId, auth, correlationId)
        }
        assertEquals(503, ex.statusCode)
        assertEquals("profiles", ex.service)
    }

    @Test
    fun `should return null when profiles list is empty`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = """{"items":[],"page":0,"size":50,"totalCount":0}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = ProfilesClient(engine, baseUrl)
        val result = client.getActiveProfile(userId, auth, correlationId)
        assertEquals(null, result)
    }

    @Test
    fun `should throw UpstreamException on 403 from upstream`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(content = "", status = HttpStatusCode.Forbidden)
        }
        val client = ProfilesClient(engine, baseUrl)
        val ex = assertFailsWith<UpstreamException> {
            client.getActiveProfile(userId, auth, correlationId)
        }
        assertEquals(403, ex.statusCode)
        assertEquals("profiles", ex.service)
    }
}

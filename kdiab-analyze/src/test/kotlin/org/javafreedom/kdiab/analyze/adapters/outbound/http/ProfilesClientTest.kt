package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProfilesClientTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val auth = "Bearer test-token"
    private val correlationId = "test-cid"
    private val userId = "user-123"
    private val baseUrl = "http://profiles"

    private fun profileJson(id: String, status: String = "ACTIVE") =
        """{"id":"$id","userId":"$userId","status":"$status","name":"My Profile","insulinType":"rapid","durationOfAction":180,"createdAt":"2024-01-01T00:00:00Z"}"""

    private fun pagedResponse(vararg profiles: String, page: Int = 0, size: Int = 50, totalCount: Int = profiles.size) =
        """{"items":[${profiles.joinToString(",")}],"page":$page,"size":$size,"totalCount":$totalCount}"""

    @Test
    fun `getProfiles returns list from paginated response`() = runTest {
        val body = pagedResponse(profileJson("p-1", "ACTIVE"), profileJson("p-2", "ARCHIVED"), totalCount = 2)
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = buildClient(engine)
        val result = client.getProfiles(userId, auth, correlationId)
        assertEquals(2, result.size)
        assertEquals("p-1", result[0].id)
        assertEquals("ACTIVE", result[0].status.value)
        assertEquals("ARCHIVED", result[1].status.value)
    }

    @Test
    fun `getProfiles returns empty list for empty paginated response`() = runTest {
        val body = pagedResponse(totalCount = 0)
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        assertEquals(0, buildClient(engine).getProfiles(userId, auth, correlationId).size)
    }

    @Test
    fun `getProfiles throws UpstreamException on 403`() = runTest {
        val engine = MockEngine { _ ->
            respond(content = "", status = HttpStatusCode.Forbidden)
        }
        assertFailsWith<UpstreamException> { buildClient(engine).getProfiles(userId, auth, correlationId) }
    }

    @Test
    fun `getProfiles sends X-Correlation-ID header`() = runTest {
        var capturedCorrelationId: String? = null
        val engine = MockEngine { request ->
            capturedCorrelationId = request.headers["X-Correlation-ID"]
            respond(
                content = pagedResponse(totalCount = 0),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = buildClient(engine)
        client.getProfiles(userId, auth, correlationId)
        assertEquals(correlationId, capturedCorrelationId)
    }

    private fun buildClient(engine: MockEngine): ProfilesClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        return ProfilesClient(httpClient, baseUrl)
    }
}

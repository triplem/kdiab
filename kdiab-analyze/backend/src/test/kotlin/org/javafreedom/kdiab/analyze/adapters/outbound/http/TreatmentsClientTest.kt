package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TreatmentsClientTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val auth = "Bearer test-token"
    private val correlationId = "test-cid"
    private val userId = "user-123"
    private val baseUrl = "http://treatments"

    private fun treatmentJson(id: String) =
        """{"id":"$id","userId":"$userId","treatedAt":"2024-01-15T08:00:00Z","type":"BOLUS","data":{"insulin":2.5}}"""

    @Test
    fun `getTreatments returns list from plain array response`() = runTest {
        val body = """[${treatmentJson("t-1")},${treatmentJson("t-2")}]"""
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = buildClient(engine)
        val result = client.getTreatments(userId, auth, correlationId)
        assertEquals(2, result.size)
        assertEquals("t-1", result[0].id)
        assertEquals("BOLUS", result[0].type)
    }

    @Test
    fun `getTreatments returns empty list for empty array`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = "[]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = buildClient(engine)
        assertEquals(0, client.getTreatments(userId, auth, correlationId).size)
    }

    @Test
    fun `getTreatments throws UpstreamException on 401`() = runTest {
        val engine = MockEngine { _ ->
            respond(content = "", status = HttpStatusCode.Unauthorized)
        }
        assertFailsWith<UpstreamException> { buildClient(engine).getTreatments(userId, auth, correlationId) }
    }

    @Test
    fun `getTreatments sends X-Correlation-ID header`() = runTest {
        var capturedCorrelationId: String? = null
        val engine = MockEngine { request ->
            capturedCorrelationId = request.headers["X-Correlation-ID"]
            respond(
                content = "[]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = buildClient(engine)
        client.getTreatments(userId, auth, correlationId)
        assertEquals(correlationId, capturedCorrelationId)
    }

    private fun buildClient(engine: MockEngine): TreatmentsClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        return TreatmentsClient(httpClient, baseUrl)
    }
}

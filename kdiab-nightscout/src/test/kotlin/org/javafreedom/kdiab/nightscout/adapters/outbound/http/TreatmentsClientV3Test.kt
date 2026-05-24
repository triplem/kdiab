package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import org.javafreedom.kdiab.common.plugins.CircuitBreaker
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.UpdateTreatmentRequest
import org.javafreedom.kdiab.nightscout.domain.exception.UpstreamException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TreatmentsClientV3Test {

    companion object {
        private const val BASE_URL = "http://localhost:8083"
        private const val USER_ID = "user-123"
        private const val AUTH = "Bearer test-token"
        private const val CORR = "corr-id"
        private const val TREATMENT_ID = "treatment-abc"

        private val pagedTreatmentsJson = """
            {
              "items": [
                {
                  "id": "$TREATMENT_ID",
                  "userId": "$USER_ID",
                  "treatedAt": "2024-01-01T12:00:00Z",
                  "createdAt": "2024-01-01T12:00:00Z",
                  "type": "BOLUS",
                  "data": {"insulin": 2.5},
                  "status": "ACTIVE"
                }
              ],
              "totalCount": 1,
              "page": 0,
              "size": 200
            }
        """.trimIndent()

        private val treatmentResponseJson = """
            {
              "id": "$TREATMENT_ID",
              "userId": "$USER_ID",
              "treatedAt": "2024-01-01T12:00:00Z",
              "createdAt": "2024-01-01T12:00:00Z",
              "type": "BOLUS",
              "data": {"insulin": 2.5},
              "status": "ACTIVE"
            }
        """.trimIndent()
    }

    // ── getTreatment ─────────────────────────────────────────────────────────

    @Test
    fun `getTreatment returns matching item when found in page`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(pagedTreatmentsJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = TreatmentsClient(engine, BASE_URL)
        val result = client.getTreatment(USER_ID, AUTH, CORR, TREATMENT_ID)
        assertNotNull(result)
        assertEquals(TREATMENT_ID, result.id)
    }

    @Test
    fun `getTreatment returns null when id not found in any page`() = runBlocking {
        val emptyPageJson = """{"items":[],"totalCount":0,"page":0,"size":200}"""
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(emptyPageJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = TreatmentsClient(engine, BASE_URL)
        val result = client.getTreatment(USER_ID, AUTH, CORR, "nonexistent-id")
        assertNull(result)
    }

    @Test
    fun `getTreatment throws UpstreamException on 5xx from upstream`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"code":500,"message":"Internal Server Error"}"""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val cb = CircuitBreaker(name = "treatments-get-test", failureThreshold = 1)
        val client = TreatmentsClient(engine, BASE_URL, cb)
        assertFailsWith<UpstreamException> {
            client.getTreatment(USER_ID, AUTH, CORR, TREATMENT_ID)
        }
    }

    // ── updateTreatment ──────────────────────────────────────────────────────

    @Test
    fun `updateTreatment returns updated treatment on success`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(treatmentResponseJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = TreatmentsClient(engine, BASE_URL)
        val request = UpdateTreatmentRequest(
            treatedAt = "2024-01-01T12:00:00Z",
            data = buildJsonObject { put("insulin", 3.0) },
        )
        val result = client.updateTreatment(USER_ID, AUTH, CORR, TREATMENT_ID, request)
        assertEquals(TREATMENT_ID, result.id)
    }

    @Test
    fun `updateTreatment sends PUT to correct path`() = runBlocking {
        var capturedPath = ""
        var capturedMethod = ""
        val engine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedMethod = request.method.value
            respond(
                content = ByteReadChannel(treatmentResponseJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = TreatmentsClient(engine, BASE_URL)
        val request = UpdateTreatmentRequest(
            treatedAt = "2024-01-01T12:00:00Z",
            data = buildJsonObject { put("insulin", 2.5) },
        )
        client.updateTreatment(USER_ID, AUTH, CORR, TREATMENT_ID, request)
        assertEquals("/api/v1/users/$USER_ID/treatments/$TREATMENT_ID", capturedPath)
        assertEquals("PUT", capturedMethod)
    }

    @Test
    fun `updateTreatment throws UpstreamException on 4xx response`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"code":404,"message":"Not Found"}"""),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val cb = CircuitBreaker(name = "treatments-update-test", failureThreshold = 1)
        val client = TreatmentsClient(engine, BASE_URL, cb)
        val request = UpdateTreatmentRequest(
            treatedAt = "2024-01-01T12:00:00Z",
            data = buildJsonObject { put("insulin", 2.5) },
        )
        assertFailsWith<UpstreamException> {
            client.updateTreatment(USER_ID, AUTH, CORR, TREATMENT_ID, request)
        }
    }

    // ── deleteTreatment ──────────────────────────────────────────────────────

    @Test
    fun `deleteTreatment with permanent=false calls archive endpoint`() = runBlocking {
        var capturedPath = ""
        val engine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = TreatmentsClient(engine, BASE_URL)
        client.deleteTreatment(USER_ID, AUTH, CORR, TREATMENT_ID, permanent = false)
        assertEquals("/api/v1/users/$USER_ID/treatments/archive", capturedPath)
    }

    @Test
    fun `deleteTreatment with permanent=true calls delete endpoint`() = runBlocking {
        var capturedPath = ""
        val engine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.NoContent,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = TreatmentsClient(engine, BASE_URL)
        client.deleteTreatment(USER_ID, AUTH, CORR, TREATMENT_ID, permanent = true)
        assertEquals("/api/v1/users/$USER_ID/treatments/delete", capturedPath)
    }

    @Test
    fun `deleteTreatment throws UpstreamException on 5xx response`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"code":500,"message":"Internal Server Error"}"""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val cb = CircuitBreaker(name = "treatments-delete-test", failureThreshold = 1)
        val client = TreatmentsClient(engine, BASE_URL, cb)
        assertFailsWith<UpstreamException> {
            client.deleteTreatment(USER_ID, AUTH, CORR, TREATMENT_ID, permanent = true)
        }
    }
}

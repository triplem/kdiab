package org.javafreedom.kdiab.bff.adapters.outbound.http

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.bff.domain.exception.UpstreamException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Suppress("LongMethod")
class MeasuresClientTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val auth = "Bearer test-token"
    private val correlationId = "test-cid"
    private val userId = "user-123"
    private val baseUrl = "http://measures"

    private fun measure(id: String, sgv: Double = 120.0) = MeasureDto(
        id = id,
        userId = userId,
        measuredAt = "2024-01-15T12:00:00Z",
        type = "CGM",
        data = buildJsonObject { put("value", sgv); put("unit", "mg/dL") },
        status = "ACTIVE",
    )

    private fun pagedJson(items: List<MeasureDto>, page: Int, size: Int, total: Long) =
        buildString {
            append("""{"items":""")
            append(Json.encodeToString(items))
            append(""","page":$page,"size":$size,"totalCount":$total}""")
        }

    // ── single-page response ──────────────────────────────────────────────────

    @Test
    fun `getMeasures returns all items from a single page`() = runTest {
        val items = listOf(measure("m-1"), measure("m-2"))
        val engine = MockEngine { _ ->
            respond(
                content = pagedJson(items, page = 0, size = 200, total = 2),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = buildClient(engine)
        val result = client.getMeasures(userId, auth, correlationId)
        assertEquals(2, result.size)
        assertEquals("m-1", result[0].id)
        assertEquals("m-2", result[1].id)
    }

    // ── multi-page response ───────────────────────────────────────────────────

    @Test
    fun `getMeasures fetches all pages and returns flat list`() = runTest {
        val page0 = (1..3).map { measure("m-$it") }
        val page1 = (4..5).map { measure("m-$it") }
        var callCount = 0

        val engine = MockEngine { request ->
            val page = request.url.parameters["page"]?.toInt() ?: 0
            callCount++
            when (page) {
                0 -> respond(
                    content = pagedJson(page0, page = 0, size = 3, total = 5),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                else -> respond(
                    content = pagedJson(page1, page = 1, size = 3, total = 5),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }

        val client = buildClient(engine)
        val result = client.getMeasures(userId, auth, correlationId)

        assertEquals(5, result.size)
        assertEquals(2, callCount)
        assertEquals("m-1", result[0].id)
        assertEquals("m-5", result[4].id)
    }

    // ── empty response ────────────────────────────────────────────────────────

    @Test
    fun `getMeasures returns empty list when totalCount is zero`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = pagedJson(emptyList(), page = 0, size = 200, total = 0),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = buildClient(engine)
        val result = client.getMeasures(userId, auth, correlationId)
        assertEquals(0, result.size)
    }

    // ── upstream error ────────────────────────────────────────────────────────

    @Test
    fun `getMeasures throws UpstreamException on 401`() = runTest {
        val engine = MockEngine { _ ->
            respond(content = "", status = HttpStatusCode.Unauthorized)
        }
        val client = buildClient(engine)
        assertFailsWith<UpstreamException> {
            client.getMeasures(userId, auth, correlationId)
        }
    }

    @Test
    fun `getMeasures throws UpstreamException on 500`() = runTest {
        val engine = MockEngine { _ ->
            respond(content = "", status = HttpStatusCode.InternalServerError)
        }
        val client = buildClient(engine)
        assertFailsWith<UpstreamException> {
            client.getMeasures(userId, auth, correlationId)
        }
    }

    // ── pagination parameters ─────────────────────────────────────────────────

    @Test
    fun `getMeasures sends correct page and size query parameters`() = runTest {
        val capturedParams = mutableListOf<Pair<String?, String?>>()
        val engine = MockEngine { request ->
            capturedParams += request.url.parameters["page"] to request.url.parameters["size"]
            respond(
                content = pagedJson(emptyList(), page = 0, size = 200, total = 0),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = buildClient(engine)
        client.getMeasures(userId, auth, correlationId)

        assertEquals(1, capturedParams.size)
        assertEquals("0", capturedParams[0].first)
        assertEquals("200", capturedParams[0].second)
    }

    // ── header forwarding ─────────────────────────────────────────────────────

    @Test
    fun `getMeasures sends X-Correlation-ID header`() = runTest {
        var capturedCorrelationId: String? = null
        val engine = MockEngine { request ->
            capturedCorrelationId = request.headers["X-Correlation-ID"]
            respond(
                content = pagedJson(emptyList(), page = 0, size = 200, total = 0),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = buildClient(engine)
        client.getMeasures(userId, auth, correlationId)
        assertEquals(correlationId, capturedCorrelationId)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun buildClient(engine: MockEngine): MeasuresClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        return MeasuresClient(httpClient, baseUrl)
    }
}

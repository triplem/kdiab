package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.analyze.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.analyze.api.upstream.measures.models.MeasureSource
import org.javafreedom.kdiab.analyze.api.upstream.measures.models.MeasureStatus
import org.javafreedom.kdiab.analyze.api.upstream.measures.models.MeasureType
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Suppress("LongMethod")
class MeasuresClientTest {

    private val auth = "Bearer test-token"
    private val correlationId = "test-cid"
    private val userId = "user-123"
    private val baseUrl = "http://measures"

    private fun measure(id: String, sgv: Double = 120.0) = MeasureResponse(
        id = id,
        userId = userId,
        measuredAt = "2024-01-15T12:00:00Z",
        createdAt = "2024-01-15T12:00:00Z",
        type = MeasureType.CGM,
        source = MeasureSource.MANUAL,
        `data` = buildJsonObject { put("value", sgv); put("unit", "mg/dL") },
        status = MeasureStatus.ACTIVE,
    )

    private fun pagedJson(items: List<MeasureResponse>, page: Int, size: Int, total: Long) =
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
        // PAGE_SIZE=200: totalCount=201 forces 2 pages (ceil(201/200)=2)
        val page0 = (1..200).map { measure("m-$it") }
        val page1 = listOf(measure("m-201"))
        var callCount = 0

        val engine = MockEngine { request ->
            val page = request.url.parameters["page"]?.toInt() ?: 0
            callCount++
            when (page) {
                0 -> respond(
                    content = pagedJson(page0, page = 0, size = 200, total = 201),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                else -> respond(
                    content = pagedJson(page1, page = 1, size = 200, total = 201),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }

        val client = buildClient(engine)
        val result = client.getMeasures(userId, auth, correlationId)

        assertEquals(201, result.size)
        assertEquals(2, callCount)
        assertEquals("m-1", result[0].id)
        assertEquals("m-201", result[200].id)
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

    @Test
    fun `getMeasures throws UpstreamException with statusCode when first page returns 500`() = runTest {
        val engine = MockEngine { _ ->
            respond(content = "", status = HttpStatusCode.InternalServerError)
        }
        val client = buildClient(engine)
        val ex = assertFailsWith<UpstreamException> {
            client.getMeasures(userId, auth, correlationId)
        }
        assertEquals(500, ex.statusCode)
    }

    @Test
    fun `getMeasures throws UpstreamException when second page returns 502 and partial data is discarded`() = runTest {
        // PAGE_SIZE=200: totalCount=201 forces 2 pages; page 1 returns 502
        val page0 = (1..200).map { measure("m-$it") }
        var callCount = 0

        val engine = MockEngine { request ->
            val page = request.url.parameters["page"]?.toInt() ?: 0
            callCount++
            when (page) {
                0 -> respond(
                    content = pagedJson(page0, page = 0, size = 200, total = 201),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                else -> respond(content = "", status = HttpStatusCode.BadGateway)
            }
        }

        val client = buildClient(engine)
        val ex = assertFailsWith<UpstreamException> {
            client.getMeasures(userId, auth, correlationId)
        }
        assertEquals(502, ex.statusCode)
        assertEquals(2, callCount)
    }

    @Test
    fun `getMeasures makes exactly one request when first page is empty`() = runTest {
        var callCount = 0
        val engine = MockEngine { _ ->
            callCount++
            respond(
                content = pagedJson(emptyList(), page = 0, size = 200, total = 0),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = buildClient(engine)
        val result = client.getMeasures(userId, auth, correlationId)
        assertEquals(0, result.size)
        assertEquals(1, callCount)
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

    // ── from/to parameters ────────────────────────────────────────────────────

    @Test
    fun `getMeasures sends from and to query parameters when provided`() = runTest {
        val capturedParams = mutableListOf<Triple<String?, String?, String?>>()
        val engine = MockEngine { request ->
            capturedParams += Triple(
                request.url.parameters["from"],
                request.url.parameters["to"],
                request.url.parameters["page"],
            )
            respond(
                content = pagedJson(emptyList(), page = 0, size = 200, total = 0),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = buildClient(engine)
        client.getMeasures(userId, auth, correlationId, from = "2024-01-01T00:00:00Z", to = "2024-01-31T23:59:59Z")

        assertEquals(1, capturedParams.size)
        assertEquals("2024-01-01T00:00:00Z", capturedParams[0].first)
        assertEquals("2024-01-31T23:59:59Z", capturedParams[0].second)
    }

    @Test
    fun `getMeasures does not send from and to when null`() = runTest {
        val capturedParams = mutableListOf<Pair<String?, String?>>()
        val engine = MockEngine { request ->
            capturedParams += request.url.parameters["from"] to request.url.parameters["to"]
            respond(
                content = pagedJson(emptyList(), page = 0, size = 200, total = 0),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = buildClient(engine)
        client.getMeasures(userId, auth, correlationId)

        assertEquals(1, capturedParams.size)
        assertEquals(null, capturedParams[0].first)
        assertEquals(null, capturedParams[0].second)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun buildClient(engine: MockEngine): MeasuresClient =
        MeasuresClient(engine, baseUrl)
}

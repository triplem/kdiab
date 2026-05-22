package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class TreatmentsClientTest {

    private val auth = "Bearer test-token"
    private val correlationId = "test-cid"
    private val userId = "user-123"
    private val baseUrl = "http://treatments"

    private fun treatmentJson(id: String) =
        """{"id":"$id","userId":"$userId","treatedAt":"2024-01-15T08:00:00Z","createdAt":"2024-01-15T08:00:00Z","type":"BOLUS","data":{"insulin":2.5},"status":"ACTIVE"}"""

    private fun pagedJson(items: String, totalCount: Int) =
        """{"items":[$items],"page":0,"size":200,"totalCount":$totalCount}"""

    private fun emptyPagedJson() = pagedJson("", 0)

    @Test
    fun `getTreatments returns list from paged response`() = runBlocking {
        val body = pagedJson("${treatmentJson("t-1")},${treatmentJson("t-2")}", 2)
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
    fun `getTreatments returns empty list when first page is empty`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = emptyPagedJson(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = buildClient(engine)
        val result = client.getTreatments(userId, auth, correlationId)
        assertEquals(0, result.size)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `getTreatments fetches multiple pages until all items retrieved`() = runBlocking {
        // PAGE_SIZE=200: totalCount=201 forces 2 pages (ceil(201/200)=2)
        val page0Items = (1..200).joinToString(",") { treatmentJson("t-$it") }
        val page1Items = treatmentJson("t-201")
        var callCount = 0
        val engine = MockEngine { request ->
            callCount++
            val page = request.url.parameters["page"]?.toInt() ?: 0
            val body = if (page == 0)
                """{"items":[$page0Items],"page":0,"size":200,"totalCount":201}"""
            else
                """{"items":[$page1Items],"page":1,"size":200,"totalCount":201}"""
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val result = buildClient(engine).getTreatments(userId, auth, correlationId)
        assertEquals(201, result.size)
        assertEquals(2, callCount)
    }

    @Test
    fun `getTreatments throws UpstreamException when first page returns 500`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(content = """{"code":500,"message":"Internal Server Error"}""", status = HttpStatusCode.InternalServerError)
        }
        val ex = assertFailsWith<UpstreamException> {
            buildClient(engine).getTreatments(userId, auth, correlationId)
        }
        assertEquals("treatments", ex.service)
        assertEquals(500, ex.statusCode)
    }

    @Test
    fun `getTreatments throws UpstreamException when second page returns 502`() = runBlocking {
        // PAGE_SIZE=200: totalCount=201 forces 2 pages; page 1 returns 502
        val page0Items = (1..200).joinToString(",") { treatmentJson("t-$it") }
        var requestPage = 0
        val engine = MockEngine { _ ->
            if (requestPage++ == 0) {
                respond(
                    content = """{"items":[$page0Items],"page":0,"size":200,"totalCount":201}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond(content = "", status = HttpStatusCode.BadGateway)
            }
        }
        assertFailsWith<UpstreamException> {
            buildClient(engine).getTreatments(userId, auth, correlationId)
        }
    }

    @Test
    fun `getTreatments throws UpstreamException on 401`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(content = "", status = HttpStatusCode.Unauthorized)
        }
        assertFailsWith<UpstreamException> { buildClient(engine).getTreatments(userId, auth, correlationId) }
    }

    @Test
    fun `getTreatments sends X-Correlation-ID header`() = runBlocking {
        var capturedCorrelationId: String? = null
        val engine = MockEngine { request ->
            capturedCorrelationId = request.headers["X-Correlation-ID"]
            respond(
                content = emptyPagedJson(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        buildClient(engine).getTreatments(userId, auth, correlationId)
        assertEquals(correlationId, capturedCorrelationId)
    }

    @Test
    fun `getTreatments sends from and to query parameters when provided`() = runBlocking {
        val capturedParams = mutableListOf<Pair<String?, String?>>()
        val engine = MockEngine { request ->
            capturedParams += request.url.parameters["from"] to request.url.parameters["to"]
            respond(
                content = emptyPagedJson(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        buildClient(engine).getTreatments(
            userId, auth, correlationId,
            from = "2024-01-01T00:00:00Z", to = "2024-01-31T23:59:59Z"
        )
        assertEquals(1, capturedParams.size)
        assertEquals("2024-01-01T00:00:00Z", capturedParams[0].first)
        assertEquals("2024-01-31T23:59:59Z", capturedParams[0].second)
    }

    @Test
    fun `getTreatments does not send from and to when null`() = runBlocking {
        val capturedParams = mutableListOf<Pair<String?, String?>>()
        val engine = MockEngine { request ->
            capturedParams += request.url.parameters["from"] to request.url.parameters["to"]
            respond(
                content = emptyPagedJson(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        buildClient(engine).getTreatments(userId, auth, correlationId)
        assertEquals(1, capturedParams.size)
        assertEquals(null, capturedParams[0].first)
        assertEquals(null, capturedParams[0].second)
    }

    // ── getTreatmentsByType ───────────────────────────────────────────────────

    @Test
    fun `getTreatmentsByType returns list from single-page response`() = runBlocking {
        val body = pagedJson("${treatmentJson("s-1")},${treatmentJson("s-2")}", 2)
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val result = buildClient(engine).getTreatmentsByType(userId, auth, correlationId, "SENSOR_INSERT")
        assertEquals(2, result.size)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `getTreatmentsByType returns empty list when first page is empty`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = emptyPagedJson(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val result = buildClient(engine).getTreatmentsByType(userId, auth, correlationId, "SITE_CHANGE")
        assertEquals(0, result.size)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `getTreatmentsByType fetches multiple pages in parallel`() = runBlocking {
        // totalCount=201 forces 2 pages (ceil(201/200)=2)
        val page0Items = (1..200).joinToString(",") { treatmentJson("s-$it") }
        val page1Items = treatmentJson("s-201")
        var callCount = 0
        val engine = MockEngine { request ->
            callCount++
            val page = request.url.parameters["page"]?.toInt() ?: 0
            val body = if (page == 0)
                """{"items":[$page0Items],"page":0,"size":200,"totalCount":201}"""
            else
                """{"items":[$page1Items],"page":1,"size":200,"totalCount":201}"""
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val result = buildClient(engine).getTreatmentsByType(userId, auth, correlationId, "SENSOR_INSERT")
        assertEquals(201, result.size)
        assertEquals(2, callCount)
    }

    @Test
    fun `getTreatmentsByType throws UpstreamException when first page returns 500`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(content = """{"code":500,"message":"Internal Server Error"}""", status = HttpStatusCode.InternalServerError)
        }
        val ex = assertFailsWith<UpstreamException> {
            buildClient(engine).getTreatmentsByType(userId, auth, correlationId, "SENSOR_INSERT")
        }
        assertEquals("treatments", ex.service)
        assertEquals(500, ex.statusCode)
    }

    @Test
    fun `getTreatmentsByType throws UpstreamException when second page returns 502`() = runBlocking {
        val page0Items = (1..200).joinToString(",") { treatmentJson("s-$it") }
        var requestPage = 0
        val engine = MockEngine { _ ->
            if (requestPage++ == 0) {
                respond(
                    content = """{"items":[$page0Items],"page":0,"size":200,"totalCount":201}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond(content = "", status = HttpStatusCode.BadGateway)
            }
        }
        assertFailsWith<UpstreamException> {
            buildClient(engine).getTreatmentsByType(userId, auth, correlationId, "SENSOR_INSERT")
        }
    }

    // ── getDeviceAge ──────────────────────────────────────────────────────────

    @Test
    fun `getDeviceAge returns device age timestamps`() = runBlocking {
        val body = """{"catheterChangedAt":"2026-05-14T10:30:00Z","reservoirChangedAt":"2026-05-13T08:00:00Z","sensorInsertedAt":"2026-05-12T18:00:00Z","batteryChangedAt":"2026-05-10T14:00:00Z"}"""
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val result = buildClient(engine).getDeviceAge(userId, auth, correlationId)
        assertEquals("2026-05-14T10:30:00Z", result.catheterChangedAt)
        assertEquals("2026-05-13T08:00:00Z", result.reservoirChangedAt)
        assertEquals("2026-05-12T18:00:00Z", result.sensorInsertedAt)
        assertEquals("2026-05-10T14:00:00Z", result.batteryChangedAt)
    }

    @Test
    fun `getDeviceAge returns null fields when no device events recorded`() = runBlocking {
        val body = """{}"""
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val result = buildClient(engine).getDeviceAge(userId, auth, correlationId)
        assertEquals(null, result.catheterChangedAt)
        assertEquals(null, result.reservoirChangedAt)
        assertEquals(null, result.sensorInsertedAt)
        assertEquals(null, result.batteryChangedAt)
    }

    @Test
    fun `getDeviceAge throws UpstreamException on 500`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(content = """{"code":500,"message":"error"}""", status = HttpStatusCode.InternalServerError)
        }
        val ex = assertFailsWith<UpstreamException> {
            buildClient(engine).getDeviceAge(userId, auth, correlationId)
        }
        assertEquals("treatments", ex.service)
        assertEquals(500, ex.statusCode)
    }

    // ── getLatestDeviceStatus ─────────────────────────────────────────────────

    @Test
    fun `getLatestDeviceStatus returns status when found`() = runBlocking {
        val body = """{"id":"abc","userId":"$userId","recordedAt":"2026-05-16T10:00:00Z","device":"AAPS 3.2.0","batteryLevel":87}"""
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val result = buildClient(engine).getLatestDeviceStatus(userId, auth, correlationId)
        assertNotNull(result)
        assertEquals("abc", result.id)
        assertEquals("AAPS 3.2.0", result.device)
        assertEquals(87, result.batteryLevel)
    }

    @Test
    fun `getLatestDeviceStatus returns null when 404`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(content = """{"code":404,"message":"Not Found"}""", status = HttpStatusCode.NotFound)
        }
        val result = buildClient(engine).getLatestDeviceStatus(userId, auth, correlationId)
        assertEquals(null, result)
    }

    @Test
    fun `getLatestDeviceStatus throws UpstreamException on 500`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(content = """{"code":500,"message":"error"}""", status = HttpStatusCode.InternalServerError)
        }
        assertFailsWith<UpstreamException> {
            buildClient(engine).getLatestDeviceStatus(userId, auth, correlationId)
        }
    }

    private fun buildClient(engine: MockEngine): TreatmentsClient =
        TreatmentsClient(engine, baseUrl)
}

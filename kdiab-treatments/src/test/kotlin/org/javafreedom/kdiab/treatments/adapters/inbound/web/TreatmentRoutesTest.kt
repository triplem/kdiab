@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.adapters.inbound.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.treatments.application.service.DeviceStatusService
import org.javafreedom.kdiab.treatments.application.service.TreatmentService
import org.javafreedom.kdiab.treatments.domain.model.Treatment
import org.javafreedom.kdiab.treatments.domain.model.TreatmentType
import org.javafreedom.kdiab.common.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.treatments.domain.repository.TreatmentRepository
import org.javafreedom.kdiab.treatments.module

// Top-level helper: installs mock DI bindings on the Application before module() runs.
// Extracted to avoid implicit-receiver ambiguity when called inside testApplication lambdas.
private fun Application.installMockDi(
    treatmentService: TreatmentService,
    deviceStatusService: DeviceStatusService,
    auditLogRepository: AuditLogRepository,
) {
    install(DI) { }
    dependencies {
        provide<TreatmentService> { treatmentService }
        provide<DeviceStatusService> { deviceStatusService }
        provide<AuditLogRepository> { auditLogRepository }
    }
}

class TreatmentRoutesTest {

    // ── Test JWT helpers ──────────────────────────────────────────────────────

    private companion object {
        const val JWT_SECRET   = "test-secret-for-unit-tests-only"
        const val AUDIENCE     = "treatment"
        const val ISSUER       = "http://localhost:8081/realms/kdiab-treatments"

        const val SARAH_ID     = "11111111-1111-1111-1111-111111111111"
        const val MIKE_ID      = "22222222-2222-2222-2222-222222222222"
        const val DOCTOR_ID    = "33333333-3333-3333-3333-333333333333"
        const val ADMIN_ID     = "55555555-5555-5555-5555-555555555555"
        const val TREATMENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"

        fun token(
            userId: String,
            roles: List<String>,
            allowedPatients: List<String> = emptyList()
        ): String = JWT.create()
            .withSubject(userId)
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("roles", roles)
            .apply { if (allowedPatients.isNotEmpty()) withClaim("allowed_patients", allowedPatients) }
            .sign(Algorithm.HMAC256(JWT_SECRET))

        val sarahToken  get() = token(SARAH_ID,  listOf("PATIENT"))
        val mikeToken   get() = token(MIKE_ID,   listOf("PATIENT"))
        val doctorToken get() = token(DOCTOR_ID, listOf("DOCTOR"), listOf(SARAH_ID))
        val adminToken  get() = token(ADMIN_ID,  listOf("ADMIN"))
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private fun testTreatment(userId: Uuid = Uuid.parse(SARAH_ID)) = Treatment(
        id        = Uuid.parse(TREATMENT_ID),
        userId    = userId,
        treatedAt = Instant.parse("2024-01-01T10:00:00Z"),
        createdAt = Instant.parse("2024-01-01T10:00:00Z"),
        type   = TreatmentType.BOLUS,
        data   = buildJsonObject { put("insulin", 2.5) },
    )

    private val createBody = """
        {"treatedAt":"2024-01-01T10:00:00Z","type":"BOLUS","data":{"insulin":2.5}}
    """.trimIndent()

    private val bulkBody = """{"treatmentIds":["$TREATMENT_ID"]}"""

    // ── Test application setup ────────────────────────────────────────────────

    private fun routeTest(
        block: suspend ApplicationTestBuilder.(TreatmentRepository) -> Unit
    ) {
        val mockRepo = mockk<TreatmentRepository>(relaxed = true)
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "jwt.domain"       to ISSUER,
                    "jwt.audience"     to AUDIENCE,
                    "jwt.realm"        to "kdiab-treatments",
                    "jwt.test"         to "true",
                    "jwt.secret"       to JWT_SECRET,
                    "app.initDatabase" to "false",
                )
            }
            application {
                installMockDi(
                    TreatmentService(mockRepo),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                )
                module()
            }
            block(mockRepo)
        }
    }

    // ── GET /api/v1/users/{userId}/treatments ─────────────────────────────────

    @Test
    fun `list treatments - 401 without auth token`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID/treatments")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `list treatments - 200 patient reads own treatments`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any(), any(), any()) } returns listOf(testTreatment())
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any(), any(), any()) } returns 1L
        val resp = client.get("/api/v1/users/$SARAH_ID/treatments") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list treatments - 403 patient reads another user treatments`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$MIKE_ID/treatments") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `list treatments - 200 doctor reads allowed patient treatments`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any(), any(), any()) } returns listOf(testTreatment())
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any(), any(), any()) } returns 1L
        val resp = client.get("/api/v1/users/$SARAH_ID/treatments") {
            bearerAuth(doctorToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list treatments - 403 doctor reads non-allowed patient treatments`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$MIKE_ID/treatments") {
            bearerAuth(doctorToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `list treatments - 200 admin reads any user treatments`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(MIKE_ID), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { repo.countByUserId(Uuid.parse(MIKE_ID), any(), any(), any()) } returns 0L
        val resp = client.get("/api/v1/users/$MIKE_ID/treatments") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list treatments - 200 with from and to query params`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any(), any(), any()) } returns listOf(testTreatment())
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any(), any(), any()) } returns 1L
        val resp = client.get(
            "/api/v1/users/$SARAH_ID/treatments?from=2024-01-01T00:00:00Z&to=2024-01-31T23:59:59Z"
        ) {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list treatments - 200 type filter returns PagedTreatmentResponse object`() = routeTest { repo ->
        coEvery { repo.findByUserIdAndType(Uuid.parse(SARAH_ID), any(), any(), any(), any()) } returns listOf(testTreatment())
        val resp = client.get("/api/v1/users/$SARAH_ID/treatments?type=BOLUS") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assert(body.trimStart().startsWith("{")) { "Expected JSON object but got: $body" }
        assert(body.contains("\"items\"")) { "Expected 'items' field in paged response: $body" }
        assert(body.contains("\"totalCount\"")) { "Expected 'totalCount' field in paged response: $body" }
    }

    @Test
    fun `list treatments - 400 with invalid from timestamp`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID/treatments?from=not-a-timestamp") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `list treatments - 400 with invalid to timestamp`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID/treatments?to=not-a-timestamp") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── POST /api/v1/users/{userId}/treatments ────────────────────────────────

    @Test
    fun `create treatment - 201 patient creates own treatment`() = routeTest { repo ->
        coEvery { repo.save(any()) } returns testTreatment()
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun `create treatment - 403 patient creates treatment for another user`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$MIKE_ID/treatments") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `create treatment - 201 admin creates treatment for any user`() = routeTest { repo ->
        coEvery { repo.save(any()) } returns testTreatment(Uuid.parse(MIKE_ID))
        val resp = client.post("/api/v1/users/$MIKE_ID/treatments") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun `create treatment - 201 doctor creates treatment for allowed patient`() = routeTest { repo ->
        coEvery { repo.save(any()) } returns testTreatment()
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun `create treatment - 403 doctor creates treatment for non-allowed patient`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$MIKE_ID/treatments") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    // ── POST /api/v1/users/{userId}/treatments/archive ───────────────────────

    @Test
    fun `archive treatments - 200 patient archives own treatments`() = routeTest { repo ->
        coEvery { repo.archiveAll(any(), any()) } just runs
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments/archive") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `archive treatments - 403 patient cannot archive another users treatments`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$MIKE_ID/treatments/archive") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `archive treatments - 200 doctor archives for assigned patient`() = routeTest { repo ->
        coEvery { repo.archiveAll(any(), any()) } just runs
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments/archive") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `archive treatments - 400 when empty treatment ids`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments/archive") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"treatmentIds":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `archive treatments - 400 when bulk size exceeds limit`() = routeTest { _ ->
        val ids = (1..201).map { "\"00000000-0000-0000-0000-${it.toString().padStart(12, '0')}\"" }.joinToString(",")
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments/archive") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"treatmentIds":[$ids]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── POST /api/v1/users/{userId}/treatments/delete ─────────────────────────

    @Test
    fun `delete treatments - 403 patient cannot delete own treatments`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments/delete") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `delete treatments - 403 patient cannot delete another users treatments`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$MIKE_ID/treatments/delete") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `delete treatments - 200 doctor can delete for assigned patient`() = routeTest { repo ->
        coEvery { repo.deleteAll(any(), any()) } just runs
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments/delete") {
            bearerAuth(doctorToken)  // doctor is assigned to SARAH
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `delete treatments - 403 doctor cannot delete for non-assigned patient`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$MIKE_ID/treatments/delete") {
            bearerAuth(doctorToken)  // doctor is only assigned to SARAH, not MIKE
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `delete treatments - 200 admin can delete`() = routeTest { repo ->
        coEvery { repo.deleteAll(any(), any()) } just runs
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments/delete") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `delete treatments - 400 when empty treatment ids`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments/delete") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"treatmentIds":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `delete treatments - 400 when bulk size exceeds limit`() = routeTest { _ ->
        val ids = (1..201).map { "\"00000000-0000-0000-0000-${it.toString().padStart(12, '0')}\"" }.joinToString(",")
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments/delete") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"treatmentIds":[$ids]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── Exception / StatusPages coverage ─────────────────────────────────────

    @Test
    fun `status pages - 409 on ConflictException from repository`() = routeTest { repo ->
        coEvery { repo.save(any()) } throws
            org.javafreedom.kdiab.common.domain.exception.ConflictException("duplicate")
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Conflict, resp.status)
    }

    @Test
    fun `status pages - 400 on BusinessValidationException from repository`() = routeTest { repo ->
        coEvery { repo.save(any()) } throws
            org.javafreedom.kdiab.common.domain.exception.BusinessValidationException("invalid data")
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `status pages - 400 on IllegalArgumentException from repository`() = routeTest { repo ->
        coEvery { repo.save(any()) } throws IllegalArgumentException("bad argument")
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `status pages - 500 on unexpected exception from repository`() = routeTest { repo ->
        coEvery { repo.save(any()) } throws RuntimeException("unexpected")
        val resp = client.post("/api/v1/users/$SARAH_ID/treatments") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.InternalServerError, resp.status)
    }
}

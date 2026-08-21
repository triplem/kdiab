@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.adapters.inbound.web

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.measures.application.service.MeasureService
import org.javafreedom.kdiab.common.domain.model.AuditLog
import org.javafreedom.kdiab.common.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.measures.domain.model.Measure
import org.javafreedom.kdiab.measures.domain.model.MeasureSource
import org.javafreedom.kdiab.measures.domain.model.MeasureStatus
import org.javafreedom.kdiab.measures.domain.model.MeasureType
import org.javafreedom.kdiab.measures.domain.repository.MeasureRepository
import org.javafreedom.kdiab.measures.module

// Top-level helper: installs mock DI bindings on the Application before module() runs.
// Extracted to avoid implicit-receiver ambiguity when called inside testApplication lambdas.
private fun Application.installMockDi(
    measureService: MeasureService,
    auditLogRepository: AuditLogRepository,
) {
    install(DI) { }
    dependencies {
        provide<MeasureService> { measureService }
        provide<AuditLogRepository> { auditLogRepository }
    }
}

class MeasureRoutesTest {

    // ── Test JWT helpers ──────────────────────────────────────────────────────

    private companion object {
        const val JWT_SECRET = "test-secret-for-unit-tests-only-hs256"
        const val AUDIENCE   = "measure"
        const val ISSUER     = "http://localhost:8081/realms/kdiab-measures"

        const val SARAH_ID   = "11111111-1111-1111-1111-111111111111"
        const val MIKE_ID    = "22222222-2222-2222-2222-222222222222"
        const val DOCTOR_ID  = "33333333-3333-3333-3333-333333333333"
        const val ADMIN_ID   = "55555555-5555-5555-5555-555555555555"
        const val MEASURE_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"

        fun token(
            userId: String,
            roles: List<String>,
            allowedPatients: List<String> = emptyList()
        ): String {
            val claims = JWTClaimsSet.Builder()
                .subject(userId)
                .audience(AUDIENCE)
                .issuer(ISSUER)
                .claim("roles", roles)
                .apply { if (allowedPatients.isNotEmpty()) claim("allowed_patients", allowedPatients) }
                .build()
            return SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
                .apply { sign(MACSigner(JWT_SECRET.toByteArray())) }
                .serialize()
        }

        val sarahToken   get() = token(SARAH_ID,  listOf("PATIENT"))
        val mikeToken    get() = token(MIKE_ID,   listOf("PATIENT"))
        val doctorToken  get() = token(DOCTOR_ID, listOf("DOCTOR"), listOf(SARAH_ID))
        val adminToken   get() = token(ADMIN_ID,  listOf("ADMIN"))
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private fun testMeasure(userId: Uuid = Uuid.parse(SARAH_ID)) = Measure(
        id         = Uuid.parse(MEASURE_ID),
        userId     = userId,
        measuredAt = Instant.parse("2024-01-01T10:00:00Z"),
        createdAt  = Instant.parse("2024-01-01T10:00:00Z"),
        type   = MeasureType.BGM,
        source = MeasureSource.MANUAL,
        data   = buildJsonObject { put("mbg", 120) },
        status = MeasureStatus.ACTIVE
    )

    private val createBody = """
        {"measuredAt":"2024-01-01T10:00:00Z","type":"BGM","source":"MANUAL","data":{"mbg":120}}
    """.trimIndent()

    private val bulkBody = """{"measureIds":["$MEASURE_ID"]}"""

    // ── Test application setup ────────────────────────────────────────────────

    private fun routeTest(
        block: suspend ApplicationTestBuilder.(MeasureRepository) -> Unit
    ) {
        val mockRepo = mockk<MeasureRepository>(relaxed = true)
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "jwt.domain"       to ISSUER,
                    "jwt.audience"     to AUDIENCE,
                    "jwt.realm"        to "kdiab-measures",
                    "jwt.test"         to "true",
                    "jwt.secret"       to JWT_SECRET,
                    "app.initDatabase" to "false",
                )
            }
            application {
                installMockDi(MeasureService(mockRepo), mockk(relaxed = true))
                module()
            }
            block(mockRepo)
        }
    }

    // ── GET /api/v1/users/{userId}/measures ───────────────────────────────────

    @Test
    fun `list measures - 401 without auth token`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID/measures")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `list measures - 200 patient reads own measures`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any(), any()) } returns listOf(testMeasure())
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any(), any()) } returns 1L
        val resp = client.get("/api/v1/users/$SARAH_ID/measures") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list measures - 403 patient reads another user measures`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$MIKE_ID/measures") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `list measures - 200 doctor reads allowed patient measures`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any(), any()) } returns listOf(testMeasure())
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any(), any()) } returns 1L
        val resp = client.get("/api/v1/users/$SARAH_ID/measures") {
            bearerAuth(doctorToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list measures - 403 doctor reads non-allowed patient measures`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$MIKE_ID/measures") {
            bearerAuth(doctorToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `list measures - 200 admin reads any user measures`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(MIKE_ID), any(), any(), any(), any()) } returns emptyList()
        coEvery { repo.countByUserId(Uuid.parse(MIKE_ID), any(), any()) } returns 0L
        val resp = client.get("/api/v1/users/$MIKE_ID/measures") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list measures - 200 with from and to query params`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any(), any()) } returns listOf(testMeasure())
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any(), any()) } returns 1L
        val resp = client.get("/api/v1/users/$SARAH_ID/measures?from=2024-01-01T00:00:00Z&to=2024-01-31T23:59:59Z") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list measures - 400 with invalid from timestamp`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID/measures?from=not-a-timestamp") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `list measures - 400 with invalid to timestamp`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID/measures?to=not-a-timestamp") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── POST /api/v1/users/{userId}/measures ──────────────────────────────────

    @Test
    fun `create measure - 201 patient creates own measure`() = routeTest { repo ->
        coEvery { repo.save(any()) } returns testMeasure()
        val resp = client.post("/api/v1/users/$SARAH_ID/measures") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun `create measure - 403 patient creates measure for another user`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$MIKE_ID/measures") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `create measure - 201 admin creates measure for any user`() = routeTest { repo ->
        coEvery { repo.save(any()) } returns testMeasure(Uuid.parse(MIKE_ID))
        val resp = client.post("/api/v1/users/$MIKE_ID/measures") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun `create measure - 201 doctor creates measure for allowed patient`() = routeTest { repo ->
        coEvery { repo.save(any()) } returns testMeasure()
        val resp = client.post("/api/v1/users/$SARAH_ID/measures") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun `create measure - 403 doctor creates measure for non-allowed patient`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$MIKE_ID/measures") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `list measures - 400 with invalid status query param`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID/measures?status=INVALID") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `list measures - 200 with status=ARCHIVED query param`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any(), any(), any()) } returns 0L
        val resp = client.get("/api/v1/users/$SARAH_ID/measures?status=ARCHIVED") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list measures - 200 with glucoseUnit=mmol-L query param`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any(), any(), any()) } returns listOf(testMeasure())
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any(), any(), any()) } returns 1L
        val resp = client.get("/api/v1/users/$SARAH_ID/measures?glucoseUnit=mmol/L") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list measures - 200 with weightUnit=lbs query param`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any(), any(), any()) } returns listOf(testMeasure())
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any(), any(), any()) } returns 1L
        val resp = client.get("/api/v1/users/$SARAH_ID/measures?weightUnit=lbs") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list measures - 200 with explicit page and size query params`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any(), any(), any()) } returns 0L
        val resp = client.get("/api/v1/users/$SARAH_ID/measures?page=1&size=10") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list measures - 200 with negative page coerced to 0`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any(), any(), any()) } returns 0L
        val resp = client.get("/api/v1/users/$SARAH_ID/measures?page=-5") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        coVerify { repo.findByUserId(Uuid.parse(SARAH_ID), 0, any(), any(), any(), any()) }
    }

    @Test
    fun `list measures - 200 with oversized size param clamped to max`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any(), any(), any()) } returns 0L
        val resp = client.get("/api/v1/users/$SARAH_ID/measures?size=9999") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        coVerify { repo.findByUserId(Uuid.parse(SARAH_ID), any(), 200, any(), any(), any()) }
    }

    // ── PUT /api/v1/users/{userId}/measures/{measureId} ───────────────────────

    private val updateBody = """{"measuredAt":"2024-06-01T08:00:00Z","data":{"mbg":95}}"""

    @Test
    fun `update measure - 200 patient updates own measure`() = routeTest { repo ->
        coEvery { repo.update(Uuid.parse(MEASURE_ID), Uuid.parse(SARAH_ID), any(), any()) } returns testMeasure()
        val resp = client.put("/api/v1/users/$SARAH_ID/measures/$MEASURE_ID") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(updateBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `update measure - 403 patient updates measure for another user`() = routeTest { _ ->
        val resp = client.put("/api/v1/users/$MIKE_ID/measures/$MEASURE_ID") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(updateBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `update measure - 400 with invalid measuredAt timestamp`() = routeTest { _ ->
        val resp = client.put("/api/v1/users/$SARAH_ID/measures/$MEASURE_ID") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"measuredAt":"not-a-timestamp","data":{"mbg":95}}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `update measure - 200 admin updates any user measure`() = routeTest { repo ->
        coEvery { repo.update(Uuid.parse(MEASURE_ID), Uuid.parse(SARAH_ID), any(), any()) } returns testMeasure()
        val resp = client.put("/api/v1/users/$SARAH_ID/measures/$MEASURE_ID") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(updateBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── POST /api/v1/users/{userId}/measures/archive ──────────────────────────

    @Test
    fun `archive measures - 200 patient archives own measures`() = routeTest { repo ->
        coEvery { repo.archive(any(), Uuid.parse(SARAH_ID)) } just runs
        val resp = client.post("/api/v1/users/$SARAH_ID/measures/archive") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `archive measures - 403 patient archives another user measures`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$MIKE_ID/measures/archive") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `archive measures - 200 doctor archives allowed patient measures`() = routeTest { repo ->
        coEvery { repo.archive(any(), Uuid.parse(SARAH_ID)) } just runs
        val resp = client.post("/api/v1/users/$SARAH_ID/measures/archive") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `archive measures - 403 doctor archives non-allowed patient measures`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$MIKE_ID/measures/archive") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `archive measures - 200 admin archives measures`() = routeTest { repo ->
        coEvery { repo.archive(any(), Uuid.parse(SARAH_ID)) } just runs
        val resp = client.post("/api/v1/users/$SARAH_ID/measures/archive") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `archive measures - 400 when empty measure ids`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$SARAH_ID/measures/archive") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"measureIds":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `archive measures - 400 when bulk size exceeds limit`() = routeTest { _ ->
        val ids = (1..201).map { "\"00000000-0000-0000-0000-${it.toString().padStart(12, '0')}\"" }.joinToString(",")
        val resp = client.post("/api/v1/users/$SARAH_ID/measures/archive") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"measureIds":[$ids]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── POST /api/v1/users/{userId}/measures/unarchive ────────────────────────

    @Test
    fun `unarchive measures - 200 patient unarchives own measures`() = routeTest { repo ->
        coEvery { repo.unarchive(any(), Uuid.parse(SARAH_ID)) } just runs
        val resp = client.post("/api/v1/users/$SARAH_ID/measures/unarchive") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `unarchive measures - 403 patient unarchives another user measures`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$MIKE_ID/measures/unarchive") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `unarchive measures - 400 when empty measure ids`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$SARAH_ID/measures/unarchive") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"measureIds":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `unarchive measures - 400 when bulk size exceeds limit`() = routeTest { _ ->
        val ids = (1..201).map { "\"00000000-0000-0000-0000-${it.toString().padStart(12, '0')}\"" }.joinToString(",")
        val resp = client.post("/api/v1/users/$SARAH_ID/measures/unarchive") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"measureIds":[$ids]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `unarchive measures - 200 admin unarchives any user measures`() = routeTest { repo ->
        coEvery { repo.unarchive(any(), Uuid.parse(SARAH_ID)) } just runs
        val resp = client.post("/api/v1/users/$SARAH_ID/measures/unarchive") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── DELETE /api/v1/users/{userId}/measures ───────────────────────────────

    @Test
    fun `delete measures - 403 patient cannot delete`() = routeTest { _ ->
        val resp = client.delete("/api/v1/users/$SARAH_ID/measures") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `delete measures - 200 doctor can delete`() = routeTest { repo ->
        coEvery { repo.deleteAll(any(), any()) } just runs
        val resp = client.delete("/api/v1/users/$SARAH_ID/measures") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `delete measures - 200 admin can delete`() = routeTest { repo ->
        coEvery { repo.deleteAll(any(), any()) } just runs
        val resp = client.delete("/api/v1/users/$SARAH_ID/measures") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `delete measures - 403 doctor deletes for non-allowed patient`() = routeTest { _ ->
        val resp = client.delete("/api/v1/users/$MIKE_ID/measures") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(bulkBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `delete measures - 400 when empty measure ids and admin`() = routeTest { _ ->
        val resp = client.delete("/api/v1/users/$SARAH_ID/measures") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"measureIds":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `delete measures - 400 when bulk size exceeds limit and admin`() = routeTest { _ ->
        val ids = (1..201).map { "\"00000000-0000-0000-0000-${it.toString().padStart(12, '0')}\"" }.joinToString(",")
        val resp = client.delete("/api/v1/users/$SARAH_ID/measures") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"measureIds":[$ids]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── Exception / StatusPages coverage ─────────────────────────────────────

    @Test
    fun `status pages - 409 on ConflictException from repository`() = routeTest { repo ->
        coEvery { repo.save(any()) } throws
            org.javafreedom.kdiab.common.domain.exception.ConflictException("duplicate")
        val resp = client.post("/api/v1/users/$SARAH_ID/measures") {
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
        val resp = client.post("/api/v1/users/$SARAH_ID/measures") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `status pages - 400 on IllegalArgumentException from repository`() = routeTest { repo ->
        coEvery { repo.save(any()) } throws IllegalArgumentException("bad argument")
        val resp = client.post("/api/v1/users/$SARAH_ID/measures") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `status pages - 500 on unexpected exception from repository`() = routeTest { repo ->
        coEvery { repo.save(any()) } throws RuntimeException("unexpected")
        val resp = client.post("/api/v1/users/$SARAH_ID/measures") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.InternalServerError, resp.status)
    }

    // ── GET /audit ────────────────────────────────────────────────────────────

    private fun auditRouteTest(
        block: suspend ApplicationTestBuilder.(AuditLogRepository) -> Unit
    ) {
        val auditRepo = mockk<AuditLogRepository>(relaxed = true)
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "jwt.domain"       to ISSUER,
                    "jwt.audience"     to AUDIENCE,
                    "jwt.realm"        to "kdiab-measures",
                    "jwt.test"         to "true",
                    "jwt.secret"       to JWT_SECRET,
                    "app.initDatabase" to "false",
                )
            }
            application {
                install(DI) { }
                dependencies {
                    provide<MeasureService> { MeasureService(mockk(relaxed = true)) }
                    provide<AuditLogRepository> { auditRepo }
                }
                module()
            }
            block(auditRepo)
        }
    }

    @Test
    fun `audit - 403 for non-admin user`() = auditRouteTest { _ ->
        val resp = client.get("/api/v1/audit?patientId=$SARAH_ID&from=2024-01-01T00:00:00Z&to=2024-01-31T23:59:59Z") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `audit - 200 admin gets audit log`() = auditRouteTest { auditRepo ->
        coEvery { auditRepo.findByPatientId(any(), any(), any()) } returns emptyList()
        val resp = client.get("/api/v1/audit?patientId=$SARAH_ID&from=2024-01-01T00:00:00Z&to=2024-01-31T23:59:59Z") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `audit - 400 missing patientId`() = auditRouteTest { _ ->
        val resp = client.get("/api/v1/audit?from=2024-01-01T00:00:00Z&to=2024-01-31T23:59:59Z") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `audit - 400 missing from`() = auditRouteTest { _ ->
        val resp = client.get("/api/v1/audit?patientId=$SARAH_ID&to=2024-01-31T23:59:59Z") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `audit - 400 missing to`() = auditRouteTest { _ ->
        val resp = client.get("/api/v1/audit?patientId=$SARAH_ID&from=2024-01-01T00:00:00Z") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `audit - 400 invalid patientId`() = auditRouteTest { _ ->
        val resp = client.get("/api/v1/audit?patientId=not-a-uuid&from=2024-01-01T00:00:00Z&to=2024-01-31T23:59:59Z") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `audit - 400 invalid from date`() = auditRouteTest { _ ->
        val resp = client.get("/api/v1/audit?patientId=$SARAH_ID&from=not-a-date&to=2024-01-31T23:59:59Z") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `audit - 400 invalid to date`() = auditRouteTest { _ ->
        val resp = client.get("/api/v1/audit?patientId=$SARAH_ID&from=2024-01-01T00:00:00Z&to=not-a-date") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `audit - 200 returns audit log entries`() = auditRouteTest { auditRepo ->
        val entry = AuditLog(
            id = Uuid.parse(MEASURE_ID),
            doctorId = Uuid.parse(DOCTOR_ID),
            patientId = Uuid.parse(SARAH_ID),
            action = "measures.list",
            occurredAt = Instant.parse("2024-01-15T10:00:00Z"),
            ipAddress = "127.0.0.1",
            userAgent = "test-agent",
        )
        coEvery { auditRepo.findByPatientId(any(), any(), any()) } returns listOf(entry)
        val resp = client.get("/api/v1/audit?patientId=$SARAH_ID&from=2024-01-01T00:00:00Z&to=2024-01-31T23:59:59Z") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }
}

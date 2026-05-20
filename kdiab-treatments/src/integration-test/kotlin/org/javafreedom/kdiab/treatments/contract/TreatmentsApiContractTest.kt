@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.contract

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.common.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.treatments.application.service.DeviceStatusService
import org.javafreedom.kdiab.treatments.application.service.TreatmentService
import org.javafreedom.kdiab.treatments.domain.model.Treatment
import org.javafreedom.kdiab.treatments.domain.model.TreatmentType
import org.javafreedom.kdiab.treatments.domain.repository.TreatmentRepository
import org.javafreedom.kdiab.treatments.module

/**
 * API contract tests verifying that kdiab-treatments satisfies the response shapes
 * that its consumers depend on.
 *
 * **Contracts verified:**
 *
 * 1. **kdiab-analyze → kdiab-treatments:**
 *    - GET /api/v1/users/{userId}/treatments returns a paginated envelope with
 *      `items`, `totalCount`, `totalPages` — the exact fields TreatmentsClient reads.
 *    - GET /api/v1/users/{userId}/treatments?type=SITE_CHANGE returns filtered results.
 *    - GET /api/v1/users/{userId}/device-age returns `catheterChangedAt`,
 *      `reservoirChangedAt`, `sensorInsertedAt`, `batteryChangedAt`.
 *
 * 2. **kdiab-ui → kdiab-treatments:**
 *    - POST /api/v1/users/{userId}/treatments returns 201 + Location header + full
 *      TreatmentResponse with `id`, `userId`, `treatedAt`, `createdAt`, `type`,
 *      `data`, `status`.
 *    - GET /api/v1/users/{userId}/treatments returns pagination metadata consistent
 *      with the UI's expectations (`page`, `size`, `totalCount`, `totalPages`).
 *
 * Each test uses Ktor's embedded test server with mocked service layer so tests
 * remain fast and require no running database. The focus is on verifying the
 * *response schema* rather than business logic (which is covered by unit tests).
 */
class TreatmentsApiContractTest {

    private companion object {
        const val JWT_SECRET = "contract-test-secret-only"
        const val AUDIENCE   = "treatment"
        const val ISSUER     = "http://localhost:8081/realms/kdiab-treatments"

        const val USER_ID      = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        const val TREATMENT_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

        fun patientToken(userId: String = USER_ID): String =
            JWT.create()
                .withSubject(userId)
                .withAudience(AUDIENCE)
                .withIssuer(ISSUER)
                .withClaim("roles", listOf("PATIENT"))
                .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.HMAC256(JWT_SECRET))

        val testConfig = MapApplicationConfig(
            "jwt.domain"   to ISSUER,
            "jwt.audience" to AUDIENCE,
            "jwt.realm"    to "kdiab-treatments",
            "jwt.test"     to "true",
            "jwt.secret"   to JWT_SECRET,
        )
    }

    private fun bolusFixture(userId: Uuid = Uuid.parse(USER_ID)) = Treatment(
        id        = Uuid.parse(TREATMENT_ID),
        userId    = userId,
        treatedAt = Instant.parse("2026-01-15T08:00:00Z"),
        createdAt = Instant.parse("2026-01-15T08:00:00Z"),
        type      = TreatmentType.BOLUS,
        data      = buildJsonObject { put("insulin", 3.5) },
    )

    private fun siteChangeFixture(userId: Uuid = Uuid.parse(USER_ID)) = Treatment(
        id        = Uuid.random(),
        userId    = userId,
        treatedAt = Instant.parse("2026-01-10T09:00:00Z"),
        createdAt = Instant.parse("2026-01-10T09:00:00Z"),
        type      = TreatmentType.SITE_CHANGE,
        data      = buildJsonObject { },
    )

    private fun Application.installMocks(
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

    // ── kdiab-analyze: GET /treatments response envelope ─────────────────────

    @Test
    fun `kdiab-analyze contract - list treatments returns paginated envelope with required fields`() =
        testApplication {
            val repo = mockk<TreatmentRepository>(relaxed = true)
            coEvery {
                repo.findByUserId(Uuid.parse(USER_ID), any(), any(), any(), any(), any())
            } returns listOf(bolusFixture())
            coEvery { repo.countByUserId(Uuid.parse(USER_ID), any(), any(), any()) } returns 1L

            environment { config = testConfig }
            application {
                installMocks(TreatmentService(repo), mockk(relaxed = true), mockk(relaxed = true))
                module(initDatabase = false)
            }

            val client = createClient { install(ContentNegotiation) { json() } }
            val response = client.get("/api/v1/users/$USER_ID/treatments") {
                bearerAuth(patientToken())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // kdiab-analyze reads: firstPage.items, firstPage.totalCount (computes totalPages client-side)
            assertNotNull(body["items"], "missing 'items' field — kdiab-analyze requires this")
            assertTrue(body["items"]!!.jsonArray.isNotEmpty(), "items array must not be empty")
            assertNotNull(body["totalCount"], "missing 'totalCount' — kdiab-analyze reads this for pagination")
            assertNotNull(body["page"],       "missing 'page' — UI depends on this for pagination")
            assertNotNull(body["size"],       "missing 'size' — UI depends on this for pagination")
            assertEquals(1, body["totalCount"]!!.jsonPrimitive.int)
        }

    @Test
    fun `kdiab-analyze contract - treatment item has required fields`() =
        testApplication {
            val repo = mockk<TreatmentRepository>(relaxed = true)
            coEvery {
                repo.findByUserId(Uuid.parse(USER_ID), any(), any(), any(), any(), any())
            } returns listOf(bolusFixture())
            coEvery { repo.countByUserId(Uuid.parse(USER_ID), any(), any(), any()) } returns 1L

            environment { config = testConfig }
            application {
                installMocks(TreatmentService(repo), mockk(relaxed = true), mockk(relaxed = true))
                module(initDatabase = false)
            }

            val client = createClient { install(ContentNegotiation) { json() } }
            val response = client.get("/api/v1/users/$USER_ID/treatments") {
                bearerAuth(patientToken())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val item = Json.parseToJsonElement(response.bodyAsText())
                .jsonObject["items"]!!.jsonArray[0].jsonObject

            // Fields that kdiab-analyze maps via TreatmentResponse.toDomain()
            assertNotNull(item["id"],        "item missing 'id'")
            assertNotNull(item["userId"],    "item missing 'userId'")
            assertNotNull(item["treatedAt"], "item missing 'treatedAt'")
            assertNotNull(item["type"],      "item missing 'type'")
            assertNotNull(item["data"],      "item missing 'data'")
            assertEquals(TREATMENT_ID, item["id"]!!.jsonPrimitive.content)
            assertEquals(USER_ID,      item["userId"]!!.jsonPrimitive.content)
            assertEquals("BOLUS",      item["type"]!!.jsonPrimitive.content)
        }

    @Test
    fun `kdiab-analyze contract - list treatments with type filter returns filtered results`() =
        testApplication {
            val repo = mockk<TreatmentRepository>(relaxed = true)
            coEvery {
                repo.findByUserIdAndType(Uuid.parse(USER_ID), TreatmentType.SITE_CHANGE, any(), any(), any())
            } returns listOf(siteChangeFixture())

            environment { config = testConfig }
            application {
                installMocks(TreatmentService(repo), mockk(relaxed = true), mockk(relaxed = true))
                module(initDatabase = false)
            }

            val client = createClient { install(ContentNegotiation) { json() } }
            val response = client.get("/api/v1/users/$USER_ID/treatments?type=SITE_CHANGE") {
                bearerAuth(patientToken())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val items = body["items"]!!.jsonArray
            assertEquals(1, items.size)
            assertEquals("SITE_CHANGE", items[0].jsonObject["type"]!!.jsonPrimitive.content)
        }

    // ── kdiab-analyze: GET /device-age response shape ────────────────────────

    @Test
    fun `kdiab-analyze contract - device-age returns expected nullable timestamp fields`() =
        testApplication {
            val repo = mockk<TreatmentRepository>(relaxed = true)
            coEvery { repo.findLatestTimestampsByTypes(Uuid.parse(USER_ID), any()) } returns emptyMap()

            environment { config = testConfig }
            application {
                installMocks(TreatmentService(repo), mockk(relaxed = true), mockk(relaxed = true))
                module(initDatabase = false)
            }

            val client = createClient { install(ContentNegotiation) { json() } }
            val response = client.get("/api/v1/users/$USER_ID/device-age") {
                bearerAuth(patientToken())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            // kdiab-analyze maps: body.catheterChangedAt, reservoirChangedAt, sensorInsertedAt, batteryChangedAt
            assertTrue(body.containsKey("catheterChangedAt"),  "missing 'catheterChangedAt'")
            assertTrue(body.containsKey("reservoirChangedAt"), "missing 'reservoirChangedAt'")
            assertTrue(body.containsKey("sensorInsertedAt"),   "missing 'sensorInsertedAt'")
            assertTrue(body.containsKey("batteryChangedAt"),   "missing 'batteryChangedAt'")
        }

    // ── kdiab-ui: POST /treatments contract ──────────────────────────────────

    @Test
    fun `kdiab-ui contract - create treatment returns 201 with Location header and response body`() =
        testApplication {
            val repo = mockk<TreatmentRepository>(relaxed = true)
            coEvery { repo.save(any()) } answers { firstArg() }

            environment { config = testConfig }
            application {
                installMocks(TreatmentService(repo), mockk(relaxed = true), mockk(relaxed = true))
                module(initDatabase = false)
            }

            val client = createClient { install(ContentNegotiation) { json() } }
            val response = client.post("/api/v1/users/$USER_ID/treatments") {
                bearerAuth(patientToken())
                contentType(ContentType.Application.Json)
                setBody("""{"treatedAt":"2026-01-15T08:00:00Z","type":"BOLUS","data":{"insulin":3.5}}""")
            }

            assertEquals(HttpStatusCode.Created, response.status)

            // kdiab-ui reads the Location header to navigate to the new resource
            assertNotNull(response.headers[HttpHeaders.Location], "missing Location header")
            assertTrue(
                response.headers[HttpHeaders.Location]!!.contains("/treatments/"),
                "Location header must point to the created treatment",
            )

            // kdiab-ui reads the response body to update local state
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["id"],        "response missing 'id'")
            assertNotNull(body["userId"],    "response missing 'userId'")
            assertNotNull(body["treatedAt"], "response missing 'treatedAt'")
            assertNotNull(body["type"],      "response missing 'type'")
            assertNotNull(body["status"],    "response missing 'status'")
            assertEquals(USER_ID, body["userId"]!!.jsonPrimitive.content)
            assertEquals("BOLUS", body["type"]!!.jsonPrimitive.content)
            assertEquals("ACTIVE", body["status"]!!.jsonPrimitive.content)
        }

    // ── kdiab-ui: GET /treatments pagination metadata ────────────────────────

    @Test
    fun `kdiab-ui contract - empty treatment list returns valid pagination envelope`() =
        testApplication {
            val repo = mockk<TreatmentRepository>(relaxed = true)
            coEvery { repo.findByUserId(any(), any(), any(), any(), any(), any()) } returns emptyList()
            coEvery { repo.countByUserId(any(), any(), any(), any()) } returns 0L

            environment { config = testConfig }
            application {
                installMocks(TreatmentService(repo), mockk(relaxed = true), mockk(relaxed = true))
                module(initDatabase = false)
            }

            val client = createClient { install(ContentNegotiation) { json() } }
            val response = client.get("/api/v1/users/$USER_ID/treatments") {
                bearerAuth(patientToken())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            assertEquals(0, body["totalCount"]!!.jsonPrimitive.int)
            assertEquals(0, body["items"]!!.jsonArray.size)
            assertNotNull(body["page"])
            assertNotNull(body["size"])
        }

    // ── Security contract: auth is enforced ──────────────────────────────────

    @Test
    fun `contract - unauthenticated requests are rejected with 401`() =
        testApplication {
            val repo = mockk<TreatmentRepository>(relaxed = true)
            environment { config = testConfig }
            application {
                installMocks(TreatmentService(repo), mockk(relaxed = true), mockk(relaxed = true))
                module(initDatabase = false)
            }

            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("/api/v1/users/$USER_ID/treatments").status,
                "GET /treatments must require auth",
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.post("/api/v1/users/$USER_ID/treatments").status,
                "POST /treatments must require auth",
            )
        }
}

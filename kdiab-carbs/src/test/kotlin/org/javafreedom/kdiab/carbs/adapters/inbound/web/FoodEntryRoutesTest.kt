@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.carbs.adapters.inbound.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.carbs.application.service.FoodEntryService
import org.javafreedom.kdiab.carbs.domain.model.FoodEntry
import org.javafreedom.kdiab.carbs.domain.model.PagedFoodEntries
import org.javafreedom.kdiab.carbs.domain.repository.FoodEntryRepository
import org.javafreedom.kdiab.carbs.module

// Top-level helper: installs mock DI bindings on the Application before module() runs.
// Extracted to avoid implicit-receiver ambiguity when called inside testApplication lambdas.
private fun Application.installMockDi(foodEntryService: FoodEntryService) {
    install(DI) { }
    dependencies {
        provide<FoodEntryService> { foodEntryService }
    }
}

class FoodEntryRoutesTest {

    // ── Test JWT helpers ──────────────────────────────────────────────────────

    private companion object {
        const val JWT_SECRET  = "test-secret-for-unit-tests-only"
        const val AUDIENCE    = "carbs"
        const val ISSUER      = "http://localhost:8081/realms/kdiab-carbs"

        const val SARAH_ID    = "11111111-1111-1111-1111-111111111111"
        const val MIKE_ID     = "22222222-2222-2222-2222-222222222222"
        const val DOCTOR_ID   = "33333333-3333-3333-3333-333333333333"
        const val ADMIN_ID    = "55555555-5555-5555-5555-555555555555"
        const val FOOD_ID     = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"

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

    private fun testEntry(userId: Uuid = Uuid.parse(SARAH_ID)) = FoodEntry(
        id           = Uuid.parse(FOOD_ID),
        userId       = userId,
        name         = "White rice",
        portionGrams = 150.0,
        carbsPer100g = 28.0,
        createdAt    = Instant.parse("2024-01-01T10:00:00Z"),
        updatedAt    = Instant.parse("2024-01-01T10:00:00Z"),
    )

    private val createBody = """{"name":"White rice","portionGrams":150.0,"carbsPer100g":28.0}"""
    private val updateBody = """{"name":"Brown rice","portionGrams":200.0,"carbsPer100g":23.0}"""

    // ── Test application setup ────────────────────────────────────────────────

    private fun routeTest(
        block: suspend ApplicationTestBuilder.(FoodEntryRepository) -> Unit
    ) {
        val mockRepo = mockk<FoodEntryRepository>(relaxed = true)
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "jwt.domain"       to ISSUER,
                    "jwt.audience"     to AUDIENCE,
                    "jwt.realm"        to "kdiab-carbs",
                    "jwt.test"         to "true",
                    "jwt.secret"       to JWT_SECRET,
                    "app.initDatabase" to "false",
                )
            }
            application {
                installMockDi(FoodEntryService(mockRepo))
                module()
            }
            block(mockRepo)
        }
    }

    // ── GET /api/v1/users/{userId}/foods ──────────────────────────────────────

    @Test
    fun `list foods - 401 without auth token`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID/foods")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `list foods - 200 patient reads own foods`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any()) } returns listOf(testEntry())
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any()) } returns 1L
        val resp = client.get("/api/v1/users/$SARAH_ID/foods") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list foods - 403 patient reads another user foods`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$MIKE_ID/foods") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `list foods - 200 doctor reads allowed patient foods`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any()) } returns listOf(testEntry())
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any()) } returns 1L
        val resp = client.get("/api/v1/users/$SARAH_ID/foods") {
            bearerAuth(doctorToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `list foods - 403 doctor reads non-allowed patient foods`() = routeTest { _ ->
        val resp = client.get("/api/v1/users/$MIKE_ID/foods") {
            bearerAuth(doctorToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `list foods - 200 admin reads any user foods`() = routeTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(MIKE_ID), any(), any(), any()) } returns emptyList()
        coEvery { repo.countByUserId(Uuid.parse(MIKE_ID), any()) } returns 0L
        val resp = client.get("/api/v1/users/$MIKE_ID/foods") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── POST /api/v1/users/{userId}/foods ─────────────────────────────────────

    @Test
    fun `create food entry - 401 without auth token`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$SARAH_ID/foods") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `create food entry - 201 patient creates own food entry`() = routeTest { repo ->
        coEvery { repo.save(any()) } returns testEntry()
        val resp = client.post("/api/v1/users/$SARAH_ID/foods") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun `create food entry - 403 patient creates food entry for another user`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$MIKE_ID/foods") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `create food entry - 201 admin creates food entry for any user`() = routeTest { repo ->
        coEvery { repo.save(any()) } returns testEntry(Uuid.parse(MIKE_ID))
        val resp = client.post("/api/v1/users/$MIKE_ID/foods") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun `create food entry - 403 doctor cannot create food entry for patient`() = routeTest { _ ->
        // checkWriteAccess only allows self or admin — doctors cannot write
        val resp = client.post("/api/v1/users/$SARAH_ID/foods") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    // ── POST /api/v1/users/{userId}/foods/{foodId}/archive ────────────────────

    @Test
    fun `archive food entry - 401 without auth token`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$SARAH_ID/foods/$FOOD_ID/archive")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `archive food entry - 200 patient archives own food entry`() = routeTest { repo ->
        coEvery { repo.archive(Uuid.parse(FOOD_ID), Uuid.parse(SARAH_ID)) } returns testEntry()
        val resp = client.post("/api/v1/users/$SARAH_ID/foods/$FOOD_ID/archive") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `archive food entry - 403 patient archives another user food entry`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$MIKE_ID/foods/$FOOD_ID/archive") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `archive food entry - 200 admin archives any food entry`() = routeTest { repo ->
        coEvery { repo.archive(Uuid.parse(FOOD_ID), Uuid.parse(SARAH_ID)) } returns testEntry()
        val resp = client.post("/api/v1/users/$SARAH_ID/foods/$FOOD_ID/archive") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `archive food entry - 403 doctor cannot archive patient food entry`() = routeTest { _ ->
        val resp = client.post("/api/v1/users/$SARAH_ID/foods/$FOOD_ID/archive") {
            bearerAuth(doctorToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    // ── PUT /api/v1/users/{userId}/foods/{foodId} ─────────────────────────────

    @Test
    fun `update food entry - 401 without auth token`() = routeTest { _ ->
        val resp = client.put("/api/v1/users/$SARAH_ID/foods/$FOOD_ID") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(updateBody)
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `update food entry - 200 patient updates own food entry`() = routeTest { repo ->
        coEvery { repo.update(any(), any(), any(), any(), any()) } returns testEntry()
        val resp = client.put("/api/v1/users/$SARAH_ID/foods/$FOOD_ID") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(updateBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `update food entry - 403 patient updates another user food entry`() = routeTest { _ ->
        val resp = client.put("/api/v1/users/$MIKE_ID/foods/$FOOD_ID") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(updateBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `update food entry - 200 admin updates any food entry`() = routeTest { repo ->
        coEvery { repo.update(any(), any(), any(), any(), any()) } returns testEntry()
        val resp = client.put("/api/v1/users/$SARAH_ID/foods/$FOOD_ID") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(updateBody)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `update food entry - 403 doctor cannot update patient food entry`() = routeTest { _ ->
        val resp = client.put("/api/v1/users/$SARAH_ID/foods/$FOOD_ID") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(updateBody)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    // ── DELETE /api/v1/users/{userId}/foods/{foodId} ──────────────────────────

    @Test
    fun `delete food entry - 401 without auth token`() = routeTest { _ ->
        val resp = client.delete("/api/v1/users/$SARAH_ID/foods/$FOOD_ID")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `delete food entry - 204 patient deletes own food entry`() = routeTest { repo ->
        coEvery { repo.delete(Uuid.parse(FOOD_ID), Uuid.parse(SARAH_ID)) } just runs
        val resp = client.delete("/api/v1/users/$SARAH_ID/foods/$FOOD_ID") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)
    }

    @Test
    fun `delete food entry - 403 patient deletes another user food entry`() = routeTest { _ ->
        val resp = client.delete("/api/v1/users/$MIKE_ID/foods/$FOOD_ID") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `delete food entry - 204 admin deletes any food entry`() = routeTest { repo ->
        coEvery { repo.delete(any(), any()) } just runs
        val resp = client.delete("/api/v1/users/$SARAH_ID/foods/$FOOD_ID") {
            bearerAuth(adminToken)
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)
    }

    @Test
    fun `delete food entry - 403 doctor cannot delete patient food entry`() = routeTest { _ ->
        val resp = client.delete("/api/v1/users/$SARAH_ID/foods/$FOOD_ID") {
            bearerAuth(doctorToken)
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    // ── Exception / StatusPages coverage ─────────────────────────────────────

    @Test
    fun `status pages - 409 on ConflictException from repository`() = routeTest { repo ->
        coEvery { repo.save(any()) } throws
            org.javafreedom.kdiab.common.domain.exception.ConflictException("duplicate entry")
        val resp = client.post("/api/v1/users/$SARAH_ID/foods") {
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
        val resp = client.post("/api/v1/users/$SARAH_ID/foods") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `status pages - 404 on ResourceNotFoundException from repository`() = routeTest { repo ->
        coEvery { repo.archive(any(), any()) } throws
            org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException("not found")
        val resp = client.post("/api/v1/users/$SARAH_ID/foods/$FOOD_ID/archive") {
            bearerAuth(sarahToken)
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `status pages - 500 on unexpected exception from repository`() = routeTest { repo ->
        coEvery { repo.save(any()) } throws RuntimeException("unexpected")
        val resp = client.post("/api/v1/users/$SARAH_ID/foods") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(createBody)
        }
        assertEquals(HttpStatusCode.InternalServerError, resp.status)
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.carbs.adapters.inbound.web

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
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.carbs.application.service.FoodEntryService
import org.javafreedom.kdiab.carbs.domain.model.FoodEntry
import org.javafreedom.kdiab.carbs.domain.repository.FoodEntryRepository
import org.javafreedom.kdiab.carbs.module
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException

private fun Application.installMockDi(foodEntryService: FoodEntryService) {
    install(DI) { }
    dependencies {
        provide<FoodEntryService> { foodEntryService }
    }
}

/**
 * Integration tests for the food entry CRUD API (GET/POST/PUT/DELETE).
 *
 * These tests wire the real FoodEntryService (not a mock) against a mocked repository so that
 * the full HTTP stack is exercised: route handler → FoodEntryService → StatusPages mapping.
 *
 * The FoodEntryRepository is mocked because the database layer is covered separately by
 * ExposedFoodEntryRepositoryTest.
 */
class FoodEntryApiTest {

    private companion object {
        const val JWT_HMAC_SEED = "unit-test-jwt-hmac-seed-hs256-pad0000"
        const val AUDIENCE      = "carbs"
        const val ISSUER        = "http://localhost:8081/realms/kdiab-carbs"

        const val SARAH_ID  = "11111111-1111-1111-1111-111111111111"
        const val MIKE_ID   = "22222222-2222-2222-2222-222222222222"
        const val DOCTOR_ID = "33333333-3333-3333-3333-333333333333"
        const val ADMIN_ID  = "55555555-5555-5555-5555-555555555555"
        const val FOOD_ID   = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"

        fun token(
            userId: String,
            roles: List<String>,
            allowedPatients: List<String> = emptyList(),
        ): String = SignedJWT(JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.Builder()
            .subject(userId)
            .audience(AUDIENCE)
            .issuer(ISSUER)
            .claim("roles", roles)
            .apply { if (allowedPatients.isNotEmpty()) claim("allowed_patients", allowedPatients) }
            .build()).apply { sign(MACSigner(JWT_HMAC_SEED.toByteArray())) }.serialize()

        val sarahToken  get() = token(SARAH_ID,  listOf("PATIENT"))
        val mikeToken   get() = token(MIKE_ID,   listOf("PATIENT"))
        val doctorToken get() = token(DOCTOR_ID, listOf("DOCTOR"), allowedPatients = listOf(SARAH_ID))
        val adminToken  get() = token(ADMIN_ID,  listOf("ADMIN"))

        fun testEntry(userId: Uuid = Uuid.parse(SARAH_ID)): FoodEntry {
            val now = Clock.System.now()
            return FoodEntry(
                id           = Uuid.parse(FOOD_ID),
                userId       = userId,
                name         = "White rice",
                portionGrams = 150.0,
                carbsPer100g = 28.0,
                createdAt    = now,
                updatedAt    = now,
            )
        }
    }

    private fun carbsApiTest(
        block: suspend ApplicationTestBuilder.(FoodEntryRepository) -> Unit,
    ) {
        val mockRepo = mockk<FoodEntryRepository>(relaxed = true)

        testApplication {
            environment {
                config = MapApplicationConfig(
                    "jwt.domain"       to ISSUER,
                    "jwt.audience"     to AUDIENCE,
                    "jwt.realm"        to "kdiab-carbs",
                    "jwt.test"         to "true",
                    "jwt.secret"       to JWT_HMAC_SEED,
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

    // ── Auth checks ───────────────────────────────────────────────────────────

    @Test
    fun `GET foods - 401 without auth token`() = carbsApiTest { _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID/foods")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST create food - 401 without auth token`() = carbsApiTest { _ ->
        val resp = client.post("/api/v1/users/$SARAH_ID/foods") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"Rice","portionGrams":150.0,"carbsPer100g":28.0}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    // ── GET /api/v1/users/{userId}/foods ──────────────────────────────────────

    @Test
    fun `GET foods - 200 patient reads own entries`() = carbsApiTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any()) } returns listOf(testEntry())
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any()) } returns 1L
        val resp = client.get("/api/v1/users/$SARAH_ID/foods") { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `GET foods - 403 patient reads another user entries`() = carbsApiTest { _ ->
        val resp = client.get("/api/v1/users/$MIKE_ID/foods") { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `GET foods - 200 doctor reads allowed patient entries`() = carbsApiTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(SARAH_ID), any(), any(), any()) } returns listOf(testEntry())
        coEvery { repo.countByUserId(Uuid.parse(SARAH_ID), any()) } returns 1L
        val resp = client.get("/api/v1/users/$SARAH_ID/foods") { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `GET foods - 403 doctor reads non-allowed patient entries`() = carbsApiTest { _ ->
        val resp = client.get("/api/v1/users/$MIKE_ID/foods") { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `GET foods - 200 admin reads any user entries`() = carbsApiTest { repo ->
        coEvery { repo.findByUserId(Uuid.parse(MIKE_ID), any(), any(), any()) } returns emptyList()
        coEvery { repo.countByUserId(Uuid.parse(MIKE_ID), any()) } returns 0L
        val resp = client.get("/api/v1/users/$MIKE_ID/foods") { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── POST /api/v1/users/{userId}/foods ─────────────────────────────────────

    @Test
    fun `POST create food - 201 patient creates own entry`() = carbsApiTest { repo ->
        coEvery { repo.save(any()) } returns testEntry()
        val resp = client.post("/api/v1/users/$SARAH_ID/foods") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"White rice","portionGrams":150.0,"carbsPer100g":28.0}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        val location = resp.headers[HttpHeaders.Location]
        assertNotNull(location)
        assertTrue(location.contains(SARAH_ID))
    }

    @Test
    fun `POST create food - 403 patient creates entry for another user`() = carbsApiTest { _ ->
        val resp = client.post("/api/v1/users/$MIKE_ID/foods") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"White rice","portionGrams":150.0,"carbsPer100g":28.0}""")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `POST create food - 403 doctor cannot create entry for patient`() = carbsApiTest { _ ->
        val resp = client.post("/api/v1/users/$SARAH_ID/foods") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"White rice","portionGrams":150.0,"carbsPer100g":28.0}""")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `POST create food - 201 admin creates entry for any user`() = carbsApiTest { repo ->
        coEvery { repo.save(any()) } returns testEntry(Uuid.parse(MIKE_ID))
        val resp = client.post("/api/v1/users/$MIKE_ID/foods") {
            bearerAuth(adminToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"White rice","portionGrams":150.0,"carbsPer100g":28.0}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status)
    }

    @Test
    fun `POST create food - 409 on duplicate entry`() = carbsApiTest { repo ->
        coEvery { repo.save(any()) } throws ConflictException("duplicate entry")
        val resp = client.post("/api/v1/users/$SARAH_ID/foods") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"White rice","portionGrams":150.0,"carbsPer100g":28.0}""")
        }
        assertEquals(HttpStatusCode.Conflict, resp.status)
    }

    // ── PUT /api/v1/users/{userId}/foods/{foodId} ─────────────────────────────

    @Test
    fun `PUT update food - 200 patient updates own entry`() = carbsApiTest { repo ->
        coEvery { repo.update(any(), any(), any(), any(), any()) } returns testEntry()
        val resp = client.put("/api/v1/users/$SARAH_ID/foods/$FOOD_ID") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"Brown rice","portionGrams":200.0,"carbsPer100g":23.0}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `PUT update food - 403 patient updates another user entry`() = carbsApiTest { _ ->
        val resp = client.put("/api/v1/users/$MIKE_ID/foods/$FOOD_ID") {
            bearerAuth(sarahToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"Brown rice","portionGrams":200.0,"carbsPer100g":23.0}""")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `PUT update food - 403 doctor cannot update patient food entry`() = carbsApiTest { _ ->
        val resp = client.put("/api/v1/users/$SARAH_ID/foods/$FOOD_ID") {
            bearerAuth(doctorToken)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name":"Brown rice","portionGrams":200.0,"carbsPer100g":23.0}""")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    // ── DELETE /api/v1/users/{userId}/foods/{foodId} ──────────────────────────

    @Test
    fun `DELETE food - 204 patient deletes own entry`() = carbsApiTest { repo ->
        coEvery { repo.delete(Uuid.parse(FOOD_ID), Uuid.parse(SARAH_ID)) } just runs
        val resp = client.delete("/api/v1/users/$SARAH_ID/foods/$FOOD_ID") { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.NoContent, resp.status)
    }

    @Test
    fun `DELETE food - 403 patient deletes another user entry`() = carbsApiTest { _ ->
        val resp = client.delete("/api/v1/users/$MIKE_ID/foods/$FOOD_ID") { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `DELETE food - 204 admin deletes any entry`() = carbsApiTest { repo ->
        coEvery { repo.delete(any(), any()) } just runs
        val resp = client.delete("/api/v1/users/$SARAH_ID/foods/$FOOD_ID") { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.NoContent, resp.status)
    }

    @Test
    fun `DELETE food - 403 doctor cannot delete patient food entry`() = carbsApiTest { _ ->
        val resp = client.delete("/api/v1/users/$SARAH_ID/foods/$FOOD_ID") { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    // ── POST /api/v1/users/{userId}/foods/{foodId}/archive ────────────────────

    @Test
    fun `POST archive food - 404 when entry not found`() = carbsApiTest { repo ->
        coEvery { repo.archive(Uuid.parse(FOOD_ID), Uuid.parse(SARAH_ID)) } throws
            ResourceNotFoundException("food entry $FOOD_ID not found")
        val resp = client.post("/api/v1/users/$SARAH_ID/foods/$FOOD_ID/archive") { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `POST archive food - 200 patient archives own entry`() = carbsApiTest { repo ->
        coEvery { repo.archive(Uuid.parse(FOOD_ID), Uuid.parse(SARAH_ID)) } returns testEntry()
        val resp = client.post("/api/v1/users/$SARAH_ID/foods/$FOOD_ID/archive") { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `POST archive food - 403 doctor cannot archive patient food entry`() = carbsApiTest { _ ->
        val resp = client.post("/api/v1/users/$SARAH_ID/foods/$FOOD_ID/archive") { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }
}

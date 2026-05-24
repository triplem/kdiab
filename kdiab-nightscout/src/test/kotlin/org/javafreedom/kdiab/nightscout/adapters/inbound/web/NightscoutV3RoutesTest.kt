package org.javafreedom.kdiab.nightscout.adapters.inbound.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.nightscout.application.service.NightscoutService
import org.javafreedom.kdiab.nightscout.application.service.NightscoutV3Service
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Entry
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Food
import org.javafreedom.kdiab.nightscout.domain.model.Ns3HistoryResult
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Profile
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Settings
import org.javafreedom.kdiab.nightscout.module
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

private fun Application.installMockDi(
    nightscoutService: NightscoutService,
    v3Service: NightscoutV3Service,
) {
    install(DI) { }
    dependencies {
        provide<NightscoutService> { nightscoutService }
        provide<NightscoutV3Service> { v3Service }
    }
}

class NightscoutV3RoutesTest {

    private companion object {
        const val JWT_SECRET = "test-secret-for-unit-tests-only"
        const val AUDIENCE = "nightscout"
        const val ISSUER = "http://localhost:8081/realms/kdiab"
        const val USER_ID = "11111111-1111-1111-1111-111111111111"

        fun token(userId: String = USER_ID, glucoseUnit: String = "mg/dL"): String = JWT.create()
            .withSubject(userId)
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("roles", listOf("PATIENT"))
            .withClaim("glucose_unit", glucoseUnit)
            .sign(Algorithm.HMAC256(JWT_SECRET))

        val userToken get() = token()

        val sampleEntry = Ns3Entry(
            identifier = "entry-1",
            date = 1704067200000L,
            dateString = "2024-01-01T00:00:00Z",
            type = "sgv",
            sgv = 120.0,
        )
    }

    private fun v3RouteTest(
        block: suspend ApplicationTestBuilder.(NightscoutV3Service) -> Unit,
    ) {
        val mockV3Service = mockk<NightscoutV3Service>()
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "jwt.domain" to ISSUER,
                    "jwt.audience" to AUDIENCE,
                    "jwt.realm" to "kdiab",
                    "jwt.test" to "true",
                    "jwt.secret" to JWT_SECRET,
                    "upstream.measuresUrl" to "http://localhost:8080",
                    "upstream.treatmentsUrl" to "http://localhost:8083",
                    "api3.maxLimit" to "1000",
                )
            }
            application {
                installMockDi(mockk(relaxed = true), mockV3Service)
                module()
            }
            block(mockV3Service)
        }
    }

    @Test
    fun `GET api v3 entries returns 200 with list`() = v3RouteTest { svc ->
        coEvery { svc.searchEntries(any(), any(), any(), any(), any()) } returns listOf(sampleEntry)

        val response = client.get("/api/v3/entries") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "entry-1")
    }

    @Test
    fun `GET api v3 entries returns 401 without token`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/entries")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET api v3 entries slash id returns 200 when found`() = v3RouteTest { svc ->
        coEvery { svc.getEntry(any(), any(), any(), "entry-1", any()) } returns sampleEntry

        val response = client.get("/api/v3/entries/entry-1") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "entry-1")
    }

    @Test
    fun `GET api v3 entries slash id returns 404 when not found`() = v3RouteTest { svc ->
        coEvery { svc.getEntry(any(), any(), any(), "missing", any()) } returns null

        val response = client.get("/api/v3/entries/missing") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET api v3 entries slash id returns 401 without token`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/entries/entry-1")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST api v3 entries returns 201 with location header`() = v3RouteTest { svc ->
        coEvery { svc.createEntry(any(), any(), any(), any(), any()) } returns sampleEntry

        val response = client.post("/api/v3/entries") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(sampleEntry))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertContains(response.headers[HttpHeaders.Location] ?: "", "entry-1")
    }

    @Test
    fun `POST api v3 entries returns 401 without token`() = v3RouteTest { _ ->
        val response = client.post("/api/v3/entries") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(sampleEntry))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `DELETE api v3 entries slash id returns 200`() = v3RouteTest { svc ->
        coJustRun { svc.deleteEntry(any(), any(), any(), "entry-1", any()) }

        val response = client.delete("/api/v3/entries/entry-1") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `DELETE api v3 entries slash id returns 401 without token`() = v3RouteTest { _ ->
        val response = client.delete("/api/v3/entries/entry-1")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ---- /api/v3/food routes ----

    private val sampleFood = Ns3Food(
        identifier = "food-1",
        name = "Apple",
        carbs = 21.0,
        portionSize = 150.0,
    )

    @Test
    fun `GET api v3 food returns 200 with list`() = v3RouteTest { svc ->
        coEvery { svc.searchFood(any(), any(), any(), any()) } returns listOf(sampleFood)

        val response = client.get("/api/v3/food") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "food-1")
    }

    @Test
    fun `GET api v3 food returns 401 without token`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/food")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET api v3 food slash id returns 200 when found`() = v3RouteTest { svc ->
        coEvery { svc.getFood(any(), any(), any(), "food-1") } returns sampleFood

        val response = client.get("/api/v3/food/food-1") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "food-1")
    }

    @Test
    fun `GET api v3 food slash id returns 404 when not found`() = v3RouteTest { svc ->
        coEvery { svc.getFood(any(), any(), any(), "missing") } returns null

        val response = client.get("/api/v3/food/missing") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST api v3 food returns 201 with location header`() = v3RouteTest { svc ->
        coEvery { svc.createFood(any(), any(), any(), any()) } returns sampleFood

        val response = client.post("/api/v3/food") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(sampleFood))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertContains(response.headers[HttpHeaders.Location] ?: "", "food-1")
    }

    @Test
    fun `POST api v3 food returns 401 without token`() = v3RouteTest { _ ->
        val response = client.post("/api/v3/food") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(sampleFood))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `DELETE api v3 food slash id returns 200`() = v3RouteTest { svc ->
        coJustRun { svc.deleteFood(any(), any(), any(), "food-1", any()) }

        val response = client.delete("/api/v3/food/food-1") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `DELETE api v3 food slash id returns 401 without token`() = v3RouteTest { _ ->
        val response = client.delete("/api/v3/food/food-1")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- /api/v3/settings ---

    @Test
    fun `GET api v3 settings returns 200 with units from JWT`() = v3RouteTest { svc ->
        val expectedSettings = Ns3Settings(identifier = USER_ID, units = "mg/dL", timeZone = "UTC")
        coEvery { svc.getSettings(any(), any(), any(), any()) } returns expectedSettings

        val response = client.get("/api/v3/settings") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "mg/dL")
    }

    @Test
    fun `GET api v3 settings returns 401 without token`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/settings")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `PUT api v3 settings returns 200 when units match JWT`() = v3RouteTest { svc ->
        val settings = Ns3Settings(identifier = USER_ID, units = "mg/dL", timeZone = "UTC")
        coEvery { svc.getSettings(any(), any(), any(), any()) } returns settings

        val response = client.put("/api/v3/settings") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(settings))
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `PUT api v3 settings returns 422 when units differ from JWT`() = v3RouteTest { _ ->
        val body = Ns3Settings(identifier = USER_ID, units = "mmol/L", timeZone = "UTC")

        val response = client.put("/api/v3/settings") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(body))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `PATCH api v3 settings returns 200 when units match JWT`() = v3RouteTest { svc ->
        val settings = Ns3Settings(identifier = USER_ID, units = "mg/dL", timeZone = "UTC")
        coEvery { svc.getSettings(any(), any(), any(), any()) } returns settings

        val response = client.patch("/api/v3/settings") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(settings))
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `PATCH api v3 settings returns 422 when units differ from JWT`() = v3RouteTest { _ ->
        val body = Ns3Settings(identifier = USER_ID, units = "mmol/L", timeZone = "UTC")

        val response = client.patch("/api/v3/settings") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(body))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    // ─── Profile routes ────────────────────────────────────────────────────────

    private val sampleProfile = Ns3Profile(
        identifier = "profile-1",
        defaultProfile = "My Profile",
        startDate = "2024-01-01T00:00:00Z",
        units = "mg/dl",
        dia = 4.0,
        basalSegments = emptyList(),
        carbratio = emptyList(),
        sens = emptyList(),
    )

    @Test
    fun `GET api v3 profile returns 200 with list`() = v3RouteTest { svc ->
        coEvery { svc.searchProfiles(any(), any(), any(), any()) } returns listOf(sampleProfile)

        val response = client.get("/api/v3/profile") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "profile-1")
    }

    @Test
    fun `GET api v3 profile returns 401 without token`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/profile")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET api v3 profile slash id returns 200 when found`() = v3RouteTest { svc ->
        coEvery { svc.getProfile(any(), any(), any(), "profile-1") } returns sampleProfile

        val response = client.get("/api/v3/profile/profile-1") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "profile-1")
    }

    @Test
    fun `GET api v3 profile slash id returns 404 when not found`() = v3RouteTest { svc ->
        coEvery { svc.getProfile(any(), any(), any(), "missing") } returns null

        val response = client.get("/api/v3/profile/missing") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST api v3 profile returns 201 with location header`() = v3RouteTest { svc ->
        coEvery { svc.createProfile(any(), any(), any(), any()) } returns sampleProfile

        val response = client.post("/api/v3/profile") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(sampleProfile))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertContains(response.headers[HttpHeaders.Location] ?: "", "profile-1")
    }

    @Test
    fun `POST api v3 profile returns 401 without token`() = v3RouteTest { _ ->
        val response = client.post("/api/v3/profile") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(sampleProfile))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `DELETE api v3 profile slash id returns 200 when permanent is false`() = v3RouteTest { svc ->
        coJustRun { svc.deleteProfile(any(), any(), any(), "profile-1", any()) }

        val response = client.delete("/api/v3/profile/profile-1") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `DELETE api v3 profile slash id returns 400 when permanent is true`() = v3RouteTest { _ ->
        val response = client.delete("/api/v3/profile/profile-1?permanent=true") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `DELETE api v3 profile slash id returns 401 without token`() = v3RouteTest { _ ->
        val response = client.delete("/api/v3/profile/profile-1")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ─── Meta endpoints ────────────────────────────────────────────────────────

    @Test
    fun `GET api v3 version returns 200 with version info`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/version") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "srvDate")
        assertContains(response.bodyAsText(), "\"status\":200")
    }

    @Test
    fun `GET api v3 version returns 200 without token`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/version")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET api v3 status returns 200 with authenticated true`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/status") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "isAuthenticated")
        assertContains(response.bodyAsText(), "true")
    }

    @Test
    fun `GET api v3 status returns 200 without token`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/status")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET api v3 lastModified returns 200 with all collections`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/lastModified") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "entries")
        assertContains(body, "treatments")
        assertContains(body, "foods")
        assertContains(body, "profile")
        assertContains(body, "devicestatus")
        assertContains(body, "srvDate")
    }

    @Test
    fun `GET api v3 lastModified returns 401 without token`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/lastModified")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET api v3 entries history returns 200 with entries`() = v3RouteTest { svc ->
        coEvery { svc.historyEntries(any(), any(), any(), null, any()) } returns
            Ns3HistoryResult(
                status = 200,
                result = listOf(sampleEntry),
                lastModified = 1704067200000L,
            )

        val response = client.get("/api/v3/entries/history") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "entry-1")
    }

    @Test
    fun `GET api v3 entries history returns 401 without token`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/entries/history")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET api v3 entries history with lastModified returns 200`() = v3RouteTest { svc ->
        val lastModified = 1704067200000L
        coEvery { svc.historyEntries(any(), any(), any(), lastModified, any()) } returns
            Ns3HistoryResult(
                status = 200,
                result = listOf(sampleEntry),
                lastModified = lastModified,
            )

        val response = client.get("/api/v3/entries/history/$lastModified") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "entry-1")
    }

    @Test
    fun `GET api v3 treatments history stub returns 200 with empty list`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/treatments/history") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "result")
    }

    @Test
    fun `GET api v3 foods history stub returns 200 with empty list`() = v3RouteTest { _ ->
        val response = client.get("/api/v3/foods/history") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "result")
    }
}

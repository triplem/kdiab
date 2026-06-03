package org.javafreedom.kdiab.nightscout

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NightscoutV3RoutesTest : BaseNightscoutTest() {

    private val userResponseJson =
        """{"userId":"u1","email":"test@test.com","displayName":"Test","roles":["PATIENT"]}"""

    private val measureResponseJson = """
        {
          "id":"measure-1","userId":"$PATIENT_ID",
          "measuredAt":"2026-01-01T10:00:00Z","createdAt":"2026-01-01T10:00:00Z",
          "type":"CGM","source":"NIGHTSCOUT","data":{"sgv":120},"status":"ACTIVE"
        }
    """.trimIndent()

    private val treatmentResponseJson = """
        {
          "id":"treatment-1","userId":"$PATIENT_ID",
          "treatedAt":"2026-01-01T10:00:00Z","createdAt":"2026-01-01T10:00:00Z",
          "type":"BOLUS","data":{"insulin":5.0},"status":"ACTIVE"
        }
    """.trimIndent()

    private val foodResponseJson = """
        {
          "id":"food-1","userId":"$PATIENT_ID","name":"Test Food",
          "portionGrams":100,"carbsPer100g":30,"carbsForPortion":30,
          "status":"ACTIVE","createdAt":"2026-01-01T10:00:00Z","updatedAt":"2026-01-01T10:00:00Z"
        }
    """.trimIndent()

    /**
     * Builds a mock engine that routes by host and HTTP method.
     * All GET list calls return empty paged responses; POST/PUT returns a valid entity response.
     * GET by id on profiles returns 404 so the service returns null (tested as not-found).
     */
    private fun buildV3MockEngine(): MockEngine = MockEngine { request ->
        val host = request.url.host
        val method = request.method.value
        val path = request.url.encodedPath
        when (host) {
            "mock-users" -> jsonResponse(this, userResponseJson)
            "mock-measures" -> when (method) {
                "POST", "PUT" -> jsonResponse(this, measureResponseJson)
                else -> emptyJsonArrayResponse(this)
            }
            "mock-treatments" -> when (method) {
                "POST", "PUT" -> jsonResponse(this, treatmentResponseJson)
                else -> emptyJsonArrayResponse(this)
            }
            "mock-carbs" -> when (method) {
                "POST", "PUT" -> jsonResponse(this, foodResponseJson)
                else -> emptyJsonArrayResponse(this)
            }
            "mock-profiles" -> when {
                // GET /users/{userId}/profiles/{id} — return 404 so service yields null
                method == "GET" && !path.endsWith("profiles") ->
                    jsonResponse(this, "{}", HttpStatusCode.NotFound)
                else -> emptyJsonArrayResponse(this)
            }
            else -> emptyJsonArrayResponse(this)
        }
    }

    // ── Public endpoints ──────────────────────────────────────────────────────

    @Test
    fun `GET api v3 version - returns 200 without authentication`() = runNightscoutApp {
        val response = client.get("/api/v3/version")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"status\":200")
    }

    @Test
    fun `GET api v3 status - returns 200 without authentication`() = runNightscoutApp {
        val response = client.get("/api/v3/status")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"status\":200")
    }

    // ── lastModified ──────────────────────────────────────────────────────────

    @Test
    fun `GET api v3 lastModified - returns 401 without authentication`() = runNightscoutApp {
        val response = client.get("/api/v3/lastModified")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET api v3 lastModified - returns 200 with valid JWT`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/lastModified") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "entries")
        }

    // ── Entries ───────────────────────────────────────────────────────────────

    @Test
    fun `GET api v3 entries - returns 401 without authentication`() = runNightscoutApp {
        val response = client.get("/api/v3/entries")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET api v3 entries - returns 200 with empty list`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/entries") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "\"result\":[]")
        }

    @Test
    fun `GET api v3 entries by id - returns 404 when not found`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/entries/nonexistent-id") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertContains(response.bodyAsText(), "\"status\":404")
        }

    @Test
    fun `POST api v3 entries - returns 201 with location header`() =
        runNightscoutApp(buildV3MockEngine()) {
            val entryJson = """
                {
                  "identifier":"entry-local-1","date":1735725600000,
                  "dateString":"2026-01-01T10:00:00Z","type":"sgv","sgv":120.0
                }
            """.trimIndent()
            val response = client.post("/api/v3/entries") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
                contentType(ContentType.Application.Json)
                setBody(entryJson)
            }
            assertEquals(HttpStatusCode.Created, response.status)
            assertNotNull(response.headers[HttpHeaders.Location])
        }

    @Test
    fun `DELETE api v3 entries by id - returns 200`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.delete("/api/v3/entries/measure-1") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "\"status\":200")
        }

    @Test
    fun `GET api v3 entries history - returns 200`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/entries/history") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `GET api v3 entries history with lastModified - returns 200`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/entries/history/1735725600000") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    // ── Treatments ────────────────────────────────────────────────────────────

    @Test
    fun `GET api v3 treatments - returns 401 without authentication`() = runNightscoutApp {
        val response = client.get("/api/v3/treatments")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET api v3 treatments - returns 200 with empty list`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/treatments") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "\"result\":[]")
        }

    @Test
    fun `GET api v3 treatments by id - returns 404 when not found`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/treatments/nonexistent-id") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `POST api v3 treatments - returns 201 with location header`() =
        runNightscoutApp(buildV3MockEngine()) {
            val treatmentJson = """
                {
                  "identifier":"t-local-1","date":1735725600000,
                  "dateString":"2026-01-01T10:00:00Z","eventType":"Bolus","insulin":5.0
                }
            """.trimIndent()
            val response = client.post("/api/v3/treatments") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
                contentType(ContentType.Application.Json)
                setBody(treatmentJson)
            }
            assertEquals(HttpStatusCode.Created, response.status)
            assertNotNull(response.headers[HttpHeaders.Location])
        }

    @Test
    fun `DELETE api v3 treatments by id - returns 200`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.delete("/api/v3/treatments/treatment-1") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `GET api v3 treatments history - returns 200`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/treatments/history") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    // ── Food ──────────────────────────────────────────────────────────────────

    @Test
    fun `GET api v3 food - returns 401 without authentication`() = runNightscoutApp {
        val response = client.get("/api/v3/food")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET api v3 food - returns 200 with empty list`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/food") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "\"result\":[]")
        }

    @Test
    fun `GET api v3 food by id - returns 404 when not found`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/food/nonexistent-id") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `POST api v3 food - returns 201 with location header`() =
        runNightscoutApp(buildV3MockEngine()) {
            val foodJson = """
                {"identifier":"f-local-1","name":"Banana","carbs":23.0,"portionSize":100.0}
            """.trimIndent()
            val response = client.post("/api/v3/food") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
                contentType(ContentType.Application.Json)
                setBody(foodJson)
            }
            assertEquals(HttpStatusCode.Created, response.status)
            assertNotNull(response.headers[HttpHeaders.Location])
        }

    @Test
    fun `DELETE api v3 food by id - returns 200`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.delete("/api/v3/food/food-1") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    // ── Settings ──────────────────────────────────────────────────────────────

    @Test
    fun `GET api v3 settings - returns 200 with glucose unit`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/settings") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "\"status\":200")
        }

    @Test
    fun `PUT api v3 settings - returns 200 when units match upstream`() =
        runNightscoutApp(buildV3MockEngine()) {
            // mock returns default "mg/dL"; sending matching units should succeed
            val settingsJson = """{"identifier":"u1","units":"mg/dL","timeZone":"UTC"}"""
            val response = client.put("/api/v3/settings") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
                contentType(ContentType.Application.Json)
                setBody(settingsJson)
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `PUT api v3 settings - returns 422 when units mismatch upstream`() =
        runNightscoutApp(buildV3MockEngine()) {
            // mock returns "mg/dL"; sending "mmol/L" must be rejected
            val settingsJson = """{"identifier":"u1","units":"mmol/L","timeZone":"UTC"}"""
            val response = client.put("/api/v3/settings") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
                contentType(ContentType.Application.Json)
                setBody(settingsJson)
            }
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        }

    // ── Profile ───────────────────────────────────────────────────────────────

    @Test
    fun `GET api v3 profile - returns 200 with empty list`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/profile") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "\"result\":[]")
        }

    @Test
    fun `GET api v3 profile by id - returns 404 when not found`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/profile/nonexistent-id") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `DELETE api v3 profile by id - returns 200`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.delete("/api/v3/profile/profile-1") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `DELETE api v3 profile by id with permanent - returns 400`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.delete("/api/v3/profile/profile-1?permanent=true") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `GET api v3 profile history - returns 200`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/profile/history") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    // ── DeviceStatus ──────────────────────────────────────────────────────────

    @Test
    fun `GET api v3 devicestatus - returns 200 with empty list`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/devicestatus") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "\"result\":[]")
        }

    @Test
    fun `GET api v3 devicestatus by id - returns 404 when not found`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/devicestatus/nonexistent-id") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `POST api v3 devicestatus - returns 201 with location header`() =
        runNightscoutApp(buildV3MockEngine()) {
            val dsJson = """
                {"identifier":"ds-local-1","date":1735725600000,"dateString":"2026-01-01T10:00:00Z"}
            """.trimIndent()
            val response = client.post("/api/v3/devicestatus") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
                contentType(ContentType.Application.Json)
                setBody(dsJson)
            }
            assertEquals(HttpStatusCode.Created, response.status)
            assertNotNull(response.headers[HttpHeaders.Location])
        }

    @Test
    fun `DELETE api v3 devicestatus by id - returns 200`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.delete("/api/v3/devicestatus/ds-1") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `GET api v3 devicestatus history - returns 200`() =
        runNightscoutApp(buildV3MockEngine()) {
            val response = client.get("/api/v3/devicestatus/history") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
}

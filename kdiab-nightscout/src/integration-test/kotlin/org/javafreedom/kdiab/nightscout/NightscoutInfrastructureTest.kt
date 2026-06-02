package org.javafreedom.kdiab.nightscout

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Infrastructure smoke tests for kdiab-nightscout.
 *
 * Verifies that the Ktor test application starts correctly with mock upstream clients
 * and that core public and authenticated endpoints are reachable.
 * These tests exercise the full HTTP routing, authentication, and DI wiring pipeline
 * without requiring any real upstream service.
 */
class NightscoutInfrastructureTest : BaseNightscoutTest() {

    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Test
    fun `GET status json - returns 200 with ok status without authentication`() =
        runNightscoutApp {
            val response = client.get("/api/v1/status.json")

            assertEquals(HttpStatusCode.OK, response.status)
            val json = lenientJson.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("ok", json["status"]?.jsonPrimitive?.content)
            assertNotNull(json["serverTime"])
        }

    @Test
    fun `GET api v3 version - returns 200 without authentication`() =
        runNightscoutApp {
            val response = client.get("/api/v3/version")

            assertEquals(HttpStatusCode.OK, response.status)
            val json = lenientJson.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(200, json["status"]?.jsonPrimitive?.content?.toInt())
        }

    @Test
    fun `GET api v3 status - returns 200 without authentication`() =
        runNightscoutApp {
            val response = client.get("/api/v3/status")

            assertEquals(HttpStatusCode.OK, response.status)
            val json = lenientJson.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(200, json["status"]?.jsonPrimitive?.content?.toInt())
        }

    @Test
    fun `GET entries json - returns 401 without authentication`() =
        runNightscoutApp {
            val response = client.get("/api/v1/entries.json")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `GET entries json - returns 200 with valid JWT`() =
        runNightscoutApp {
            val token = patientToken()

            val response = client.get("/api/v1/entries.json") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `GET healthz - returns 200 without authentication`() =
        runNightscoutApp {
            val response = client.get("/healthz")

            assertEquals(HttpStatusCode.OK, response.status)
        }
}

package org.javafreedom.kdiab.nightscout

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.nightscout.domain.model.NightscoutEntry
import org.javafreedom.kdiab.nightscout.domain.model.NightscoutTreatment
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class NightscoutV1RoutesTest : BaseNightscoutTest() {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `GET treatments json - returns 401 without authentication`() = runNightscoutApp {
        val response = client.get("/api/v1/treatments.json")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET treatments json - returns 200 with valid JWT`() = runNightscoutApp {
        val response = client.get("/api/v1/treatments.json") {
            header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET treatments json - returns empty list when no treatments`() = runNightscoutApp {
        val response = client.get("/api/v1/treatments.json") {
            header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `GET entries json - returns empty list when measures have no CGM data`() = runNightscoutApp {
        val response = client.get("/api/v1/entries.json") {
            header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `GET entries json - accepts count query parameter`() = runNightscoutApp {
        val response = client.get("/api/v1/entries.json?count=10") {
            header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET entries json - accepts from and to query parameters`() = runNightscoutApp {
        val response = client.get("/api/v1/entries.json?from=2026-01-01T00:00:00Z&to=2026-01-02T00:00:00Z") {
            header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET treatments json - accepts count from and to query parameters`() = runNightscoutApp {
        val response = client.get("/api/v1/treatments.json?count=5&from=2026-01-01T00:00:00Z") {
            header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST entries json - returns 401 without authentication`() = runNightscoutApp {
        val response = client.post("/api/v1/entries.json") {
            contentType(ContentType.Application.Json)
            setBody("[]")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST entries json - returns 200 with empty list`() = runNightscoutApp {
        val response = client.post("/api/v1/entries.json") {
            header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            contentType(ContentType.Application.Json)
            setBody("[]")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `POST entries json - returns submitted entries unchanged when type is not convertible`() = runNightscoutApp {
        val now = System.currentTimeMillis()
        val entry = NightscoutEntry(
            type = "cal",
            date = now,
            dateString = "2026-01-01T10:00:00Z",
            id = "cal-entry-1",
            mills = now,
        )
        val body = json.encodeToString(listOf(entry))

        val response = client.post("/api/v1/entries.json") {
            header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "cal-entry-1")
    }

    @Test
    fun `POST treatments json - returns 401 without authentication`() = runNightscoutApp {
        val response = client.post("/api/v1/treatments.json") {
            contentType(ContentType.Application.Json)
            setBody("[]")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST treatments json - returns 200 with empty list`() = runNightscoutApp {
        val response = client.post("/api/v1/treatments.json") {
            header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
            contentType(ContentType.Application.Json)
            setBody("[]")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `POST treatments json - returns submitted treatments unchanged when event type is not convertible`() =
        runNightscoutApp {
            val now = System.currentTimeMillis()
            val treatment = NightscoutTreatment(
                id = "treatment-1",
                eventType = "Unknown Custom Event",
                createdAt = "2026-01-01T10:00:00Z",
                mills = now,
            )
            val body = json.encodeToString(listOf(treatment))

            val response = client.post("/api/v1/treatments.json") {
                header(HttpHeaders.Authorization, "Bearer ${patientToken()}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "treatment-1")
        }
}

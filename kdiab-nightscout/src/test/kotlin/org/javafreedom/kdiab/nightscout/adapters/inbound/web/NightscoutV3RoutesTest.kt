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
}

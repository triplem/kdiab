@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.calc.adapters.inbound.web

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.javafreedom.kdiab.calc.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.calc.application.service.DoseCalculationService
import org.javafreedom.kdiab.calc.domain.model.ActiveProfile
import org.javafreedom.kdiab.calc.domain.model.GlucoseTarget
import org.javafreedom.kdiab.calc.domain.model.IcrRatio
import org.javafreedom.kdiab.calc.domain.model.IsfRatio
import org.javafreedom.kdiab.calc.module
import java.util.Date

// Top-level helper: installs mock DI bindings on the Application before module() runs.
// Extracted to avoid implicit-receiver ambiguity when called inside testApplication lambdas.
private fun Application.installMockDi(service: DoseCalculationService) {
    install(DI) { }
    dependencies {
        provide<DoseCalculationService> { service }
    }
}

/**
 * Integration tests for [CalcRoutes] using Ktor's embedded test engine.
 *
 * [ProfilesClient] is mocked so these tests exercise the full HTTP routing,
 * authentication, deserialization, service call, and response mapping pipeline
 * without requiring a real upstream profiles service.
 */
class CalcRoutesIntegrationTest {

    private val issuer = "http://localhost:8081/realms/kdiab"
    private val audience = "calc"
    private val jwtSecret = "test-secret-for-integration-tests"

    private val userId = Uuid.parse("11111111-1111-1111-1111-111111111111")

    private val profilesClient = mockk<ProfilesClient>()
    private val service = DoseCalculationService(profilesClient)

    private val testProfile = ActiveProfile(
        id = "profile-abc",
        timeZone = null,
        isf = listOf(IsfRatio(startTime = "00:00", value = 50.0)),
        icr = listOf(IcrRatio(startTime = "00:00", value = 15.0)),
        targets = listOf(GlucoseTarget(startTime = "00:00", low = 100.0, high = 120.0)),
    )

    private fun calcTestConfig() = MapApplicationConfig(
        "jwt.domain" to issuer,
        "jwt.audience" to audience,
        "jwt.realm" to "kdiab",
        "jwt.test" to "true",
        "jwt.secret" to jwtSecret,
    )

    private fun token(userIdStr: String, roles: List<String>): String =
        SignedJWT(JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.Builder()
            .subject(userIdStr)
            .audience(audience)
            .issuer(issuer)
            .claim("roles", roles)
            .expirationTime(Date(System.currentTimeMillis() + 60_000))
            .build()).apply { sign(MACSigner(jwtSecret.toByteArray())) }.serialize()

    @Test
    fun `POST calculateDose - returns 200 with computed dose for authenticated patient`() =
        testApplication {
            environment { config = calcTestConfig() }
            application { installMockDi(service); module() }

            coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns testProfile

            val client = createClient { install(ContentNegotiation) { json() } }

            val body = """
                {
                    "currentBg": 200.0,
                    "glucoseUnit": "mg/dL",
                    "trend": "FLAT",
                    "carbsGrams": 45.0,
                    "activeIob": 0.0
                }
            """.trimIndent()

            val response = client.post("/api/v1/users/$userId/calc/dose") {
                header(HttpHeaders.Authorization, "Bearer ${token(userId.toString(), listOf("PATIENT"))}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            // correction = (200 - 110) / 50 = 1.8, carbDose = 45 / 15 = 3.0, total = 4.8
            assertEquals(4.8, json["totalRecommended"]!!.jsonPrimitive.content.toDouble())
            assertEquals(1.8, json["correctionDose"]!!.jsonPrimitive.content.toDouble())
            assertEquals(3.0, json["carbDose"]!!.jsonPrimitive.content.toDouble())
            assertEquals("profile-abc", json["profileId"]!!.jsonPrimitive.content)
        }

    @Test
    fun `POST calculateDose - returns 400 for invalid trend value`() =
        testApplication {
            environment { config = calcTestConfig() }
            application { installMockDi(service); module() }

            val client = createClient { install(ContentNegotiation) { json() } }

            val body = """
                {
                    "currentBg": 150.0,
                    "glucoseUnit": "mg/dL",
                    "trend": "NOT_A_VALID_TREND",
                    "carbsGrams": 0.0
                }
            """.trimIndent()

            val response = client.post("/api/v1/users/$userId/calc/dose") {
                header(HttpHeaders.Authorization, "Bearer ${token(userId.toString(), listOf("PATIENT"))}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            // Invalid trend enum value → 400 Bad Request from StatusPages
            assertTrue(
                response.status == HttpStatusCode.BadRequest ||
                    response.status == HttpStatusCode.UnprocessableEntity,
                "Expected 400 or 422, got ${response.status}",
            )
        }

    @Test
    fun `POST calculateDose - returns 400 with clinical message when activeIob omitted`() =
        testApplication {
            environment { config = calcTestConfig() }
            application { installMockDi(service); module() }

            val client = createClient { install(ContentNegotiation) { json() } }

            // Valid body EXCEPT activeIob is missing — #1563 requires a hard 400, never a silent 0.
            val body = """{"currentBg":200.0,"glucoseUnit":"mg/dL","trend":"FLAT","carbsGrams":0.0}"""

            val response = client.post("/api/v1/users/$userId/calc/dose") {
                header(HttpHeaders.Authorization, "Bearer ${token(userId.toString(), listOf("PATIENT"))}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(
                response.bodyAsText().contains("activeIob is required"),
                "Expected the clinical 'activeIob is required' message, got: ${response.bodyAsText()}",
            )
        }

    @Test
    fun `POST calculateDose - returns 400 when activeIob is negative`() =
        testApplication {
            environment { config = calcTestConfig() }
            application { installMockDi(service); module() }

            val client = createClient { install(ContentNegotiation) { json() } }

            val body = """{"currentBg":200.0,"glucoseUnit":"mg/dL","trend":"FLAT","activeIob":-1.0}"""

            val response = client.post("/api/v1/users/$userId/calc/dose") {
                header(HttpHeaders.Authorization, "Bearer ${token(userId.toString(), listOf("PATIENT"))}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(
                response.bodyAsText().contains("zero or positive"),
                "Expected the 'zero or positive' message, got: ${response.bodyAsText()}",
            )
        }

    @Test
    fun `POST calculateDose - returns 401 when no auth token`() =
        testApplication {
            environment { config = calcTestConfig() }
            application { installMockDi(service); module() }

            val client = createClient { install(ContentNegotiation) { json() } }

            val response = client.post("/api/v1/users/$userId/calc/dose") {
                contentType(ContentType.Application.Json)
                setBody("""{"currentBg":150.0,"glucoseUnit":"mg/dL","trend":"FLAT","carbsGrams":0.0}""")
            }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `POST calculateDose - returns 403 when patient accesses another user`() =
        testApplication {
            environment { config = calcTestConfig() }
            application { installMockDi(service); module() }

            val anotherUserId = Uuid.parse("22222222-2222-2222-2222-222222222222")
            val client = createClient { install(ContentNegotiation) { json() } }

            val response = client.post("/api/v1/users/$anotherUserId/calc/dose") {
                header(HttpHeaders.Authorization, "Bearer ${token(userId.toString(), listOf("PATIENT"))}")
                contentType(ContentType.Application.Json)
                setBody("""{"currentBg":150.0,"glucoseUnit":"mg/dL","trend":"FLAT","carbsGrams":0.0}""")
            }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `GET healthz - returns 200 without auth`() =
        testApplication {
            environment { config = calcTestConfig() }
            application { installMockDi(service); module() }

            val response = client.get("/healthz")

            assertEquals(HttpStatusCode.OK, response.status)
        }
}

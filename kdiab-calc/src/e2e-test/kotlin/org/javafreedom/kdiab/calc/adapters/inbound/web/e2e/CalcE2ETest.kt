@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.calc.adapters.inbound.web.e2e

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import java.util.Date
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.javafreedom.kdiab.calc.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.IcrSegment
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.IsfSegment
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.Profile
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.TargetSegment
import org.javafreedom.kdiab.calc.application.service.DoseCalculationService
import org.javafreedom.kdiab.calc.module

private const val ISSUER = "http://localhost:8081/realms/kdiab"
private const val AUDIENCE = "calc"
private const val JWT_SECRET = "test-secret-e2e-calc-only"

private val patientId = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

private fun generateJwt(
    userId: String,
    roles: List<String>,
    allowedPatients: List<String> = emptyList(),
): String = JWT.create()
    .withSubject(userId)
    .withAudience(AUDIENCE)
    .withIssuer(ISSUER)
    .withClaim("roles", roles)
    .apply { if (allowedPatients.isNotEmpty()) withClaim("allowed_patients", allowedPatients) }
    .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
    .sign(Algorithm.HMAC256(JWT_SECRET))

private fun calcConfig() = MapApplicationConfig(
    "jwt.domain" to ISSUER,
    "jwt.audience" to AUDIENCE,
    "jwt.realm" to "kdiab",
    "jwt.test" to "true",
    "jwt.secret" to JWT_SECRET,
)

private val activeProfile = Profile(
    id = "e2e-profile-001",
    userId = patientId.toString(),
    name = "E2E Test Profile",
    insulinType = "rapid",
    durationOfAction = 240,
    status = Profile.Status.ACTIVE,
    isf = listOf(IsfSegment(startTime = "00:00", `value` = 40.0)),
    icr = listOf(IcrSegment(startTime = "00:00", `value` = 10.0)),
    targets = listOf(TargetSegment(startTime = "00:00", low = 90.0, high = 110.0)),
)

/**
 * E2E tests for the kdiab-calc dose calculation API.
 *
 * Uses an embedded Ktor server. The upstream ProfilesClient is mocked to avoid
 * requiring a live kdiab-profiles instance. These tests exercise the golden path
 * and critical failure scenarios end-to-end through the full HTTP stack.
 */
class CalcE2ETest : BehaviorSpec({

    val profilesClient = mockk<ProfilesClient>()
    val service = DoseCalculationService(profilesClient)

    given("a running kdiab-calc service") {

        `when`("checking the health endpoint") {
            then("GET /healthz returns 200 without authentication") {
                testApplication {
                    environment { config = calcConfig() }
                    application { module(doseCalculationService = service) }
                    val response = client.get("/healthz")
                    response.status shouldBe HttpStatusCode.OK
                }
            }
        }

        `when`("a patient submits a valid dose calculation request") {
            then("POST /calculate returns 200 with totalRecommended > 0 for high BG with carbs") {
                testApplication {
                    environment { config = calcConfig() }
                    application { module(doseCalculationService = service) }

                    coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns activeProfile

                    val client = createClient { install(ContentNegotiation) { json() } }
                    val token = generateJwt(patientId.toString(), listOf("PATIENT"))

                    val response = client.post("/api/v1/users/$patientId/calc/dose") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        // BG = 180 mg/dL (above target 100), carbs = 30g
                        // correction = (180 - 100) / 40 = 2.0, carbDose = 30 / 10 = 3.0, total = 5.0
                        setBody(
                            """{"currentBg":180.0,"glucoseUnit":"mg/dL","trend":"FLAT","carbsGrams":30.0}"""
                        )
                    }

                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["totalRecommended"]!!.jsonPrimitive.content.toDouble() shouldBeGreaterThan 0.0
                    body["profileId"]!!.jsonPrimitive.content shouldBe "e2e-profile-001"
                    body["warnings"]!!.jsonArray.size shouldBe 0
                }
            }

            then("response includes a hypoglycemia warning when BG is below 70 mg/dL") {
                testApplication {
                    environment { config = calcConfig() }
                    application { module(doseCalculationService = service) }

                    coEvery { profilesClient.getActiveProfile(any(), any(), any()) } returns activeProfile

                    val client = createClient { install(ContentNegotiation) { json() } }
                    val token = generateJwt(patientId.toString(), listOf("PATIENT"))

                    val response = client.post("/api/v1/users/$patientId/calc/dose") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """{"currentBg":60.0,"glucoseUnit":"mg/dL","trend":"DOUBLE_DOWN","carbsGrams":0.0}"""
                        )
                    }

                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    val warnings = body["warnings"]!!.jsonArray
                    (warnings.size > 0) shouldBe true
                }
            }
        }

        `when`("a patient accesses another user's calculation endpoint") {
            then("they receive 403 Forbidden") {
                testApplication {
                    environment { config = calcConfig() }
                    application { module(doseCalculationService = service) }

                    val client = createClient { install(ContentNegotiation) { json() } }
                    val otherUserId = Uuid.parse("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                    val token = generateJwt(patientId.toString(), listOf("PATIENT"))

                    val response = client.post("/api/v1/users/$otherUserId/calc/dose") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(
                            """{"currentBg":150.0,"glucoseUnit":"mg/dL","trend":"FLAT","carbsGrams":0.0}"""
                        )
                    }

                    response.status shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        `when`("a request is sent without an Authorization header") {
            then("they receive 401 Unauthorized") {
                testApplication {
                    environment { config = calcConfig() }
                    application { module(doseCalculationService = service) }

                    val response = client.post("/api/v1/users/$patientId/calc/dose") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """{"currentBg":150.0,"glucoseUnit":"mg/dL","trend":"FLAT","carbsGrams":0.0}"""
                        )
                    }

                    response.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }
    }
})

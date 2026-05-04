package org.javafreedom.kdiab.analyze.adapters.inbound.web.e2e

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.analyze.adapters.inbound.web.AgpResponseDto
import org.javafreedom.kdiab.analyze.adapters.inbound.web.Hba1cResponseDto
import org.javafreedom.kdiab.analyze.adapters.inbound.web.ProfilesResponseDto
import org.javafreedom.kdiab.analyze.adapters.inbound.web.TimelineResponse
import org.javafreedom.kdiab.analyze.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.analyze.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.analyze.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.analyze.application.service.AnalyticsService
import org.javafreedom.kdiab.analyze.application.service.ProfilesService
import org.javafreedom.kdiab.analyze.application.service.TimelineService
import org.javafreedom.kdiab.analyze.module

class BffE2ETest : BehaviorSpec({

    val jwtSecret = "test-secret-for-analyze-tests"
    val audience = "analyze"
    val issuer = "http://localhost:8085/realms/kdiab-analyze"
    val sarahId = "11111111-1111-1111-1111-111111111111"
    val from = "2024-01-01T00:00:00Z"
    val to = "2024-01-31T23:59:59Z"

    fun token(userId: String, roles: List<String>): String = JWT.create()
        .withSubject(userId)
        .withAudience(audience)
        .withIssuer(issuer)
        .withClaim("roles", roles)
        .withClaim("glucose_unit", "mg/dL")
        .sign(Algorithm.HMAC256(jwtSecret))

    val sarahToken = token(sarahId, listOf("PATIENT"))

    // Realistic mock data for sarah
    val cgmMeasureJson = """{"id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","userId":"$sarahId","measuredAt":"2024-01-15T10:00:00Z","type":"CGM","source":"NIGHTSCOUT","data":{"value":120.0,"unit":"mg/dL","trend":"Flat"},"status":"ACTIVE"}"""
    val bolusTreatmentJson = """{"id":"cccccccc-cccc-cccc-cccc-cccccccccccc","userId":"$sarahId","treatedAt":"2024-01-15T12:00:00Z","type":"BOLUS","data":{"units":3.5}}"""
    val activeProfileJson = """{"id":"dddddddd-dddd-dddd-dddd-dddddddddddd","userId":"$sarahId","status":"ACTIVE","name":"Sarah Basal Profile","createdAt":"2024-01-01T00:00:00Z","validFrom":"2024-01-01T00:00:00Z"}"""

    val measuresPagedJson = """{"items":[$cgmMeasureJson],"page":0,"size":200,"totalCount":1}"""
    val treatmentsJson = """[$bolusTreatmentJson]"""
    val profilesJson = """[$activeProfileJson]"""

    val lenientJson = Json { ignoreUnknownKeys = true }

    fun buildMockEngine(): MockEngine = MockEngine { req ->
        val path = req.url.encodedPath
        when {
            path.contains("measures") -> respond(
                measuresPagedJson,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
            path.contains("treatments") -> respond(
                treatmentsJson,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
            path.contains("profiles") -> respond(
                profilesJson,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
            else -> respond(
                """{"items":[],"page":0,"size":200,"totalCount":0}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
    }

    fun buildServices(): Triple<TimelineService, AnalyticsService, ProfilesService> {
        val httpClient = HttpClient(buildMockEngine()) {
            install(ContentNegotiation) { json() }
        }
        val measuresClient = MeasuresClient(httpClient, "http://mock-measures")
        val treatmentsClient = TreatmentsClient(httpClient, "http://mock-treatments")
        val profilesClient = ProfilesClient(httpClient, "http://mock-profiles")
        return Triple(
            TimelineService(measuresClient, treatmentsClient),
            AnalyticsService(measuresClient),
            ProfilesService(profilesClient),
        )
    }

    given("a patient with CGM, BOLUS, and profile data") {
        val (timelineService, analyticsService, profilesService) = buildServices()

        `when`("they GET /timeline") {
            then("the response contains the CGM measure and BOLUS treatment") {
                testApplication {
                    environment {
                        config = MapApplicationConfig(
                            "jwt.domain" to issuer,
                            "jwt.audience" to audience,
                            "jwt.realm" to "kdiab-analyze",
                            "jwt.test" to "true",
                            "jwt.secret" to jwtSecret,
                        )
                    }
                    application { module(timelineService, analyticsService, profilesService) }
                    val resp = client.get("/api/v1/users/$sarahId/timeline?from=$from&to=$to") {
                        bearerAuth(sarahToken)
                    }
                    resp.status shouldBe HttpStatusCode.OK
                    val timeline = lenientJson.decodeFromString<TimelineResponse>(resp.bodyAsText())
                    timeline.measures shouldHaveSize 1
                    timeline.measures[0].id shouldBe "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
                    timeline.measures[0].type shouldBe "CGM"
                    timeline.treatments shouldHaveSize 1
                    timeline.treatments[0].id shouldBe "cccccccc-cccc-cccc-cccc-cccccccccccc"
                    timeline.treatments[0].type shouldBe "BOLUS"
                }
            }
        }

        `when`("they GET /analytics/hba1c") {
            then("hba1c is computed and readingCount matches") {
                testApplication {
                    environment {
                        config = MapApplicationConfig(
                            "jwt.domain" to issuer,
                            "jwt.audience" to audience,
                            "jwt.realm" to "kdiab-analyze",
                            "jwt.test" to "true",
                            "jwt.secret" to jwtSecret,
                        )
                    }
                    application { module(timelineService, analyticsService, profilesService) }
                    val resp = client.get("/api/v1/users/$sarahId/analytics/hba1c?from=$from&to=$to") {
                        bearerAuth(sarahToken)
                    }
                    resp.status shouldBe HttpStatusCode.OK
                    val result = lenientJson.decodeFromString<Hba1cResponseDto>(resp.bodyAsText())
                    result.readingCount shouldBe 1
                    result.hba1c.shouldNotBeNull()
                    result.hba1c!! shouldBe (5.8118 plusOrMinus 0.01)
                }
            }
        }

        `when`("they GET /profiles/active") {
            then("the ACTIVE profile is listed with validFrom") {
                testApplication {
                    environment {
                        config = MapApplicationConfig(
                            "jwt.domain" to issuer,
                            "jwt.audience" to audience,
                            "jwt.realm" to "kdiab-analyze",
                            "jwt.test" to "true",
                            "jwt.secret" to jwtSecret,
                        )
                    }
                    application { module(timelineService, analyticsService, profilesService) }
                    val resp = client.get("/api/v1/users/$sarahId/profiles/active?from=$from&to=$to") {
                        bearerAuth(sarahToken)
                    }
                    resp.status shouldBe HttpStatusCode.OK
                    val result = lenientJson.decodeFromString<ProfilesResponseDto>(resp.bodyAsText())
                    result.profiles shouldHaveSize 1
                    result.profiles[0].status shouldBe "ACTIVE"
                    result.profiles[0].name shouldBe "Sarah Basal Profile"
                    result.profiles[0].validFrom.shouldNotBeNull()
                }
            }
        }

        `when`("an unauthenticated request is made") {
            then("401 is returned") {
                testApplication {
                    environment {
                        config = MapApplicationConfig(
                            "jwt.domain" to issuer,
                            "jwt.audience" to audience,
                            "jwt.realm" to "kdiab-analyze",
                            "jwt.test" to "true",
                            "jwt.secret" to jwtSecret,
                        )
                    }
                    application { module(timelineService, analyticsService, profilesService) }
                    val resp = client.get("/api/v1/users/$sarahId/timeline?from=$from&to=$to")
                    resp.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }
    }
}) {
    @Suppress("unused")
    companion object {
        // E2E tests check full user journeys through the BFF with MockEngine-backed HTTP clients.
        // Each `given` block builds fresh services with a MockEngine that returns realistic data.
    }
}

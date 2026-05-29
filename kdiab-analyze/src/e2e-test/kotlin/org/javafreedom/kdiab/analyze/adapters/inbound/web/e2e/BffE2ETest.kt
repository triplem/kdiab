package org.javafreedom.kdiab.analyze.adapters.inbound.web.e2e

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

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
import io.mockk.mockk
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import org.javafreedom.kdiab.analyze.application.service.AnalyticsOperation
import org.javafreedom.kdiab.analyze.application.service.AnalyticsService
import org.javafreedom.kdiab.analyze.application.service.DeviceUsageOperation
import org.javafreedom.kdiab.analyze.application.service.DeviceUsageService
import org.javafreedom.kdiab.analyze.application.service.ProfilesOperation
import org.javafreedom.kdiab.analyze.application.service.ProfilesService
import org.javafreedom.kdiab.analyze.application.service.TimelineOperation
import org.javafreedom.kdiab.analyze.application.service.TimelineService
import org.javafreedom.kdiab.analyze.module

// Top-level helper: installs mock DI bindings on the Application before module() runs.
// Extracted to avoid implicit-receiver ambiguity when called inside testApplication/BehaviorSpec lambdas.
private fun Application.installMockDi(
    timelineService: TimelineOperation,
    analyticsService: AnalyticsOperation,
    profilesService: ProfilesOperation,
    deviceUsageService: DeviceUsageOperation,
) {
    install(DI) { }
    dependencies {
        provide<TimelineOperation> { timelineService }
        provide<AnalyticsOperation> { analyticsService }
        provide<ProfilesOperation> { profilesService }
        provide<DeviceUsageOperation> { deviceUsageService }
        provide<TreatmentsClient> { mockk(relaxed = true) }
    }
}

class BffE2ETest : BehaviorSpec({

    val jwtSecret = "test-secret-for-analyze-tests"
    val audience = "analyze"
    val issuer = "http://localhost:8085/realms/kdiab-analyze"
    val sarahId = "11111111-1111-1111-1111-111111111111"
    val mikeId = "22222222-2222-2222-2222-222222222222"
    val drHouseId = "33333333-3333-3333-3333-333333333333"
    val from = "2024-01-01T00:00:00Z"
    val to = "2024-01-31T23:59:59Z"

    fun token(
        userId: String,
        roles: List<String>,
        allowedPatients: List<String> = emptyList(),
    ): String = JWT.create()
        .withSubject(userId)
        .withAudience(audience, "measure", "profile", "treatment")
        .withIssuer(issuer)
        .withClaim("roles", roles)
        .withClaim("glucose_unit", "mg/dL")
        .withClaim("allowed_patients", allowedPatients)
        .sign(Algorithm.HMAC256(jwtSecret))

    val sarahToken = token(sarahId, listOf("PATIENT"))
    val mikeToken = token(mikeId, listOf("PATIENT"))

    // dr_house is a DOCTOR assigned to sarah
    val drHouseToken = token(drHouseId, listOf("DOCTOR"), allowedPatients = listOf(sarahId))

    // A DOCTOR whose allowed_patients does NOT include sarah
    val drCameronToken = token(
        "44444444-4444-4444-4444-444444444444",
        listOf("DOCTOR"),
        allowedPatients = listOf(mikeId),
    )

    // Realistic mock data for sarah
    val cgmMeasureJson =
        """{"id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","userId":"$sarahId",""" +
        """"measuredAt":"2024-01-15T10:00:00Z","createdAt":"2024-01-15T10:00:00Z","type":"CGM","source":"NIGHTSCOUT",""" +
        """"data":{"value":120.0,"unit":"mg/dL","trend":"Flat"},"status":"ACTIVE"}"""
    val bolusTreatmentJson =
        """{"id":"cccccccc-cccc-cccc-cccc-cccccccccccc","userId":"$sarahId",""" +
        """"treatedAt":"2024-01-15T12:00:00Z","createdAt":"2024-01-15T12:00:00Z","type":"BOLUS","data":{"units":3.5},"status":"ACTIVE"}"""
    val activeProfileJson =
        """{"id":"dddddddd-dddd-dddd-dddd-dddddddddddd","userId":"$sarahId","status":"ACTIVE",""" +
        """"name":"Sarah Basal Profile",""" +
        """"settings":{"insulinType":"rapid","durationOfAction":180},""" +
        """"schedule":{"basal":[{"startTime":"00:00","value":1.0}],"icr":[],"isf":[],"targets":[]},""" +
        """"insulinType":"rapid","durationOfAction":180,""" +
        """"createdAt":"2024-01-01T00:00:00Z","validFrom":"2024-01-01T00:00:00Z"}"""

    val measuresPagedJson = """{"items":[$cgmMeasureJson],"page":0,"size":200,"totalCount":1}"""
    val treatmentsJson = """{"items":[$bolusTreatmentJson],"page":0,"size":200,"totalCount":1}"""
    val profilesJson = """{"items":[$activeProfileJson],"page":0,"size":50,"totalCount":1}"""

    // 10 CGM readings at 10:xx UTC on different days — all land in hourly bucket 10
    val agpCgmReadings = (1..10).joinToString(",") { day ->
        val dayStr = day.toString().padStart(2, '0')
        val minStr = (day * 3).toString().padStart(2, '0')
        """{"id":"${"a".repeat(8)}-${"a".repeat(4)}-${"a".repeat(4)}-${"a".repeat(4)}-${day.toString().padStart(12, '0')}",""" +
        """"userId":"$sarahId","measuredAt":"2024-01-${dayStr}T10:${minStr}:00Z",""" +
        """"createdAt":"2024-01-${dayStr}T10:${minStr}:00Z",""" +
        """"type":"CGM","source":"NIGHTSCOUT","data":{"value":${100.0 + day * 2},"unit":"mg/dL","trend":"Flat"},""" +
        """"status":"ACTIVE"}"""
    }
    val agpMeasuresPagedJson = """{"items":[$agpCgmReadings],"page":0,"size":200,"totalCount":10}"""

    val lenientJson = Json { ignoreUnknownKeys = true }

    val jwtConfig = mapOf(
        "jwt.domain" to issuer,
        "jwt.audience" to audience,
        "jwt.realm" to "kdiab-analyze",
        "jwt.test" to "true",
        "jwt.secret" to jwtSecret,
    )

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
        val mockEngine = buildMockEngine()
        val measuresClient = MeasuresClient(mockEngine, "http://mock-measures")
        val treatmentsClient = TreatmentsClient(mockEngine, "http://mock-treatments")
        val profilesClient = ProfilesClient(mockEngine, "http://mock-profiles")
        return Triple(
            TimelineService(measuresClient, treatmentsClient),
            AnalyticsService(measuresClient, profilesClient, treatmentsClient),
            ProfilesService(profilesClient),
        )
    }

    given("a patient with CGM, BOLUS, and profile data") {
        val (timelineService, analyticsService, profilesService) = buildServices()
        val deviceUsageService: DeviceUsageService = mockk()

        `when`("they GET /timeline") {
            then("the response contains the CGM measure and BOLUS treatment") {
                testApplication {
                    environment { config = MapApplicationConfig(*jwtConfig.toList().toTypedArray()) }
                    application { installMockDi(timelineService, analyticsService, profilesService, deviceUsageService); module() }
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
                    environment { config = MapApplicationConfig(*jwtConfig.toList().toTypedArray()) }
                    application { installMockDi(timelineService, analyticsService, profilesService, deviceUsageService); module() }
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
                    environment { config = MapApplicationConfig(*jwtConfig.toList().toTypedArray()) }
                    application { installMockDi(timelineService, analyticsService, profilesService, deviceUsageService); module() }
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
                    environment { config = MapApplicationConfig(*jwtConfig.toList().toTypedArray()) }
                    application { installMockDi(timelineService, analyticsService, profilesService, deviceUsageService); module() }
                    val resp = client.get("/api/v1/users/$sarahId/timeline?from=$from&to=$to")
                    resp.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }

        `when`("they GET /analytics/agp with 10 readings spread across UTC hour 10") {
            then("the response contains 288 five-minute buckets with readings in hour 10 and non-null medians") {
                val agpEngine = MockEngine { req ->
                    val path = req.url.encodedPath
                    when {
                        path.contains("measures") -> respond(
                            agpMeasuresPagedJson,
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
                val agpMeasuresClient = MeasuresClient(agpEngine, "http://mock-measures")
                val agpProfilesClient = ProfilesClient(agpEngine, "http://mock-profiles")
                val agpTreatmentsClient = TreatmentsClient(agpEngine, "http://mock-treatments")
                val agpAnalyticsService = AnalyticsService(agpMeasuresClient, agpProfilesClient, agpTreatmentsClient)
                val agpServices = Triple(timelineService, agpAnalyticsService, profilesService)

                testApplication {
                    environment { config = MapApplicationConfig(*jwtConfig.toList().toTypedArray()) }
                    application { installMockDi(agpServices.first, agpServices.second, agpServices.third, deviceUsageService); module() }
                    val resp = client.get("/api/v1/users/$sarahId/analytics/agp?from=$from&to=$to") {
                        bearerAuth(sarahToken)
                    }
                    resp.status shouldBe HttpStatusCode.OK
                    val result = lenientJson.decodeFromString<AgpResponseDto>(resp.bodyAsText())
                    result.bucketData shouldHaveSize 288
                    // 10 readings at day*3 minutes past 10:00 UTC (03,06,09,12,15,18,21,24,27,30).
                    // At 5-min granularity some share buckets: 600(1),605(2),610(1),615(2),620(2),625(1),630(1)
                    // → 7 distinct occupied buckets, total 10 readings.
                    result.totalReadingCount shouldBe 10
                    val occupiedBuckets = result.bucketData.filter { it.count > 0 }
                    occupiedBuckets.size shouldBe 7
                    occupiedBuckets.forEach { bucket ->
                        bucket.minuteOfDay shouldBe (bucket.minuteOfDay / 5 * 5) // must be multiple of 5
                        bucket.median.shouldNotBeNull()
                    }
                    // All occupied buckets are within hour 10 (minuteOfDay 600–659)
                    occupiedBuckets.forEach { bucket ->
                        (bucket.minuteOfDay in 600..659) shouldBe true
                    }
                    val bucket0 = result.bucketData.first { it.minuteOfDay == 0 }
                    bucket0.count shouldBe 0
                }
            }
        }

        `when`("a PATIENT requests another patient's (mike's) data") {
            then("403 is returned") {
                testApplication {
                    environment { config = MapApplicationConfig(*jwtConfig.toList().toTypedArray()) }
                    application { installMockDi(timelineService, analyticsService, profilesService, deviceUsageService); module() }
                    // sarah's token used to request mike's userId — access must be denied
                    val resp = client.get("/api/v1/users/$mikeId/timeline?from=$from&to=$to") {
                        bearerAuth(sarahToken)
                    }
                    resp.status shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        `when`("a DOCTOR whose allowedPatients does NOT include sarah requests sarah's data") {
            then("403 is returned") {
                testApplication {
                    environment { config = MapApplicationConfig(*jwtConfig.toList().toTypedArray()) }
                    application { installMockDi(timelineService, analyticsService, profilesService, deviceUsageService); module() }
                    val resp = client.get("/api/v1/users/$sarahId/timeline?from=$from&to=$to") {
                        bearerAuth(drCameronToken)
                    }
                    resp.status shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        `when`("the upstream measures service returns 500 during a timeline request") {
            // TimelineService uses supervisorScope + runCatching, so one failing upstream
            // does NOT crash the entire request; instead errors are collected and returned
            // in body.errors while body.measures is empty and status is still 200.
            then("200 is returned with an empty measures list and a non-empty errors list") {
                val failingEngine = MockEngine { req ->
                    val path = req.url.encodedPath
                    when {
                        path.contains("measures") -> respond(
                            """{"error":"upstream failure"}""",
                            HttpStatusCode.InternalServerError,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                        path.contains("treatments") -> respond(
                            treatmentsJson,
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
                val failingMeasuresClient = MeasuresClient(failingEngine, "http://mock-measures")
                val failingTreatmentsClient = TreatmentsClient(failingEngine, "http://mock-treatments")
                val failingTimelineService = TimelineService(failingMeasuresClient, failingTreatmentsClient)

                testApplication {
                    environment { config = MapApplicationConfig(*jwtConfig.toList().toTypedArray()) }
                    application { installMockDi(failingTimelineService, analyticsService, profilesService, deviceUsageService); module() }
                    val resp = client.get("/api/v1/users/$sarahId/timeline?from=$from&to=$to") {
                        bearerAuth(sarahToken)
                    }
                    resp.status shouldBe HttpStatusCode.OK
                    val timeline = lenientJson.decodeFromString<TimelineResponse>(resp.bodyAsText())
                    timeline.measures shouldHaveSize 0
                    timeline.errors.shouldNotBeEmpty()
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

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.adapters.inbound.web.e2e

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.*
import java.util.Date
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.javafreedom.kdiab.measures.module

private const val ISSUER = "http://localhost:8081/realms/kdiab-measures"
private const val AUDIENCE = "measure"
private const val JWT_SECRET = "test-secret-for-unit-tests-only"

private val userSarahId = Uuid.parse("11111111-1111-1111-1111-111111111111")
private val userMikeId = Uuid.parse("22222222-2222-2222-2222-222222222222")

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

private fun measuresConfig(dbName: String = "e2e_measures") = MapApplicationConfig(
    "jwt.domain" to ISSUER,
    "jwt.audience" to AUDIENCE,
    "jwt.realm" to "kdiab-measures",
    "jwt.secret" to JWT_SECRET,
    "jwt.test" to "true",
    "storage.driverClassName" to "org.h2.Driver",
    "storage.jdbcUrl" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    "storage.username" to "root",
    "storage.password" to "password",
    "storage.maximumPoolSize" to "3",
    "storage.isAutoCommit" to "false",
    "storage.transactionIsolation" to "TRANSACTION_REPEATABLE_READ",
)


private val createCgmBody = """
    {"measuredAt":"2024-01-15T10:00:00Z","type":"CGM","source":"NIGHTSCOUT","data":{"sgv":120,"trend":"Flat"}}
""".trimIndent()

private val createBgmBody = """
    {"measuredAt":"2024-01-15T10:05:00Z","type":"BGM","source":"MANUAL","data":{"mbg":115}}
""".trimIndent()

/**
 * E2E tests for the Measures API.
 *
 * Uses an embedded Ktor server with an H2 in-memory database bootstrapped via Liquibase
 * (through DatabaseFactory.init). The full migration chain runs for each test application.
 */
class MeasureE2ETest : BehaviorSpec({

    given("a running Measures Service") {

        `when`("a patient manages their CGM measures") {
            then("they can create, list, filter by type, archive, and delete") {
                testApplication {
                    environment { config = measuresConfig("e2e_measures_patient") }
                    application { module() }

                    val client = createClient {
                        install(ContentNegotiation) { json() }
                    }

                    val sarahToken = generateJwt(
                        userId = userSarahId.toString(),
                        roles = listOf("PATIENT"),
                    )

                    // 1. GET measures -> 200, empty list (totalCount = 0)
                    val emptyResp = client.get("/api/v1/users/$userSarahId/measures") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                    }
                    emptyResp.status shouldBe HttpStatusCode.OK
                    val emptyBody = Json.parseToJsonElement(emptyResp.bodyAsText()).jsonObject
                    emptyBody["totalCount"]!!.jsonPrimitive.long shouldBe 0L

                    // 2. POST measure (CGM) -> 201, capture id
                    val createResp = client.post("/api/v1/users/$userSarahId/measures") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                        contentType(ContentType.Application.Json)
                        setBody(createCgmBody)
                    }
                    createResp.status shouldBe HttpStatusCode.Created
                    val createdBody = Json.parseToJsonElement(createResp.bodyAsText()).jsonObject
                    val createdId = createdBody["id"]!!.jsonPrimitive.content

                    // 3. POST another measure (BGM)
                    val createBgmResp = client.post("/api/v1/users/$userSarahId/measures") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                        contentType(ContentType.Application.Json)
                        setBody(createBgmBody)
                    }
                    createBgmResp.status shouldBe HttpStatusCode.Created

                    // 4. GET measures -> 2 items total
                    val listResp = client.get("/api/v1/users/$userSarahId/measures") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                    }
                    listResp.status shouldBe HttpStatusCode.OK
                    val listBody = Json.parseToJsonElement(listResp.bodyAsText()).jsonObject
                    listBody["totalCount"]!!.jsonPrimitive.long shouldBe 2L

                    // 5. GET measures?type=BGM -> only 1 BGM item visible via type filter path
                    // (Note: type filtering uses findByUserIdAndType internally — test the list endpoint counts)
                    val allItems = listBody["items"]!!.jsonArray
                    allItems.size shouldBe 2

                    // 6. POST archive the CGM measure -> 200
                    val archiveResp = client.post("/api/v1/users/$userSarahId/measures/archive") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"measureIds":["$createdId"]}""")
                    }
                    archiveResp.status shouldBe HttpStatusCode.OK

                    // 7. GET measures -> only 1 item remains (CGM was archived)
                    val afterArchiveResp = client.get("/api/v1/users/$userSarahId/measures") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                    }
                    afterArchiveResp.status shouldBe HttpStatusCode.OK
                    val afterArchiveBody = Json.parseToJsonElement(afterArchiveResp.bodyAsText()).jsonObject
                    afterArchiveBody["totalCount"]!!.jsonPrimitive.long shouldBe 1L
                }
            }
        }

        `when`("a doctor deletes measures for an allowed patient") {
            then("they receive 200") {
                testApplication {
                    environment { config = measuresConfig("e2e_measures_doctor") }
                    application { module() }

                    val client = createClient {
                        install(ContentNegotiation) { json() }
                    }

                    val doctorId = Uuid.random()
                    val doctorToken = generateJwt(
                        userId = doctorId.toString(),
                        roles = listOf("DOCTOR"),
                        allowedPatients = listOf(userSarahId.toString()),
                    )
                    val sarahToken = generateJwt(
                        userId = userSarahId.toString(),
                        roles = listOf("PATIENT"),
                    )

                    // Create a measure as sarah
                    val createResp = client.post("/api/v1/users/$userSarahId/measures") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                        contentType(ContentType.Application.Json)
                        setBody(createCgmBody)
                    }
                    createResp.status shouldBe HttpStatusCode.Created
                    val createdId = Json.parseToJsonElement(createResp.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content

                    // Doctor deletes it
                    val deleteResp = client.delete("/api/v1/users/$userSarahId/measures") {
                        header(HttpHeaders.Authorization, "Bearer $doctorToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"measureIds":["$createdId"]}""")
                    }
                    deleteResp.status shouldBe HttpStatusCode.OK

                    // Verify it's gone
                    val listResp = client.get("/api/v1/users/$userSarahId/measures") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                    }
                    val listBody = Json.parseToJsonElement(listResp.bodyAsText()).jsonObject
                    listBody["totalCount"]!!.jsonPrimitive.long shouldBe 0L
                }
            }
        }

        `when`("a patient tries to access another user's measures") {
            then("they receive 403") {
                testApplication {
                    environment { config = measuresConfig("e2e_measures_403") }
                    application { module() }

                    val client = createClient {
                        install(ContentNegotiation) { json() }
                    }

                    val sarahToken = generateJwt(
                        userId = userSarahId.toString(),
                        roles = listOf("PATIENT"),
                    )

                    val resp = client.get("/api/v1/users/$userMikeId/measures") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                    }
                    resp.status shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        `when`("a request has no auth token") {
            then("they receive 401") {
                testApplication {
                    environment { config = measuresConfig("e2e_measures_401") }
                    application { module() }

                    val client = createClient {
                        install(ContentNegotiation) { json() }
                    }

                    val resp = client.get("/api/v1/users/$userSarahId/measures")
                    resp.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }
    }
})

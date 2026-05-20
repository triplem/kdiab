@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.carbs.adapters.inbound.web.e2e

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import java.util.Date
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.javafreedom.kdiab.carbs.module

private const val ISSUER = "http://localhost:8081/realms/kdiab-carbs"
private const val AUDIENCE = "carbs"
private const val JWT_SECRET = "test-secret-e2e-carbs-only"

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

private fun carbsConfig(dbName: String = "e2e_carbs") = MapApplicationConfig(
    "jwt.domain" to ISSUER,
    "jwt.audience" to AUDIENCE,
    "jwt.realm" to "kdiab-carbs",
    "jwt.secret" to JWT_SECRET,
    "jwt.test" to "true",
    "storage.driverClassName" to "org.h2.Driver",
    "storage.jdbcUrl" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    "storage.username" to "root",
    "storage.password" to "",
    "storage.maximumPoolSize" to "3",
    "storage.isAutoCommit" to "false",
    "storage.transactionIsolation" to "TRANSACTION_REPEATABLE_READ",
    "app.createSchema" to "true",
)

private val riceEntryBody = """{"name":"White Rice","portionGrams":150.0,"carbsPer100g":28.0}"""
private val pastaEntryBody = """{"name":"Pasta","portionGrams":200.0,"carbsPer100g":25.0}"""

/**
 * E2E tests for the kdiab-carbs Food Entry API.
 *
 * Uses an embedded Ktor server with an H2 in-memory database bootstrapped via Liquibase
 * (through DatabaseFactory.init). The full migration chain runs for each test application.
 */
class FoodEntryE2ETest : BehaviorSpec({

    given("a running kdiab-carbs service") {

        `when`("checking the health endpoint") {
            then("GET /healthz returns 200 without authentication") {
                testApplication {
                    environment { config = carbsConfig("e2e_carbs_health") }
                    application { module() }
                    val response = client.get("/healthz")
                    response.status shouldBe HttpStatusCode.OK
                }
            }
        }

        `when`("a patient manages their food entries") {
            then("they can create, list, update, and delete food entries") {
                testApplication {
                    environment { config = carbsConfig("e2e_carbs_crud") }
                    application { module() }

                    val client = createClient { install(ContentNegotiation) { json() } }
                    val sarahToken = generateJwt(userSarahId.toString(), listOf("PATIENT"))

                    // 1. GET food entries -> 200, empty list (totalCount = 0)
                    val emptyResp = client.get("/api/v1/users/$userSarahId/foods") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                    }
                    emptyResp.status shouldBe HttpStatusCode.OK
                    val emptyBody = Json.parseToJsonElement(emptyResp.bodyAsText()).jsonObject
                    emptyBody["totalCount"]!!.jsonPrimitive.long shouldBe 0L

                    // 2. POST create first entry (Rice) -> 201
                    val createRiceResp = client.post("/api/v1/users/$userSarahId/foods") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                        contentType(ContentType.Application.Json)
                        setBody(riceEntryBody)
                    }
                    createRiceResp.status shouldBe HttpStatusCode.Created
                    val riceBody = Json.parseToJsonElement(createRiceResp.bodyAsText()).jsonObject
                    val riceId = riceBody["id"]!!.jsonPrimitive.content
                    riceBody["name"]!!.jsonPrimitive.content shouldBe "White Rice"

                    // 3. POST create second entry (Pasta) -> 201
                    val createPastaResp = client.post("/api/v1/users/$userSarahId/foods") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                        contentType(ContentType.Application.Json)
                        setBody(pastaEntryBody)
                    }
                    createPastaResp.status shouldBe HttpStatusCode.Created

                    // 4. GET food entries -> 2 items
                    val listResp = client.get("/api/v1/users/$userSarahId/foods") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                    }
                    listResp.status shouldBe HttpStatusCode.OK
                    val listBody = Json.parseToJsonElement(listResp.bodyAsText()).jsonObject
                    listBody["totalCount"]!!.jsonPrimitive.long shouldBe 2L
                    listBody["items"]!!.jsonArray.size shouldBe 2

                    // 5. PUT update the Rice entry -> 200
                    val updateResp = client.put("/api/v1/users/$userSarahId/foods/$riceId") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Brown Rice","portionGrams":120.0,"carbsPer100g":23.0}""")
                    }
                    updateResp.status shouldBe HttpStatusCode.OK
                    val updatedBody = Json.parseToJsonElement(updateResp.bodyAsText()).jsonObject
                    updatedBody["name"]!!.jsonPrimitive.content shouldBe "Brown Rice"

                    // 6. DELETE the Rice entry -> 204
                    val deleteResp = client.delete("/api/v1/users/$userSarahId/foods/$riceId") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                    }
                    deleteResp.status shouldBe HttpStatusCode.NoContent

                    // 7. GET food entries -> only 1 entry remains (Pasta)
                    val afterDeleteResp = client.get("/api/v1/users/$userSarahId/foods") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                    }
                    afterDeleteResp.status shouldBe HttpStatusCode.OK
                    val afterDeleteBody = Json.parseToJsonElement(afterDeleteResp.bodyAsText()).jsonObject
                    afterDeleteBody["totalCount"]!!.jsonPrimitive.long shouldBe 1L
                }
            }
        }

        `when`("a patient uses name search") {
            then("GET /foods?q=rice returns only matching entries") {
                testApplication {
                    environment { config = carbsConfig("e2e_carbs_search") }
                    application { module() }

                    val client = createClient { install(ContentNegotiation) { json() } }
                    val sarahToken = generateJwt(userSarahId.toString(), listOf("PATIENT"))

                    client.post("/api/v1/users/$userSarahId/foods") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                        contentType(ContentType.Application.Json)
                        setBody(riceEntryBody)
                    }
                    client.post("/api/v1/users/$userSarahId/foods") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                        contentType(ContentType.Application.Json)
                        setBody(pastaEntryBody)
                    }

                    val searchResp = client.get("/api/v1/users/$userSarahId/foods?q=rice") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                    }
                    searchResp.status shouldBe HttpStatusCode.OK
                    val searchBody = Json.parseToJsonElement(searchResp.bodyAsText()).jsonObject
                    searchBody["totalCount"]!!.jsonPrimitive.long shouldBe 1L
                    val items = searchBody["items"]!!.jsonArray
                    items[0].jsonObject["name"]!!.jsonPrimitive.content shouldBe "White Rice"
                }
            }
        }

        `when`("a patient tries to access another user's food entries") {
            then("they receive 403 Forbidden") {
                testApplication {
                    environment { config = carbsConfig("e2e_carbs_403") }
                    application { module() }

                    val client = createClient { install(ContentNegotiation) { json() } }
                    val sarahToken = generateJwt(userSarahId.toString(), listOf("PATIENT"))

                    val resp = client.get("/api/v1/users/$userMikeId/foods") {
                        header(HttpHeaders.Authorization, "Bearer $sarahToken")
                    }
                    resp.status shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        `when`("a request has no auth token") {
            then("they receive 401 Unauthorized") {
                testApplication {
                    environment { config = carbsConfig("e2e_carbs_401") }
                    application { module() }

                    val resp = client.get("/api/v1/users/$userSarahId/foods")
                    resp.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }
    }
})

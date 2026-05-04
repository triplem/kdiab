@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.adapters.inbound.web.e2e

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.treatments.module

class TreatmentE2ETest : BehaviorSpec({
    val ISSUER = "http://localhost:8081/realms/kdiab-treatments"
    val AUDIENCE = "treatment"
    val JWT_SECRET = "test-secret-for-e2e-tests"
    val SARAH_ID = "11111111-1111-1111-1111-111111111111"
    val MIKE_ID = "22222222-2222-2222-2222-222222222222"

    fun token(userId: String, roles: List<String>, allowedPatients: List<String> = emptyList()): String =
        JWT.create()
            .withSubject(userId)
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("roles", roles)
            .apply { if (allowedPatients.isNotEmpty()) withClaim("allowed_patients", allowedPatients) }
            .sign(Algorithm.HMAC256(JWT_SECRET))

    val e2eConfig = MapApplicationConfig(
        "jwt.domain" to ISSUER,
        "jwt.audience" to AUDIENCE,
        "jwt.realm" to "kdiab-treatments",
        "jwt.secret" to JWT_SECRET,
        "jwt.test" to "true",
        "storage.driverClassName" to "org.h2.Driver",
        "storage.jdbcUrl" to "jdbc:h2:mem:e2e_treatments;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "storage.username" to "root",
        "storage.password" to "password",
        "storage.maximumPoolSize" to "3",
        "storage.isAutoCommit" to "false",
        "storage.transactionIsolation" to "TRANSACTION_REPEATABLE_READ"
    )

    given("a running Treatments Service") {
        `when`("a patient manages their BOLUS treatments") {
            then("they can create, list with type filter, and delete a treatment") {
                testApplication {
                    environment { config = e2eConfig }
                    application { module() }
                    val client = createClient { install(ContentNegotiation) { json() } }

                    val sarahToken = token(SARAH_ID, listOf("PATIENT"))

                    // 1. List — empty initially
                    val list0 = client.get("/api/v1/users/$SARAH_ID/treatments") {
                        bearerAuth(sarahToken)
                    }
                    list0.status shouldBe HttpStatusCode.OK
                    val empty = Json.decodeFromString<List<JsonObject>>(list0.bodyAsText())
                    empty.size shouldBe 0

                    // 2. Create BOLUS → 201
                    val create = client.post("/api/v1/users/$SARAH_ID/treatments") {
                        bearerAuth(sarahToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"treatedAt":"2024-01-15T10:00:00Z","type":"BOLUS","data":{"insulin":2.5}}""")
                    }
                    create.status shouldBe HttpStatusCode.Created
                    val created = Json.decodeFromString<JsonObject>(create.bodyAsText())
                    val treatmentId = created["id"].toString().trim('"')

                    // 3. List → 1 treatment
                    val list1 = client.get("/api/v1/users/$SARAH_ID/treatments") {
                        bearerAuth(sarahToken)
                    }
                    list1.status shouldBe HttpStatusCode.OK
                    val oneItem = Json.decodeFromString<List<JsonObject>>(list1.bodyAsText())
                    oneItem.size shouldBe 1

                    // 4. Type filter CARBS → empty
                    val listCarbs = client.get("/api/v1/users/$SARAH_ID/treatments?type=CARBS") {
                        bearerAuth(sarahToken)
                    }
                    listCarbs.status shouldBe HttpStatusCode.OK
                    Json.decodeFromString<List<JsonObject>>(listCarbs.bodyAsText()).size shouldBe 0

                    // 5. Delete → 200
                    val delete = client.post("/api/v1/users/$SARAH_ID/treatments/delete") {
                        bearerAuth(sarahToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"treatmentIds":["$treatmentId"]}""")
                    }
                    delete.status shouldBe HttpStatusCode.OK

                    // 6. List → empty again
                    val list2 = client.get("/api/v1/users/$SARAH_ID/treatments") {
                        bearerAuth(sarahToken)
                    }
                    list2.status shouldBe HttpStatusCode.OK
                    Json.decodeFromString<List<JsonObject>>(list2.bodyAsText()).size shouldBe 0
                }
            }
        }

        `when`("a patient tries to access another user's treatments") {
            then("they receive 403") {
                testApplication {
                    environment { config = e2eConfig }
                    application { module() }
                    val client = createClient { install(ContentNegotiation) { json() } }
                    val sarahToken = token(SARAH_ID, listOf("PATIENT"))
                    val resp = client.get("/api/v1/users/$MIKE_ID/treatments") {
                        bearerAuth(sarahToken)
                    }
                    resp.status shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        `when`("a request has no auth token") {
            then("they receive 401") {
                testApplication {
                    environment { config = e2eConfig }
                    application { module() }
                    val resp = client.get("/api/v1/users/$SARAH_ID/treatments")
                    resp.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }
    }
})

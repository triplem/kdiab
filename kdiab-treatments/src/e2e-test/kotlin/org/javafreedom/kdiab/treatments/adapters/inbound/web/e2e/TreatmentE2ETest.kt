@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.adapters.inbound.web.e2e

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.javafreedom.kdiab.treatments.module

class TreatmentE2ETest : BehaviorSpec({
    val ISSUER = "http://localhost:8081/realms/kdiab-treatments"
    val AUDIENCE = "treatment"
    val JWT_SECRET = "test-secret-for-e2e-tests-hs256-pad0"
    val SARAH_ID = "11111111-1111-1111-1111-111111111111"
    val MIKE_ID = "22222222-2222-2222-2222-222222222222"

    fun token(userId: String, roles: List<String>, allowedPatients: List<String> = emptyList()): String =
        SignedJWT(JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.Builder()
            .subject(userId)
            .audience(AUDIENCE)
            .issuer(ISSUER)
            .claim("roles", roles)
            .apply { if (allowedPatients.isNotEmpty()) claim("allowed_patients", allowedPatients) }
            .build()).apply { sign(MACSigner(JWT_SECRET.toByteArray())) }.serialize()

    fun treatmentsConfig(dbName: String) = MapApplicationConfig(
        "jwt.domain" to ISSUER,
        "jwt.audience" to AUDIENCE,
        "jwt.realm" to "kdiab-treatments",
        "jwt.secret" to JWT_SECRET,
        "jwt.test" to "true",
        "storage.driverClassName" to "org.h2.Driver",
        "storage.jdbcUrl" to "jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "storage.username" to "root",
        "storage.password" to "password",
        "storage.maximumPoolSize" to "3",
        "storage.isAutoCommit" to "false",
        "storage.transactionIsolation" to "TRANSACTION_REPEATABLE_READ"
    )

    given("a running Treatments Service") {
        `when`("a patient manages their BOLUS treatments") {
            then("they can create and list treatments; a doctor can delete them") {
                testApplication {
                    environment { config = treatmentsConfig("e2e_treatments_patient") }
                    application { module() }
                    val client = createClient { install(ContentNegotiation) { json() } }

                    val sarahToken = token(SARAH_ID, listOf("PATIENT"))
                    val doctorToken = token("33333333-3333-3333-3333-333333333333", listOf("DOCTOR"), listOf(SARAH_ID))

                    // 1. List — empty initially (returns PagedTreatmentResponse)
                    val list0 = client.get("/api/v1/users/$SARAH_ID/treatments") {
                        bearerAuth(sarahToken)
                    }
                    list0.status shouldBe HttpStatusCode.OK
                    val emptyPaged = Json.decodeFromString<JsonObject>(list0.bodyAsText())
                    emptyPaged["items"]!!.jsonArray.size shouldBe 0
                    emptyPaged["totalCount"]!!.jsonPrimitive.content.toLong() shouldBe 0L

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
                    val onePaged = Json.decodeFromString<JsonObject>(list1.bodyAsText())
                    onePaged["items"]!!.jsonArray.size shouldBe 1
                    onePaged["totalCount"]!!.jsonPrimitive.content.toLong() shouldBe 1L

                    // 4. Type filter CARBS → empty (returns PagedTreatmentResponse)
                    val listCarbs = client.get("/api/v1/users/$SARAH_ID/treatments?type=CARBS") {
                        bearerAuth(sarahToken)
                    }
                    listCarbs.status shouldBe HttpStatusCode.OK
                    val carbsPaged = Json.decodeFromString<JsonObject>(listCarbs.bodyAsText())
                    carbsPaged["items"]!!.jsonArray.size shouldBe 0
                    carbsPaged["totalCount"]!!.jsonPrimitive.content.toLong() shouldBe 0L

                    // 5. Patient delete → 403 (delete restricted to DOCTOR/ADMIN)
                    val patientDelete = client.post("/api/v1/users/$SARAH_ID/treatments/delete") {
                        bearerAuth(sarahToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"treatmentIds":["$treatmentId"]}""")
                    }
                    patientDelete.status shouldBe HttpStatusCode.Forbidden

                    // 6. Doctor delete → 200
                    val delete = client.post("/api/v1/users/$SARAH_ID/treatments/delete") {
                        bearerAuth(doctorToken)
                        contentType(ContentType.Application.Json)
                        setBody("""{"treatmentIds":["$treatmentId"]}""")
                    }
                    delete.status shouldBe HttpStatusCode.OK

                    // 7. List → empty again
                    val list2 = client.get("/api/v1/users/$SARAH_ID/treatments") {
                        bearerAuth(sarahToken)
                    }
                    list2.status shouldBe HttpStatusCode.OK
                    val emptyPaged2 = Json.decodeFromString<JsonObject>(list2.bodyAsText())
                    emptyPaged2["items"]!!.jsonArray.size shouldBe 0
                }
            }
        }

        `when`("a patient tries to access another user's treatments") {
            then("they receive 403") {
                testApplication {
                    environment { config = treatmentsConfig("e2e_treatments_403") }
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
                    environment { config = treatmentsConfig("e2e_treatments_401") }
                    application { module() }
                    val resp = client.get("/api/v1/users/$SARAH_ID/treatments")
                    resp.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }
    }
})

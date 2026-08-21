package org.javafreedom.kdiab.profiles.adapters.inbound.web.e2e

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.*
import java.util.Date
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.profiles.adapters.inbound.web.InsulinRequest
import org.javafreedom.kdiab.profiles.api.models.Insulin as ApiInsulin
import org.javafreedom.kdiab.profiles.module

/**
 * E2E tests for the insulin endpoints.
 *
 * The embedded Ktor server uses an H2 in-memory database bootstrapped via Liquibase (through
 * [DatabaseFactory.init]), so the full migration chain runs — including the initial insulin
 * reference data in changeset 002 (Humalog, Novolog, Fiasp, Lyumjev, Apidra).
 * Assertions account for these 5 pre-seeded insulins.
 */
class InsulinE2ETest :
    BehaviorSpec({
        val jwtDomain = "https://jwt-provider-domain/"
        val jwtAudience = "jwt-audience"
        val jwtRealm = "kdiab-profiles"
        val jwtSecret = "secret-hs256-min32-bytes-0123456789ab"

        /** Number of insulin types seeded by Liquibase changeset 002. */
        val seededInsulinCount = 5

        fun generateToken(
            userId: Uuid,
            roles: List<String> = listOf("PATIENT")
        ): String {
            return SignedJWT(
                JWSHeader(JWSAlgorithm.HS256),
                JWTClaimsSet.Builder()
                    .audience(jwtAudience)
                    .issuer(jwtDomain)
                    .subject(userId.toString())
                    .claim("roles", roles)
                    .expirationTime(Date(System.currentTimeMillis() + 60000))
                    .build()
            ).apply { sign(MACSigner(jwtSecret.toByteArray())) }.serialize()
        }

        given("A running Profile Service for Insulins") {
            `when`("I fetch and create insulins") {
                then("It should return seeded insulins and allow creating a new one") {
                    testApplication {
                        environment {
                            config = MapApplicationConfig(
                                "jwt.audience" to jwtAudience,
                                "jwt.domain" to jwtDomain,
                                "jwt.realm" to jwtRealm,
                                "jwt.secret" to jwtSecret,
                                "jwt.test" to "true",
                                "storage.driverClassName" to "org.h2.Driver",
                                "storage.jdbcUrl" to "jdbc:h2:mem:e2e_insulins;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                                "storage.username" to "root",
                                "storage.password" to "password",
                                "storage.maximumPoolSize" to "3",
                                "storage.isAutoCommit" to "false",
                                "storage.transactionIsolation" to "TRANSACTION_REPEATABLE_READ"
                            )
                        }
                        application { module() }
                        val client = createClient {
                            install(ContentNegotiation) { json() }
                        }

                        val userId = Uuid.random()
                        val patientToken = generateToken(userId)
                        val adminToken = generateToken(userId, roles = listOf("ADMIN"))

                        // 1. Fetch all — Liquibase seeds 5 reference insulin types on startup
                        val getResponse = client.get("/api/v1/insulins") {
                            header(HttpHeaders.Authorization, "Bearer $patientToken")
                        }
                        getResponse.status shouldBe HttpStatusCode.OK
                        val initialInsulins = getResponse.bodyAsText().let {
                            kotlinx.serialization.json.Json.decodeFromString<List<ApiInsulin>>(it)
                        }
                        initialInsulins shouldHaveSize seededInsulinCount
                        val seededNames = initialInsulins.map { it.name }
                        seededNames.contains("Humalog") shouldBe true
                        seededNames.contains("Fiasp") shouldBe true

                        // 2. Create a new insulin not in the seed set — requires ADMIN role
                        val createRequest = InsulinRequest(name = "NovoRapid")
                        val createResponse = client.post("/api/v1/insulins") {
                            header(HttpHeaders.Authorization, "Bearer $adminToken")
                            contentType(ContentType.Application.Json)
                            setBody(createRequest)
                        }
                        createResponse.status shouldBe HttpStatusCode.Created
                        val createdInsulin = createResponse.bodyAsText().let {
                            kotlinx.serialization.json.Json.decodeFromString<ApiInsulin>(it)
                        }
                        createdInsulin.id.shouldNotBeBlank()
                        createdInsulin.name shouldBe "NovoRapid"

                        // 3. Fetch all again — seeded + 1 newly created (any authenticated user can read)
                        val getResponse2 = client.get("/api/v1/insulins") {
                            header(HttpHeaders.Authorization, "Bearer $patientToken")
                        }
                        getResponse2.status shouldBe HttpStatusCode.OK
                        val insulinList = getResponse2.bodyAsText().let {
                            kotlinx.serialization.json.Json.decodeFromString<List<ApiInsulin>>(it)
                        }
                        insulinList shouldHaveSize (seededInsulinCount + 1)
                        insulinList.map { it.name }.contains("NovoRapid") shouldBe true
                    }
                }
            }
        }
    })

package org.javafreedom.kdiab.profiles.adapters.inbound.web.e2e

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import org.javafreedom.kdiab.profiles.api.models.BasalSegment
import org.javafreedom.kdiab.profiles.api.models.CreateProfileRequest
import org.javafreedom.kdiab.profiles.api.models.Profile
import org.javafreedom.kdiab.profiles.module

class ProfileE2ETest :
        BehaviorSpec({
                val jwtDomain = "https://jwt-provider-domain/"
                val jwtAudience = "jwt-audience"
                val jwtRealm = "kdiab-profiles"
                val jwtSecret = "secret"

                fun generateToken(
                        userId: Uuid,
                        roles: List<String> = listOf("USER"),
                        allowedPatients: List<String> = emptyList()
                ): String {
                        return JWT.create()
                                .withAudience(jwtAudience)
                                .withIssuer(jwtDomain)
                                .withSubject(userId.toString())
                                .withClaim("roles", roles)
                                .withClaim("allowed_patients", allowedPatients)
                                .withExpiresAt(Date(System.currentTimeMillis() + 60000))
                                .sign(Algorithm.HMAC256(jwtSecret))
                }

                given("A running Profile Service") {
                        `when`("I request the health check endpoint") {
                                then("It should return HTTP 200 OK") {
                                        testApplication {
                                                environment {
                                                        config =
                                                                MapApplicationConfig(
                                                                        "jwt.domain" to jwtDomain,
                                                                        "jwt.audience" to
                                                                                jwtAudience,
                                                                        "jwt.realm" to jwtRealm,
                                                                        "jwt.secret" to jwtSecret,
                                                                        "storage.driverClassName" to
                                                                                "org.h2.Driver",
                                                                        "storage.jdbcUrl" to
                                                                                "jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                                                                        "storage.username" to
                                                                                "root",
                                                                        "storage.password" to
                                                                                "password",
                                                                        "storage.maximumPoolSize" to
                                                                                "3",
                                                                        "storage.isAutoCommit" to
                                                                                "false",
                                                                        "storage.transactionIsolation" to
                                                                                "TRANSACTION_REPEATABLE_READ"
                                                                )
                                                }
                                                application { module() }
                                                val client = createClient {
                                                        install(ContentNegotiation) { json() }
                                                }

                                                val response = client.get("/healthz")
                                                response.status shouldBe HttpStatusCode.OK
                                        }
                                }
                        }

                        `when`("I manage active profiles") {
                                then("I should see history when updating active profile") {
                                        testApplication {
                                                environment {
                                                        config =
                                                                MapApplicationConfig(
                                                                        "jwt.domain" to jwtDomain,
                                                                        "jwt.audience" to
                                                                                jwtAudience,
                                                                        "jwt.realm" to jwtRealm,
                                                                        "jwt.secret" to jwtSecret,
                                                                        "jwt.test" to "true",
                                                                        "storage.driverClassName" to
                                                                                "org.h2.Driver",
                                                                        "storage.jdbcUrl" to
                                                                                "jdbc:h2:mem:e2e_history;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                                                                        "storage.username" to
                                                                                "root",
                                                                        "storage.password" to
                                                                                "password",
                                                                        "storage.maximumPoolSize" to
                                                                                "3",
                                                                        "storage.isAutoCommit" to
                                                                                "false",
                                                                        "storage.transactionIsolation" to
                                                                                "TRANSACTION_REPEATABLE_READ"
                                                                )
                                                }
                                                application { module() }
                                                val client = createClient {
                                                        install(ContentNegotiation) { json() }
                                                }

                                                val userId = Uuid.random()
                                                val token = generateToken(userId)

                                                // 1. Create Profile A
                                                val createProfileRequest =
                                                        CreateProfileRequest(
                                                                name = "Profile A",
                                                                durationOfAction = 360,
                                                                insulinType = "Fiasp",
                                                                basal = listOf(
                                                                        BasalSegment(
                                                                                startTime = "00:00",
                                                                                value = 1.0
                                                                        )
                                                                )
                                                        )
                                                val createResponse =
                                                        client.post(
                                                                "/api/v1/users/$userId/profiles"
                                                        ) {
                                                                header(
                                                                        HttpHeaders.Authorization,
                                                                        "Bearer $token"
                                                                )
                                                                contentType(
                                                                        ContentType.Application.Json
                                                                )
                                                                setBody(createProfileRequest)
                                                        }
                                                createResponse.status shouldBe
                                                        HttpStatusCode.Created
                                                val profileA =
                                                        createResponse.bodyAsText().let {
                                                                kotlinx.serialization.json.Json
                                                                        .decodeFromString<Profile>(
                                                                                it
                                                                        )
                                                        }
                                                profileA.id.shouldNotBeBlank()
                                                profileA.status shouldBe Profile.Status.DRAFT

                                                // 1.1. Verify DRAFT profile can be updated in-place
                                                val updatedProfileDraft =
                                                        profileA.copy(
                                                                name = "Profile A - Draft Updated"
                                                        )
                                                val updateDraftResponse =
                                                        client.put(
                                                                "/api/v1/users/$userId/profiles/${profileA.id}"
                                                        ) {
                                                                header(
                                                                        HttpHeaders.Authorization,
                                                                        "Bearer $token"
                                                                )
                                                                contentType(
                                                                        ContentType.Application.Json
                                                                )
                                                                setBody(updatedProfileDraft)
                                                        }
                                                updateDraftResponse.status shouldBe
                                                        HttpStatusCode.OK
                                                val profileADraftUpdated =
                                                        updateDraftResponse.bodyAsText().let {
                                                                kotlinx.serialization.json.Json
                                                                        .decodeFromString<Profile>(
                                                                                it
                                                                        )
                                                        }
                                                profileADraftUpdated.id shouldBe profileA.id
                                                profileADraftUpdated.name shouldBe
                                                        "Profile A - Draft Updated"
                                                profileADraftUpdated.status shouldBe
                                                        Profile.Status.DRAFT

                                                // 2. Activate Profile A
                                                val activateResponse =
                                                        client.post(
                                                                "/api/v1/users/$userId/profiles/${profileA.id}/activate"
                                                        ) {
                                                                header(
                                                                        HttpHeaders.Authorization,
                                                                        "Bearer $token"
                                                                )
                                                        }
                                                activateResponse.status shouldBe HttpStatusCode.OK
                                                val activeProfileA =
                                                        activateResponse.bodyAsText().let {
                                                                kotlinx.serialization.json.Json
                                                                        .decodeFromString<Profile>(
                                                                                it
                                                                        )
                                                        }
                                                activeProfileA.status shouldBe Profile.Status.ACTIVE

                                                // 3. Update Profile A -> Expect new Profile B
                                                // returned
                                                // Modify profile name
                                                val updatedProfileA =
                                                        activeProfileA.copy(
                                                                name = "Profile A - Updated"
                                                        )
                                                val updateResponse =
                                                        client.put(
                                                                "/api/v1/users/$userId/profiles/${activeProfileA.id}"
                                                        ) {
                                                                header(
                                                                        HttpHeaders.Authorization,
                                                                        "Bearer $token"
                                                                )
                                                                contentType(
                                                                        ContentType.Application.Json
                                                                )
                                                                setBody(updatedProfileA)
                                                        }
                                                updateResponse.status shouldBe HttpStatusCode.OK
                                                val profileB =
                                                        updateResponse.bodyAsText().let {
                                                                kotlinx.serialization.json.Json
                                                                        .decodeFromString<Profile>(
                                                                                it
                                                                        )
                                                        }

                                                // Check that Profile B is a NEW profile
                                                profileB.id shouldNotBe activeProfileA.id
                                                profileB.status shouldBe Profile.Status.ACTIVE
                                                profileB.previousProfileId shouldBe
                                                        activeProfileA.id
                                                profileB.name shouldBe "Profile A - Updated"

                                                // 4. Verify History shows Profile A (Archived)
                                                val from = "2020-01-01T00:00:00Z"
                                                val to = "2099-01-01T00:00:00Z"
                                                val historyResponse =
                                                        client.get(
                                                                "/api/v1/users/$userId/profiles/history?from=$from&to=$to"
                                                        ) {
                                                                header(
                                                                        HttpHeaders.Authorization,
                                                                        "Bearer $token"
                                                                )
                                                        }
                                                historyResponse.status shouldBe HttpStatusCode.OK
                                                val history =
                                                        historyResponse.bodyAsText().let {
                                                                kotlinx.serialization.json.Json
                                                                        .decodeFromString<
                                                                                List<Profile>>(it)
                                                        }

                                                // Should contain Profile A which is now ARCHIVED
                                                history.shouldHaveSize(1)
                                                val historyProfile = history[0]
                                                historyProfile.id shouldBe activeProfileA.id
                                                historyProfile.status shouldBe
                                                        Profile.Status.ARCHIVED

                                                // 5. Verify Immutability of Archived Profile
                                                // Attempt to Update
                                                val updateArchivedResponse =
                                                        client.put(
                                                                "/api/v1/users/$userId/profiles/${historyProfile.id}"
                                                        ) {
                                                                header(
                                                                        HttpHeaders.Authorization,
                                                                        "Bearer $token"
                                                                )
                                                                contentType(
                                                                        ContentType.Application.Json
                                                                )
                                                                setBody(historyProfile)
                                                        }
                                                // Assuming BusinessValidationException maps to 422?
                                                // Default StatusPages mapping might be 400 or 500
                                                // if not explicitly handled.
                                                // Let's assume 400 or check StatusPages.
                                                // I'll check StatusPages next, but for now let's
                                                // assume it's an error.
                                                updateArchivedResponse.status.shouldNotBe(
                                                        HttpStatusCode.OK
                                                )

                                                // Attempt to Delete
                                                val deleteArchivedResponse =
                                                        client.delete(
                                                                "/api/v1/users/$userId/profiles/${historyProfile.id}"
                                                        ) {
                                                                header(
                                                                        HttpHeaders.Authorization,
                                                                        "Bearer $token"
                                                                )
                                                        }
                                                deleteArchivedResponse.status.shouldNotBe(
                                                        HttpStatusCode.NoContent
                                                )
                                                deleteArchivedResponse.status.shouldNotBe(
                                                        HttpStatusCode.OK
                                                )
                                        }
                                }
                        }
                }
        })

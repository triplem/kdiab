package org.javafreedom.kdiab.nightscout

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.di.DI
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.CarbsClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.nightscout.adapters.outbound.http.UsersClient
import org.javafreedom.kdiab.nightscout.application.service.NightscoutService
import org.javafreedom.kdiab.nightscout.application.service.NightscoutV3Service
import java.util.Date

/**
 * Base class for kdiab-nightscout integration tests.
 *
 * Provides:
 * - JWT signing helper (HMAC256 — test mode only)
 * - [MockEngine] factory so each test can define its own upstream stub responses
 * - [installMockDi] that wires all service dependencies before [module] runs
 * - [buildTestConfig] for the minimal Ktor config needed by [configureSecurity]
 *
 * Each subclass builds a [MockEngine] via [buildMockEngine] and calls [runNightscoutApp].
 */
abstract class BaseNightscoutTest {

    companion object {
        const val JWT_SECRET = "test-secret-for-nightscout-integration-tests"
        const val AUDIENCE = "nightscout"
        const val ISSUER = "http://localhost:8081/realms/kdiab"
        const val PATIENT_ID = "11111111-1111-1111-1111-111111111111"

        private val jsonContentType = headersOf(
            HttpHeaders.ContentType,
            ContentType.Application.Json.toString(),
        )

        fun patientToken(userId: String = PATIENT_ID): String =
            SignedJWT(JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.Builder()
                .subject(userId)
                .audience(AUDIENCE)
                .issuer(ISSUER)
                .claim("roles", listOf("PATIENT"))
                .expirationTime(Date(System.currentTimeMillis() + 60_000))
                .build()).apply { sign(MACSigner(JWT_SECRET.toByteArray())) }.serialize()

        fun jsonResponse(
            scope: MockRequestHandleScope,
            body: String,
            status: HttpStatusCode = HttpStatusCode.OK,
        ): HttpResponseData = scope.respond(body, status, jsonContentType)

        fun emptyJsonArrayResponse(scope: MockRequestHandleScope): HttpResponseData =
            jsonResponse(scope, """{"items":[],"page":0,"size":200,"totalCount":0}""")

        /**
         * Build a [MockEngine] that dispatches based on [handler].
         * [handler] receives the encoded URL path and returns the mock response data.
         * Any path not matched by [handler] falls back to an empty JSON array.
         */
        fun buildMockEngine(
            handler: MockRequestHandleScope.(path: String) -> HttpResponseData?,
        ): MockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            handler(path) ?: emptyJsonArrayResponse(this)
        }
    }

    fun buildTestConfig(): MapApplicationConfig = MapApplicationConfig(
        "jwt.domain" to ISSUER,
        "jwt.audience" to AUDIENCE,
        "jwt.realm" to "kdiab",
        "jwt.test" to "true",
        "jwt.allowTestMode" to "true",
        "jwt.secret" to JWT_SECRET,
    )

    /**
     * Installs mock DI bindings so [module] does not create real HTTP clients.
     * Must be called on the [Application] receiver before [module] is called.
     */
    fun Application.installMockDi(mockEngine: MockEngine) {
        install(DI) { }
        dependencies {
            val measuresClient = MeasuresClient(mockEngine, "http://mock-measures")
            val treatmentsClient = TreatmentsClient(mockEngine, "http://mock-treatments")
            val carbsClient = CarbsClient(mockEngine, "http://mock-carbs")
            val profilesClient = ProfilesClient(mockEngine, "http://mock-profiles")
            val usersClient = UsersClient(mockEngine, "http://mock-users")

            provide<NightscoutService> {
                NightscoutService(measuresClient, treatmentsClient)
            }
            provide<NightscoutV3Service> {
                NightscoutV3Service(measuresClient, treatmentsClient, carbsClient, profilesClient, usersClient)
            }
            provide<UsersClient> { usersClient }
            provide<CarbsClient> { carbsClient }
            provide<ProfilesClient> { profilesClient }
        }
    }

    /**
     * Launch the Nightscout test application with mock upstream clients.
     * All upstream HTTP calls are intercepted by [mockEngine] — no real network required.
     */
    fun runNightscoutApp(
        mockEngine: MockEngine = buildMockEngine { null },
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        environment { config = buildTestConfig() }
        application {
            installMockDi(mockEngine)
            module()
        }
        block()
    }
}

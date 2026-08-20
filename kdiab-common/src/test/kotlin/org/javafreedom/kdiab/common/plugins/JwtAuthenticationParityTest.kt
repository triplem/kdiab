@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.common.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterization / parity tests (task T1) for the Nimbus-backed [configureSecurity] in HMAC test mode.
 * Asserts the negative-path matrix behaves identically to the previous java-jwt implementation: the same
 * accept/reject outcome and the same generic 401. The critical case is a present-but-non-array `roles`
 * claim, which must reject with 401 (not throw / 500) — the §12a reviewer must-fix.
 */
class JwtAuthenticationParityTest {

    private companion object {
        // Nimbus HS256 requires a >= 256-bit (32-byte) secret (java-jwt did not enforce this).
        const val SECRET = "test-secret-for-unit-tests-only-0123456789"
        const val AUDIENCE = "measure"
        const val ISSUER = "http://localhost:8081/realms/kdiab-measures"
        const val SARAH = "11111111-1111-1111-1111-111111111111"

        fun mint(
            roles: Any? = listOf("PATIENT"),
            audience: String = AUDIENCE,
            issuer: String = ISSUER,
            subject: String = SARAH,
            signingSecret: String = SECRET,
            expiresAt: java.util.Date? = null,
            allowedPatients: List<String> = emptyList(),
        ): String = TestTokenMinter.hs256(
            secret = SECRET, audience = audience, issuer = issuer, subject = subject,
            roles = roles, signingSecret = signingSecret, expiresAt = expiresAt,
            allowedPatients = allowedPatients,
        )
    }

    private fun ApplicationTestBuilder.protectedApp() {
        environment {
            config = MapApplicationConfig(
                "jwt.audience" to AUDIENCE,
                "jwt.domain" to ISSUER,
                "jwt.realm" to "kdiab",
                "jwt.test" to "true",
                "jwt.secret" to SECRET,
            )
        }
        application {
            install(ContentNegotiation) { json() }
            configureSecurity()
            routing {
                authenticate("auth-jwt") {
                    get("/protected") {
                        val principal = call.principal<UserPrincipal>()!!
                        call.respondText("ok:${principal.userId}:${principal.roles.joinToString(",")}")
                    }
                }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.getProtected(token: String?): HttpResponse =
        client.get("/protected") { if (token != null) header(HttpHeaders.Authorization, "Bearer $token") }

    @Test
    fun `valid token authenticates and populates principal`() = testApplication {
        protectedApp()
        val res = getProtected(mint())
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("ok:$SARAH:PATIENT", res.bodyAsText())
    }

    @Test
    fun `missing token is rejected`() = testApplication {
        protectedApp()
        assertEquals(HttpStatusCode.Unauthorized, getProtected(null).status)
    }

    @Test
    fun `malformed bearer is rejected`() = testApplication {
        protectedApp()
        assertEquals(HttpStatusCode.Unauthorized, getProtected("not-a-jwt").status)
    }

    @Test
    fun `bad signature is rejected`() = testApplication {
        protectedApp()
        val res = getProtected(mint(signingSecret = "a-totally-different-secret-32bytes-long!"))
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `expired token is rejected`() = testApplication {
        protectedApp()
        val res = getProtected(mint(expiresAt = java.util.Date(System.currentTimeMillis() - 60_000)))
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `wrong audience is rejected`() = testApplication {
        protectedApp()
        assertEquals(HttpStatusCode.Unauthorized, getProtected(mint(audience = "profile")).status)
    }

    @Test
    fun `wrong issuer is rejected`() = testApplication {
        protectedApp()
        assertEquals(HttpStatusCode.Unauthorized, getProtected(mint(issuer = "http://evil/realms/x")).status)
    }

    @Test
    fun `empty roles is rejected`() = testApplication {
        protectedApp()
        assertEquals(HttpStatusCode.Unauthorized, getProtected(mint(roles = emptyList<String>())).status)
    }

    @Test
    fun `missing roles claim is rejected`() = testApplication {
        protectedApp()
        assertEquals(HttpStatusCode.Unauthorized, getProtected(mint(roles = null)).status)
    }

    @Test
    fun `present-but-non-array roles is rejected with 401 not 500 (reviewer must-fix)`() = testApplication {
        protectedApp()
        // roles as a scalar string — Nimbus getStringListClaim would THROW; the mapper must guard it.
        val res = getProtected(mint(roles = "PATIENT"))
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `malformed-UUID subject is rejected`() = testApplication {
        protectedApp()
        assertEquals(HttpStatusCode.Unauthorized, getProtected(mint(subject = "not-a-uuid")).status)
    }
}

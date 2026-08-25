package org.javafreedom.kdiab.profiles

import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SecurityConfigTest {

    /**
     * When jwt.test=true and the jwt.allowTestMode opt-in is affirmed but no
     * explicit jwt.secret is provided, the application must still refuse to start.
     * The opt-in guard fires before the secret guard (SR-7), so allowTestMode is
     * affirmed here to get past it and assert the secret guard is still enforced.
     * This prevents accidental use of the test-mode (HMAC-signed tokens with a
     * predictable secret) in production deployments.
     */
    @Test
    fun `application fails to start when jwt test mode is enabled without explicit secret`() {
        val exception = assertFailsWith<IllegalStateException> {
            testApplication {
                environment {
                    config = MapApplicationConfig(
                        "jwt.audience"     to "profile",
                        "jwt.domain"       to "https://example.com",
                        "jwt.realm"        to "kdiab-profiles",
                        "jwt.test"         to "true",
                        "jwt.allowTestMode" to "true",
                        "app.initDatabase" to "false",
                        // jwt.secret intentionally omitted
                    )
                }
                application { module() }
                startApplication()
            }
        }
        assertTrue(
            exception.message?.contains("jwt.secret") == true,
            "Expected error message to mention jwt.secret, got: ${exception.message}"
        )
    }

    /**
     * When jwt.test=true and jwt.secret is explicitly provided, the application
     * starts successfully.
     */
    @Test
    fun `application starts when jwt test mode has an explicit secret`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "jwt.audience"     to "profile",
                "jwt.domain"       to "org.javafreedom.kdiab",
                "jwt.realm"        to "kdiab-profiles",
                "jwt.secret"       to "test-secret-value-hs256-min32-bytes-ok",
                "jwt.test"         to "true",
                "jwt.allowTestMode" to "true",
                "app.initDatabase" to "false",
            )
        }
        application { module() }
        startApplication()
        // If we reach here the application started without exception
    }

    /**
     * Deny-by-default: when jwt.test=true is set without the explicit
     * jwt.allowTestMode opt-in, the application must refuse to start. This is the
     * primary guard against accidentally enabling the symmetric HMAC test verifier
     * in a production deployment (config drift, leaked test profile, misconfigured
     * env var). The opt-in guard fires before the secret guard, so this throws the
     * opt-in message even though jwt.secret is present.
     */
    @Test
    fun `application fails to start when jwt test mode is enabled without allow test mode opt-in`() {
        val exception = assertFailsWith<IllegalStateException> {
            testApplication {
                environment {
                    config = MapApplicationConfig(
                        "jwt.audience"     to "profile",
                        "jwt.domain"       to "https://example.com",
                        "jwt.realm"        to "kdiab-profiles",
                        "jwt.secret"       to "test-secret-value-hs256-min32-bytes-ok",
                        "jwt.test"         to "true",
                        "app.initDatabase" to "false",
                        // jwt.allowTestMode intentionally omitted (defaults to false)
                    )
                }
                application { module() }
                startApplication()
            }
        }
        assertTrue(
            exception.message?.contains("jwt.allowTestMode") == true ||
                exception.message?.contains("JWT_ALLOW_TEST_MODE") == true,
            "Expected error message to mention jwt.allowTestMode / JWT_ALLOW_TEST_MODE, " +
                "got: ${exception.message}"
        )
    }
}

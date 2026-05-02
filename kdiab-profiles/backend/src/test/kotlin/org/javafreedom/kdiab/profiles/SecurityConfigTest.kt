package org.javafreedom.kdiab.profiles

import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SecurityConfigTest {

    /**
     * When jwt.test=true is set without an explicit jwt.secret, the application
     * must refuse to start. This prevents accidental use of the test-mode
     * (HMAC-signed tokens with a predictable secret) in production deployments.
     */
    @Test
    fun `application fails to start when jwt test mode is enabled without explicit secret`() {
        val exception = assertFailsWith<IllegalStateException> {
            testApplication {
                environment {
                    config = MapApplicationConfig(
                        "jwt.audience" to "profile",
                        "jwt.domain" to "https://example.com",
                        "jwt.realm" to "kdiab-profiles",
                        "jwt.test" to "true"
                        // jwt.secret intentionally omitted
                    )
                }
                application { module(initDatabase = false) }
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
                "jwt.audience" to "profile",
                "jwt.domain" to "org.javafreedom.kdiab",
                "jwt.realm" to "kdiab-profiles",
                "jwt.secret" to "test-secret-value",
                "jwt.test" to "true"
            )
        }
        application { module(initDatabase = false) }
        startApplication()
        // If we reach here the application started without exception
    }
}

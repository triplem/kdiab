package org.javafreedom.kdiab.treatments.plugins

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import java.net.URI
import java.util.concurrent.TimeUnit
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.treatments.domain.model.Role
import io.github.oshai.kotlinlogging.KotlinLogging


private val logger = KotlinLogging.logger {}

private const val JWK_CACHE_MAX_SIZE = 10L
private const val JWK_CACHE_EXPIRES_IN = 24L
private const val JWK_RATE_LIMIT_BUCKET_SIZE = 10L
private const val JWK_RATE_LIMIT_REFILL_RATE = 1L
private const val JWT_ACCEPT_LEEWAY = 3L

fun Application.configureSecurity() {
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtDomain = environment.config.property("jwt.domain").getString()
    val jwtRealm = environment.config.property("jwt.realm").getString()
    val isTest = environment.config.propertyOrNull("jwt.test")?.getString()?.toBoolean() ?: false
    val explicitSecret = environment.config.propertyOrNull("jwt.secret")?.getString()
    check(!isTest || explicitSecret != null) {
        "jwt.secret (JWT_SECRET env var) must be set explicitly when jwt.test=true. " +
        "Do not use the test JWT mode in production."
    }
    // In test mode, explicitSecret is guaranteed non-null by the check above.
    // In production mode, jwtSecret is never referenced (JWK is used), so null is safe.
    val jwtSecret = explicitSecret

    val jwkProvider = if (!isTest) {
        val jwksUrl = environment.config.propertyOrNull("jwt.jwksUrl")?.getString()
            ?: "$jwtDomain/protocol/openid-connect/certs"
        val jwksUri = URI(jwksUrl)
        val isInternal = jwksUri.host == "localhost" ||
            jwksUri.host == "127.0.0.1" ||
            (jwksUri.host != null && !jwksUri.host.contains('.'))
        check(isInternal || jwksUri.scheme == "https") {
            "JWKS URL must use HTTPS for non-local endpoints (got '$jwksUrl'). " +
            "Set JWKS_URL to a secure https:// endpoint."
        }
        JwkProviderBuilder(jwksUri.toURL())
            .cached(JWK_CACHE_MAX_SIZE, JWK_CACHE_EXPIRES_IN, TimeUnit.HOURS)
            .rateLimited(JWK_RATE_LIMIT_BUCKET_SIZE, JWK_RATE_LIMIT_REFILL_RATE, TimeUnit.MINUTES)
            .build()
    } else null

    authentication {
        jwt("auth-jwt") {
            realm = jwtRealm

            if (isTest) {
                val secret = checkNotNull(jwtSecret) { "jwt.secret must be set when jwt.test=true" }
                verifier(
                    JWT.require(Algorithm.HMAC256(secret))
                        .withAudience(jwtAudience)
                        .withIssuer(jwtDomain)
                        .build()
                )
            } else {
                val provider = checkNotNull(jwkProvider) { "JWK provider must be configured" }
                verifier(provider, jwtDomain) {
                    acceptLeeway(JWT_ACCEPT_LEEWAY)
                }
            }
            @Suppress("UnreachableCode")
            validate { credential ->
                if (credential.payload.audience?.contains(jwtAudience) == true) {
                    val userId = runCatching { Uuid.parse(credential.payload.subject) }.getOrNull()
                        ?: return@validate null
                    val rawRoles =
                            credential.payload.getClaim("roles").asList(String::class.java)
                                    ?: emptyList()
                    val roles = rawRoles.mapNotNull { Role.fromString(it) }.toSet()
                    if (roles.isEmpty()) return@validate null

                    val rawPatients = credential
                            .payload
                            .getClaim("allowed_patients")
                            .asList(String::class.java)
                            ?: emptyList()
                    val allowedPatients = rawPatients
                            .mapNotNull { raw -> runCatching { Uuid.parse(raw) }.getOrNull() }
                            .toSet()

                    logger.debug {
                        "JWT validated: userId=$userId roles=$roles " +
                        "rawAllowedPatients=$rawPatients allowedPatients=$allowedPatients"
                    }

                    UserPrincipal(userId, roles, allowedPatients)
                } else {
                    logger.warn {
                        "JWT rejected: audience mismatch " +
                        "(expected=$jwtAudience actual=${credential.payload.audience})"
                    }
                    null
                }
            }
        }
    }
}

data class UserPrincipal(val userId: Uuid, val roles: Set<Role>, val allowedPatients: Set<Uuid>) {
    fun isAdmin() = roles.contains(Role.ADMIN)
    fun isDoctor() = roles.contains(Role.DOCTOR)
    fun canAccess(targetUserId: Uuid) =
            userId == targetUserId ||
                    isAdmin() ||
                    (isDoctor() && allowedPatients.contains(targetUserId))
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.analyze.plugins

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.analyze.domain.exception.AuthenticationException
import org.javafreedom.kdiab.analyze.domain.model.Role

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
    val jwtSecret = environment.config.propertyOrNull("jwt.secret")?.getString()
    check(!isTest || jwtSecret != null) {
        "jwt.secret must be set when jwt.test=true"
    }

    val jwkProvider = if (!isTest) {
        val jwksUrl = environment.config.propertyOrNull("jwt.jwksUrl")?.getString()
            ?: "$jwtDomain/protocol/openid-connect/certs"
        val jwksUri = URI(jwksUrl)
        val isInternal = jwksUri.host == "localhost" ||
            jwksUri.host == "127.0.0.1" ||
            (jwksUri.host != null && !jwksUri.host.contains('.'))
        check(isInternal || jwksUri.scheme == "https") {
            "JWKS URL must use HTTPS for non-local endpoints (got '$jwksUrl')"
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
                verifier(
                    JWT.require(Algorithm.HMAC256(jwtSecret!!))
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

            validate { credential ->
                if (credential.payload.audience?.contains(jwtAudience) == true) {
                    val userId = runCatching { Uuid.parse(credential.payload.subject) }.getOrNull()
                        ?: return@validate null
                    val rawRoles = credential.payload.getClaim("roles").asList(String::class.java) ?: emptyList()
                    val roles = rawRoles.mapNotNull { Role.fromString(it) }.toSet()
                    if (roles.isEmpty()) return@validate null
                    val allowedPatients = credential.payload
                        .getClaim("allowed_patients").asList(String::class.java)
                        ?.mapNotNull { raw -> runCatching { Uuid.parse(raw) }.getOrNull() }
                        ?.toSet() ?: emptySet()
                    val glucoseUnit = credential.payload.getClaim("glucose_unit").asString() ?: "mg/dL"
                    val weightUnit = credential.payload.getClaim("weight_unit").asString() ?: "kg"

                    UserPrincipal(
                        userId, roles, allowedPatients, glucoseUnit, weightUnit,
                        audiences = credential.payload.audience ?: emptyList(),
                    )
                } else {
                    null
                }
            }
        }
    }
}

data class UserPrincipal(
    val userId: Uuid,
    val roles: Set<Role>,
    val allowedPatients: Set<Uuid>,
    val glucoseUnit: String = "mg/dL",
    val weightUnit: String = "kg",
    val audiences: List<String> = emptyList(),
) {
    fun isAdmin() = roles.contains(Role.ADMIN)
    fun isDoctor() = roles.contains(Role.DOCTOR)
    fun canAccess(targetUserId: Uuid) =
        userId == targetUserId ||
            isAdmin() ||
            (isDoctor() && allowedPatients.contains(targetUserId))
}

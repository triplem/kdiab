@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.common.plugins

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.proc.SingleKeyJWSKeySelector
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.BadJWTException
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.*
import io.ktor.server.response.*
import java.net.URI
import java.text.ParseException
import javax.crypto.spec.SecretKeySpec
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.model.Role

private val securityLogger = KotlinLogging.logger {}

private const val JWK_CACHE_TTL_MS = 24L * 60 * 60 * 1000          // 24h (was JwkProvider cache TTL)
private const val JWK_CACHE_REFRESH_TIMEOUT_MS = 15L * 1000         // 15s to refresh on an unknown kid
private const val JWK_RATE_LIMIT_MIN_INTERVAL_MS = 30L * 1000       // >= 1 refetch / 30s (mirrors the old bucket)
private const val JWT_ACCEPT_LEEWAY_SECONDS = 3                     // clock skew (== old acceptLeeway)
private const val HMAC_MIN_SECRET_BYTES = 32                        // HS256 requires a >= 256-bit secret

/** Why a token was rejected — logged (never returned to the caller) as `reason=`. */
enum class RejectionReason(val wire: String) {
    NO_TOKEN("no-token"),
    MALFORMED("malformed"),
    BAD_SIGNATURE("bad-signature"),
    EXPIRED("expired"),
    WRONG_AUDIENCE("wrong-audience"),
    WRONG_ISSUER("wrong-issuer"),
    INVALID_CLAIMS("invalid-claims"),
}

/** Verification result: the claims on success, or a classified reason on failure. */
sealed interface VerificationOutcome {
    data class Verified(val claims: JWTClaimsSet) : VerificationOutcome
    data class Rejected(val reason: RejectionReason) : VerificationOutcome
}

/** Parsed + validated `jwt.*` config. */
data class JwtConfig(
    val audience: String,
    val domain: String,
    val realm: String,
    val isTest: Boolean,
    val secret: String?,
    val jwksUrl: String,
)

fun readJwtConfig(environment: ApplicationEnvironment): JwtConfig {
    val audience = environment.config.property("jwt.audience").getString()
    val domain = environment.config.property("jwt.domain").getString()
    val realm = environment.config.property("jwt.realm").getString()
    val isTest = environment.config.propertyOrNull("jwt.test")?.getString()?.toBoolean() ?: false
    val secret = environment.config.propertyOrNull("jwt.secret")?.getString()
    check(!isTest || secret != null) {
        "jwt.secret (JWT_SECRET env var) must be set explicitly when jwt.test=true. " +
            "Do not use the test JWT mode in production."
    }
    val jwksUrl = environment.config.propertyOrNull("jwt.jwksUrl")?.getString()
        ?: "$domain/protocol/openid-connect/certs"
    if (!isTest) {
        val jwksUri = URI(jwksUrl)
        val isInternal = jwksUri.host == "localhost" ||
            jwksUri.host == "127.0.0.1" ||
            (jwksUri.host != null && !jwksUri.host.contains('.'))
        check(isInternal || jwksUri.scheme == "https") {
            "JWKS URL must use HTTPS for non-local endpoints (got '$jwksUrl'). " +
                "Set JWKS_URL to a secure https:// endpoint."
        }
    }
    return JwtConfig(audience, domain, realm, isTest, secret, jwksUrl)
}

// ── Verification (Nimbus) ─────────────────────────────────────────────────────

/** One shared claims verifier so prod (JWKS) and test (HMAC) enforce issuer+audience+exp identically. */
private fun claimsVerifier(cfg: JwtConfig) =
    DefaultJWTClaimsVerifier<SecurityContext>(
        cfg.audience,
        JWTClaimsSet.Builder().issuer(cfg.domain).build(),
        emptySet(),
    ).apply { maxClockSkew = JWT_ACCEPT_LEEWAY_SECONDS }

fun interface TokenVerifier {
    fun verify(token: String): VerificationOutcome
}

private fun DefaultJWTProcessor<SecurityContext>.toOutcome(token: String): VerificationOutcome =
    try {
        VerificationOutcome.Verified(process(token, null))
    } catch (e: ParseException) {
        securityLogger.debug(e) { "jwt parse failure" }
        VerificationOutcome.Rejected(RejectionReason.MALFORMED)
    } catch (e: BadJWTException) {
        // BadJWTException extends BadJOSEException — MUST be caught first.
        val msg = e.message.orEmpty()
        val reason = when {
            msg.contains("Expired", ignoreCase = true) || msg.contains("before use", ignoreCase = true) ->
                RejectionReason.EXPIRED
            msg.contains("audience", ignoreCase = true) -> RejectionReason.WRONG_AUDIENCE
            msg.contains("iss", ignoreCase = true) -> RejectionReason.WRONG_ISSUER
            else -> RejectionReason.INVALID_CLAIMS
        }
        VerificationOutcome.Rejected(reason)
    } catch (e: BadJOSEException) {
        securityLogger.debug(e) { "jwt signature/key failure" }
        VerificationOutcome.Rejected(RejectionReason.BAD_SIGNATURE)
    }

class JwksTokenVerifier(cfg: JwtConfig) : TokenVerifier {
    private val processor = DefaultJWTProcessor<SecurityContext>().apply {
        val jwkSource: JWKSource<SecurityContext> =
            JWKSourceBuilder.create<SecurityContext>(URI(cfg.jwksUrl).toURL())
                .cache(JWK_CACHE_TTL_MS, JWK_CACHE_REFRESH_TIMEOUT_MS)
                .rateLimited(JWK_RATE_LIMIT_MIN_INTERVAL_MS)
                .retrying(true)
                .build()
        jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
        jwtClaimsSetVerifier = claimsVerifier(cfg)
    }

    override fun verify(token: String): VerificationOutcome = processor.toOutcome(token)
}

class HmacTokenVerifier(cfg: JwtConfig) : TokenVerifier {
    private val processor = DefaultJWTProcessor<SecurityContext>().apply {
        val key = SecretKeySpec(requireNotNull(cfg.secret).toByteArray(), "HmacSHA256")
        jwsKeySelector = SingleKeyJWSKeySelector(JWSAlgorithm.HS256, key)
        jwtClaimsSetVerifier = claimsVerifier(cfg)
    }

    init {
        require(requireNotNull(cfg.secret).toByteArray().size >= HMAC_MIN_SECRET_BYTES) {
            "jwt.secret must be at least $HMAC_MIN_SECRET_BYTES bytes for HS256"
        }
    }

    override fun verify(token: String): VerificationOutcome = processor.toOutcome(token)
}

// ── Claims → UserPrincipal (exact buildPrincipal parity) ──────────────────────

// Nimbus typed accessors THROW ParseException on a present-but-wrong-shape claim, whereas java-jwt's
// getClaim(...).asList/.asString return null. Guard every typed access so a shape mismatch is treated
// as absent — reproducing java-jwt's null-on-mismatch (esp. FR-4 present-but-non-array roles → reject).
private fun JWTClaimsSet.stringListOrEmpty(name: String): List<String> =
    runCatching { getStringListClaim(name) }.getOrNull() ?: emptyList()

private fun JWTClaimsSet.stringOrNull(name: String): String? =
    runCatching { getStringClaim(name) }.getOrNull()

fun mapToPrincipal(claims: JWTClaimsSet): UserPrincipal? =
    runCatching { Uuid.parse(claims.subject) }.getOrNull()?.let { userId ->
        claims.stringListOrEmpty("roles")
            .mapNotNull { Role.fromString(it) }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { roles ->
                val allowedPatients = claims.stringListOrEmpty("allowed_patients")
                    .mapNotNull { raw -> runCatching { Uuid.parse(raw) }.getOrNull() }
                    .toSet()
                val audiences = runCatching { claims.audience }.getOrNull() ?: emptyList()
                val timezone = claims.stringOrNull("timezone") ?: "UTC"
                UserPrincipal(userId, roles, allowedPatients, audiences, timezone)
            }
    }

// ── Ktor custom AuthenticationProvider ────────────────────────────────────────

private fun ApplicationCall.clientIp(): String =
    request.header("X-Forwarded-For")
        ?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        ?: request.local.remoteHost

class JwtAuthenticationProvider(config: Config) : AuthenticationProvider(config) {
    private val verifier = config.verifier
    private val challengeKey: String = config.name ?: "auth-jwt"

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val header = context.call.request.parseAuthorizationHeader()
        val token = (header as? HttpAuthHeader.Single)
            ?.takeIf { it.authScheme.equals("Bearer", ignoreCase = true) }
            ?.blob
        if (token.isNullOrBlank()) {
            context.rejectWith(challengeKey, RejectionReason.NO_TOKEN)
            return
        }
        when (val outcome = verifier.verify(token)) {
            is VerificationOutcome.Rejected -> context.rejectWith(challengeKey, outcome.reason)
            is VerificationOutcome.Verified ->
                mapToPrincipal(outcome.claims)
                    ?.let { context.principal(it) }
                    ?: context.rejectWith(challengeKey, RejectionReason.INVALID_CLAIMS)
        }
    }

    class Config(name: String) : AuthenticationProvider.Config(name) {
        lateinit var verifier: TokenVerifier
    }
}

private fun AuthenticationContext.rejectWith(providerName: String, reason: RejectionReason) {
    val cause =
        if (reason == RejectionReason.NO_TOKEN) AuthenticationFailedCause.NoCredentials
        else AuthenticationFailedCause.InvalidCredentials
    challenge(providerName, cause) { challenge, call ->
        securityLogger.warn {
            "security_event=TOKEN_REJECTED " +
                "reason=${reason.wire} " +
                "path=${call.request.path()} " +
                "method=${call.request.httpMethod.value} " +
                "remote=${call.clientIp()} " +
                "correlationId=${call.callId ?: "-"}"
        }
        val status = HttpStatusCode.Unauthorized
        call.respond(status, ErrorResponse(status.value, "Token is not valid or has expired"))
        challenge.complete()
    }
}

fun AuthenticationConfig.jwtAuth(name: String, configure: JwtAuthenticationProvider.Config.() -> Unit) {
    register(JwtAuthenticationProvider(JwtAuthenticationProvider.Config(name).apply(configure)))
}

fun Application.configureSecurity() {
    val cfg = readJwtConfig(environment)
    val tokenVerifier: TokenVerifier = if (cfg.isTest) HmacTokenVerifier(cfg) else JwksTokenVerifier(cfg)
    authentication {
        jwtAuth("auth-jwt") { verifier = tokenVerifier }
    }
}

data class UserPrincipal(
    val userId: Uuid,
    val roles: Set<Role>,
    val allowedPatients: Set<Uuid>,
    val audiences: List<String> = emptyList(),
    val timezone: String = "UTC",
) {
    fun isAdmin() = roles.contains(Role.ADMIN)
    fun isDoctor() = roles.contains(Role.DOCTOR)
    fun canAccess(targetUserId: Uuid) =
        userId == targetUserId ||
            isAdmin() ||
            (isDoctor() && allowedPatients.contains(targetUserId))
}

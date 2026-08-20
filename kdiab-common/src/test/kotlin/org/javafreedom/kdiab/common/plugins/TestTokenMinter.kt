package org.javafreedom.kdiab.common.plugins

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.util.Date

/**
 * Jackson-free HS256 test-token minter (Nimbus MACSigner) — replaces `com.auth0.jwt.JWT.create()`.
 * Flexible enough to mint the full negative-path parity matrix (wrong shape / audience / issuer /
 * expiry / signature). The eventual cross-service shared fixture (task T4) is this same helper.
 */
object TestTokenMinter {

    @Suppress("LongParameterList")
    fun hs256(
        secret: String,
        audience: String,
        issuer: String,
        subject: String,
        roles: Any? = listOf("PATIENT"),
        allowedPatients: List<String> = emptyList(),
        timezone: Any? = null,
        expiresAt: Date? = null,
        notBefore: Date? = null,
        signingSecret: String = secret,
    ): String {
        val builder = JWTClaimsSet.Builder()
            .subject(subject)
            .audience(audience)
            .issuer(issuer)
        if (roles != null) builder.claim("roles", roles)
        if (allowedPatients.isNotEmpty()) builder.claim("allowed_patients", allowedPatients)
        if (timezone != null) builder.claim("timezone", timezone)
        if (expiresAt != null) builder.expirationTime(expiresAt)
        if (notBefore != null) builder.notBeforeTime(notBefore)
        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), builder.build())
        jwt.sign(MACSigner(signingSecret.toByteArray()))
        return jwt.serialize()
    }

    fun secondsFromNow(seconds: Long): Date = Date(System.currentTimeMillis() + seconds * 1000)
}

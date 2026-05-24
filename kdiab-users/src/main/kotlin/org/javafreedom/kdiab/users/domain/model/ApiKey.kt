@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.domain.model

import kotlinx.datetime.Instant

@Suppress("MagicNumber")
enum class ApiKeyExpiry(val months: Int?) {
    THREE_MONTHS(3),
    SIX_MONTHS(6),
    TWELVE_MONTHS(12),
    NO_EXPIRY(null),
}

data class ApiKey(
    val id: String,
    val clientId: String,
    val name: String,
    val expiresAt: Instant?,
    val createdAt: Instant,
)

data class ApiKeyCreated(
    val apiKey: ApiKey,
    val secret: String,
    val tokenEndpoint: String,
)

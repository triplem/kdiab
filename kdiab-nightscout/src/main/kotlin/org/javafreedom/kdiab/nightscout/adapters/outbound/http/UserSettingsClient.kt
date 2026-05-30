package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.common.plugins.CircuitBreaker
import org.javafreedom.kdiab.nightscout.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private const val DEFAULT_GLUCOSE_UNIT = "mg/dL"

class UserSettingsClient(
    private val httpClientEngine: HttpClientEngine,
    private val baseUrl: String,
    val circuitBreaker: CircuitBreaker = CircuitBreaker(name = "users"),
) {
    /**
     * Fetches the glucose unit preference for the authenticated user from kdiab-users.
     * The bearer token is forwarded unchanged so the users service can identify the caller.
     * Falls back to "mg/dL" on any upstream error so nightscout remains operational.
     */
    suspend fun getGlucoseUnit(authorization: String): String {
        val client = HttpClient(httpClientEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return try {
            val response = circuitBreaker.execute {
                client.get("$baseUrl/api/v1/users/me") {
                    header(HttpHeaders.Authorization, authorization)
                }
            }
            if (!response.status.isSuccess()) {
                logger.warn {
                    "users settings fetch failed status=${response.status.value} " +
                        "url=$baseUrl/api/v1/users/me — falling back to $DEFAULT_GLUCOSE_UNIT"
                }
                throw UpstreamException(
                    service = "users",
                    statusCode = response.status.value,
                    reason = response.status.description,
                    url = "$baseUrl/api/v1/users/me",
                )
            }
            val body = response.body<UserMeResponse>()
            body.settings?.units?.glucoseUnit ?: DEFAULT_GLUCOSE_UNIT
        } finally {
            client.close()
        }
    }
}

@Serializable
private data class UserMeResponse(
    val settings: SettingsPartial? = null,
)

@Serializable
private data class SettingsPartial(
    val units: UnitsPartial? = null,
)

@Serializable
private data class UnitsPartial(
    val glucoseUnit: String? = null,
)

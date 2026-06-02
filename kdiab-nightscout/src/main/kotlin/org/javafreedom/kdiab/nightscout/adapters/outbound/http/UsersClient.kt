package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.common.plugins.CircuitBreaker
import org.javafreedom.kdiab.nightscout.api.upstream.users.models.UserResponse
import org.javafreedom.kdiab.nightscout.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private const val DEFAULT_GLUCOSE_UNIT = "mg/dL"

class UsersClient(
    httpClientEngine: HttpClientEngine,
    private val baseUrl: String,
    private val circuitBreaker: CircuitBreaker = CircuitBreaker(name = "users"),
) {
    private val client = HttpClient(httpClientEngine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun getGlucoseUnit(authorization: String): String {
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
        val body = response.body<UserResponse>()
        return body.settings?.units?.glucoseUnit?.value ?: DEFAULT_GLUCOSE_UNIT
    }
}

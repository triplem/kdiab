package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

@Serializable
data class ProfileDto(
    val id: String,
    val userId: String,
    val status: String,
    val name: String,
    val createdAt: String? = null,
    val previousProfileId: String? = null,
)

class ProfilesClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getProfiles(userId: String, authorization: String, correlationId: String): List<ProfileDto> {
        logger.debug { "Fetching profiles for user $userId from $baseUrl" }
        val response = httpClient.get("$baseUrl/api/v1/users/$userId/profiles") {
            header(HttpHeaders.Authorization, authorization)
            header("X-Correlation-ID", correlationId)
        }
        if (!response.status.isSuccess()) {
            throw UpstreamException("profiles", response.status.value, response.status.description)
        }
        return response.body()
    }
}

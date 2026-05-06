package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private const val LOG_BODY_MAX_CHARS = 200

@Serializable
data class ProfileDto(
    val id: String,
    val userId: String,
    val status: String,
    val name: String,
    val createdAt: String? = null,
    val validFrom: String? = null,
    val previousProfileId: String? = null,
    val activatedAt: String? = null,
    val archivedAt: String? = null,
)

class ProfilesClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getProfiles(userId: String, authorization: String, correlationId: String): List<ProfileDto> {
        val start = System.currentTimeMillis()
        val response = httpClient.get("$baseUrl/api/v1/users/$userId/profiles") {
            header(HttpHeaders.Authorization, authorization)
            header("X-Correlation-ID", correlationId)
        }
        val ms = System.currentTimeMillis() - start
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrNull()
            val requestUrl = response.request.url.toString()
            logger.warn {
                "Upstream profiles returned ${response.status.value} in ${ms}ms" +
                    " url=$requestUrl body=${body?.take(LOG_BODY_MAX_CHARS)}"
            }
            throw UpstreamException(
                "profiles",
                response.status.value,
                response.status.description,
                responseBody = body,
                url = requestUrl,
            )
        }
        logger.info { "Fetched profiles from upstream in ${ms}ms [status=${response.status.value}]" }
        return response.body()
    }
}

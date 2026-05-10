package org.javafreedom.kdiab.calc.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.Profile
import org.javafreedom.kdiab.calc.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private const val LOG_BODY_MAX_CHARS = 200
private const val DEFAULT_PAGE_SIZE = 50

@Serializable
private data class PagedProfilesDto(
    val items: List<Profile>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)

class ProfilesClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getActiveProfile(userId: String, authorization: String, correlationId: String): Profile? {
        val start = System.currentTimeMillis()
        val response = httpClient.get("$baseUrl/api/v1/users/$userId/profiles") {
            parameter("page", 0)
            parameter("size", DEFAULT_PAGE_SIZE)
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
                service = "profiles",
                statusCode = response.status.value,
                message = response.status.description,
                responseBody = body,
                url = requestUrl,
            )
        }
        logger.info { "Fetched profiles from upstream in ${ms}ms [status=${response.status.value}]" }
        val paged = response.body<PagedProfilesDto>()
        return paged.items.firstOrNull { it.status == Profile.Status.ACTIVE }
    }
}

package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private const val LOG_BODY_MAX_CHARS = 200

@Serializable
data class TreatmentDto(
    val id: String,
    val userId: String,
    val treatedAt: String,
    val type: String,
    val notes: String? = null,
    val data: JsonObject,
)

class TreatmentsClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getTreatments(
        userId: String,
        authorization: String,
        correlationId: String,
        from: String? = null,
        to: String? = null,
    ): List<TreatmentDto> {
        val start = System.currentTimeMillis()
        val response = httpClient.get("$baseUrl/api/v1/users/$userId/treatments") {
            header(HttpHeaders.Authorization, authorization)
            header("X-Correlation-ID", correlationId)
            if (from != null) parameter("from", from)
            if (to != null) parameter("to", to)
        }
        val ms = System.currentTimeMillis() - start
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrNull()
            val requestUrl = response.request.url.toString()
            logger.warn {
                "Upstream treatments returned ${response.status.value} in ${ms}ms" +
                    " url=$requestUrl body=${body?.take(LOG_BODY_MAX_CHARS)}"
            }
            throw UpstreamException(
                "treatments",
                response.status.value,
                response.status.description,
                responseBody = body,
                url = requestUrl,
            )
        }
        logger.info { "Fetched treatments from upstream in ${ms}ms [status=${response.status.value}]" }
        return response.body()
    }
}

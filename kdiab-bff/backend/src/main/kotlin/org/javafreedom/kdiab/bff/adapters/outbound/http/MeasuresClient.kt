package org.javafreedom.kdiab.bff.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.bff.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

@Serializable
data class MeasureDto(
    val id: String,
    val userId: String,
    val measuredAt: String,
    val type: String,
    val source: String? = null,
    val data: JsonObject,
    val status: String,
)

class MeasuresClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getMeasures(userId: String, authorization: String): List<MeasureDto> {
        logger.debug { "Fetching measures for user $userId from $baseUrl" }
        val response = httpClient.get("$baseUrl/api/v1/users/$userId/measures") {
            header(HttpHeaders.Authorization, authorization)
        }
        if (!response.status.isSuccess()) {
            throw UpstreamException("measures", response.status.value, response.status.description)
        }
        return response.body()
    }
}

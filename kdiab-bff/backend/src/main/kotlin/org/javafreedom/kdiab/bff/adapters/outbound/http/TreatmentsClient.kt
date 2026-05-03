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
    suspend fun getTreatments(userId: String, authorization: String, correlationId: String): List<TreatmentDto> {
        logger.debug { "Fetching treatments for user $userId from $baseUrl" }
        val response = httpClient.get("$baseUrl/api/v1/users/$userId/treatments") {
            header(HttpHeaders.Authorization, authorization)
            header("X-Correlation-ID", correlationId)
        }
        if (!response.status.isSuccess()) {
            throw UpstreamException("treatments", response.status.value, response.status.description)
        }
        return response.body()
    }
}

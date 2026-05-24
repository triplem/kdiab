package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.*
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.header
import io.ktor.client.statement.request
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.nightscout.api.upstream.profiles.DefaultApi
import org.javafreedom.kdiab.nightscout.api.upstream.profiles.models.CreateProfileRequest
import org.javafreedom.kdiab.nightscout.api.upstream.profiles.models.Profile
import org.javafreedom.kdiab.nightscout.api.upstream.profiles.models.Profile.Status
import org.javafreedom.kdiab.common.plugins.CircuitBreaker
import org.javafreedom.kdiab.nightscout.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private val profilesJson = Json { ignoreUnknownKeys = true }

private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 30_000L
private const val PROFILES_PAGE_SIZE = 200
private const val HTTP_NOT_FOUND = 404

class ProfilesClient(
    private val httpClientEngine: HttpClientEngine,
    private val baseUrl: String,
    val circuitBreaker: CircuitBreaker = CircuitBreaker(name = "profiles"),
) {
    private fun buildApi(authorization: String, correlationId: String): DefaultApi {
        val token = authorization.removePrefix("Bearer ").trim()
        return DefaultApi(
            baseUrl = "$baseUrl/api/v1",
            httpClientEngine = httpClientEngine,
            httpClientConfig = { config ->
                config.install(ContentNegotiation) { json(profilesJson) }
                config.install(DefaultRequest) { header("X-Correlation-ID", correlationId) }
                config.install(HttpTimeout) {
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                }
                config.install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 3)
                    exponentialDelay()
                }
            },
        ).apply { setBearerToken(token) }
    }

    suspend fun listProfiles(
        userId: String,
        authorization: String,
        correlationId: String,
        statusFilter: List<String>? = null,
    ): List<Profile> {
        val api = buildApi(authorization, correlationId)
        val result = mutableListOf<Profile>()
        var page = 0
        var totalCount = Long.MAX_VALUE

        while (result.size < totalCount) {
            val httpResponse = circuitBreaker.execute {
                api.listProfiles(userId = userId, page = page, size = PROFILES_PAGE_SIZE, status = statusFilter)
            }
            if (!httpResponse.success) {
                val requestUrl = httpResponse.response.request.url.toString()
                throw UpstreamException(
                    service = "profiles",
                    statusCode = httpResponse.status,
                    reason = httpResponse.response.status.description,
                    url = requestUrl,
                )
            }
            val paged = httpResponse.body()
            totalCount = paged.totalCount
            result.addAll(paged.items)
            if (paged.items.isEmpty()) break
            page++
        }

        logger.info { "Fetched ${result.size} profiles for nightscout userId=$userId" }
        return result
    }

    suspend fun getProfile(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
    ): Profile? {
        val api = buildApi(authorization, correlationId)
        val httpResponse = circuitBreaker.execute {
            api.getProfile(userId = userId, profileId = id)
        }
        if (httpResponse.status == HTTP_NOT_FOUND) return null
        if (!httpResponse.success) {
            val requestUrl = httpResponse.response.request.url.toString()
            throw UpstreamException(
                service = "profiles",
                statusCode = httpResponse.status,
                reason = httpResponse.response.status.description,
                url = requestUrl,
            )
        }
        return httpResponse.body()
    }

    suspend fun createProfile(
        userId: String,
        authorization: String,
        correlationId: String,
        request: CreateProfileRequest,
    ): Profile {
        val api = buildApi(authorization, correlationId)
        val httpResponse = circuitBreaker.execute {
            api.createProfile(userId = userId, createProfileRequest = request)
        }
        if (!httpResponse.success) {
            val requestUrl = httpResponse.response.request.url.toString()
            throw UpstreamException(
                service = "profiles",
                statusCode = httpResponse.status,
                reason = httpResponse.response.status.description,
                url = requestUrl,
            )
        }
        logger.info { "Created profile for nightscout userId=$userId" }
        return httpResponse.body()
    }

    suspend fun updateProfile(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
        request: Profile,
    ): Profile {
        val api = buildApi(authorization, correlationId)
        val httpResponse = circuitBreaker.execute {
            api.updateProfile(userId = userId, profileId = id, profile = request)
        }
        if (!httpResponse.success) {
            val requestUrl = httpResponse.response.request.url.toString()
            throw UpstreamException(
                service = "profiles",
                statusCode = httpResponse.status,
                reason = httpResponse.response.status.description,
                url = requestUrl,
            )
        }
        logger.info { "Updated profile profileId=$id for nightscout userId=$userId" }
        return httpResponse.body()
    }

    suspend fun archiveProfile(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
    ) {
        val api = buildApi(authorization, correlationId)
        val httpResponse = circuitBreaker.execute {
            api.deleteProfile(userId = userId, profileId = id)
        }
        if (!httpResponse.success) {
            val requestUrl = httpResponse.response.request.url.toString()
            throw UpstreamException(
                service = "profiles",
                statusCode = httpResponse.status,
                reason = httpResponse.response.status.description,
                url = requestUrl,
            )
        }
        logger.info { "Archived profile profileId=$id for nightscout userId=$userId" }
    }

    suspend fun getActiveProfile(
        userId: String,
        authorization: String,
        correlationId: String,
    ): Profile? =
        listProfiles(userId, authorization, correlationId, statusFilter = listOf(Status.ACTIVE.value))
            .firstOrNull()
}

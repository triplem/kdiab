package org.javafreedom.kdiab.calc.domain.exception

/**
 * Thrown when an upstream service returns an unexpected error response.
 */
class UpstreamException(
    val service: String,
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
    val responseBody: String? = null,
    val url: String? = null,
) : RuntimeException("[$service] HTTP $statusCode${url?.let { " at $it" } ?: ""}: $message", cause)

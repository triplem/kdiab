package org.javafreedom.kdiab.analyze.domain.exception

/**
 * Thrown when an upstream service returns an unexpected error response.
 */
class UpstreamException(
    val service: String,
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : RuntimeException("[$service] HTTP $statusCode: $message", cause)

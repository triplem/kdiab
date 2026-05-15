package org.javafreedom.kdiab.nightscout.domain.exception

class UpstreamException(
    val service: String,
    val statusCode: Int,
    val reason: String,
    val responseBody: String? = null,
    val url: String? = null,
) : RuntimeException("Upstream service '$service' returned $statusCode at $url: $reason")

package org.javafreedom.kdiab.common.plugins

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val code: Int, val message: String, val correlationId: String? = null)

@Serializable
data class RateLimitErrorResponse(val code: String, val message: String)

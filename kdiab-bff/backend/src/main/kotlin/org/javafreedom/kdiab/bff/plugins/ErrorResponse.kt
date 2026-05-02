package org.javafreedom.kdiab.bff.plugins

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val code: Int, val message: String)

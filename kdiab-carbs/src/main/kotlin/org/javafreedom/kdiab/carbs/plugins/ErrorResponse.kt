package org.javafreedom.kdiab.carbs.plugins

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val code: Int, val message: String)

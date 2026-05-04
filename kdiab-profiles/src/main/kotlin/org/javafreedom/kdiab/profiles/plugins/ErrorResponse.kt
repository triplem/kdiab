package org.javafreedom.kdiab.profiles.plugins

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val code: Int, val message: String)

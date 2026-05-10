package org.javafreedom.kdiab.common.plugins

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.SerializationException
import org.javafreedom.kdiab.common.domain.exception.AuthenticationException
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException

private val logger = KotlinLogging.logger {}

fun Application.configureStatusPages(
    extraHandlers: StatusPagesConfig.() -> Unit = {}
) {
    install(StatusPages) {
        exception<AuthenticationException> { call, cause ->
            logger.warn(cause) { "Authentication failure" }
            val status = HttpStatusCode.Unauthorized
            call.respond(status, ErrorResponse(status.value, cause.message ?: "Unauthorized"))
        }
        exception<AuthorizationException> { call, cause ->
            logger.warn(cause) { "Authorization failure" }
            val status = HttpStatusCode.Forbidden
            call.respond(status, ErrorResponse(status.value, cause.message ?: "Forbidden"))
        }
        exception<ConflictException> { call, cause ->
            logger.warn(cause) { "Conflict on resource" }
            val status = HttpStatusCode.Conflict
            call.respond(status, ErrorResponse(status.value, cause.message ?: "Conflict"))
        }
        exception<ResourceNotFoundException> { call, cause ->
            logger.debug(cause) { "Resource not found" }
            val status = HttpStatusCode.NotFound
            call.respond(status, ErrorResponse(status.value, cause.message ?: "Not Found"))
        }
        exception<BusinessValidationException> { call, cause ->
            logger.warn(cause) { "Business validation failure" }
            val status = HttpStatusCode.BadRequest
            call.respond(status, ErrorResponse(status.value, cause.message ?: "Bad Request"))
        }
        exception<IllegalArgumentException> { call, cause ->
            logger.warn(cause) { "Illegal argument" }
            val status = HttpStatusCode.BadRequest
            call.respond(status, ErrorResponse(status.value, cause.message ?: "Invalid Argument"))
        }
        exception<SerializationException> { call, cause ->
            logger.warn(cause) { "Request deserialization failure" }
            val status = HttpStatusCode.BadRequest
            call.respond(status, ErrorResponse(status.value, "Invalid request body"))
        }
        extraHandlers()
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled internal server error" }
            val status = HttpStatusCode.InternalServerError
            call.respond(status, ErrorResponse(status.value, "Internal Server Error"))
        }
    }
}

package org.javafreedom.kdiab.profiles.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.javafreedom.kdiab.profiles.domain.exception.AuthenticationException
import org.javafreedom.kdiab.profiles.domain.exception.AuthorizationException
import org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.profiles.domain.exception.ConflictException
import org.javafreedom.kdiab.profiles.domain.exception.ResourceNotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException

private val logger = KotlinLogging.logger {}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<AuthenticationException> { call, cause ->
            logger.warn(cause) { "Authentication failure" }
            call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(
                            HttpStatusCode.Unauthorized.value,
                            cause.message ?: "Unauthorized"
                    )
            )
        }
        exception<AuthorizationException> { call, cause ->
            logger.warn(cause) { "Authorization failure" }
            call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse(HttpStatusCode.Forbidden.value, cause.message ?: "Forbidden")
            )
        }
        exception<ConflictException> { call, cause ->
            logger.warn(cause) { "Conflict on resource" }
            call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse(HttpStatusCode.Conflict.value, cause.message ?: "Conflict")
            )
        }
        exception<ResourceNotFoundException> { call, cause ->
            logger.debug(cause) { "Resource not found" }
            call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(HttpStatusCode.NotFound.value, cause.message ?: "Not Found")
            )
        }
        exception<BusinessValidationException> { call, cause ->
            logger.warn(cause) { "Business validation failure" }
            call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(HttpStatusCode.BadRequest.value, cause.message ?: "Bad Request")
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            logger.warn(cause) { "Illegal argument" }
            call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                            HttpStatusCode.BadRequest.value,
                            cause.message ?: "Invalid Argument"
                    )
            )
        }
        // Unique-constraint violation from the database (e.g. IDX_PROFILES_USER_ACTIVE).
        // SQL state 23505 is the standard UNIQUE VIOLATION code across PostgreSQL and other JDBC drivers.
        exception<ExposedSQLException> { call, cause ->
            val sqlState = cause.cause?.let { (it as? java.sql.SQLException)?.sqlState }
            if (sqlState == "23505") {
                logger.warn(cause) { "Unique constraint violation" }
                val message = when {
                    cause.message?.contains("idx_profiles_user_active", ignoreCase = true) == true ->
                        "This user already has an active profile. Deactivate or archive it before activating another."
                    else -> "A duplicate record already exists."
                }
                call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse(HttpStatusCode.Conflict.value, message)
                )
            } else {
                logger.error(cause) { "Unexpected database error" }
                call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(HttpStatusCode.InternalServerError.value, "Internal Server Error")
                )
            }
        }
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled internal server error" }
            call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(HttpStatusCode.InternalServerError.value, "Internal Server Error")
            )
        }
    }
}

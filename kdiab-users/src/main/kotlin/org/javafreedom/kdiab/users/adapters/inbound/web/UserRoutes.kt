@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.adapters.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.application.service.UserService

private val logger = KotlinLogging.logger {}

private const val JWT_BACKED_NOTE =
    "Glucose unit and weight unit changes take effect on your next login."
private const val DEFAULT_PAGE_SIZE = 20

fun Route.userRoutes(userService: UserService) {
    authenticate("auth-jwt") {
        selfRoutes(userService)
        adminRoutes(userService)
    }
}

private fun Route.selfRoutes(userService: UserService) {
    get("/users/me") {
        val principal = call.principal<UserPrincipal>()!!
        val user = userService.getMe(principal)
        call.respond(user.toResponse())
    }

    patch("/users/me/settings") {
        val principal = call.principal<UserPrincipal>()!!
        val req = call.receive<PatchSettingsRequest>()
        val patch = req.toPatch()
        val updated = userService.updateMySettings(principal, patch)
        val note = if (req.glucoseUnit != null || req.weightUnit != null) JWT_BACKED_NOTE else null
        call.respond(updated.toResponse(note))
    }
}

private fun Route.adminRoutes(userService: UserService) {
    get("/users") {
        val principal = call.principal<UserPrincipal>()!!
        val search = call.request.queryParameters["search"]
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
        val size = call.request.queryParameters["size"]?.toIntOrNull() ?: DEFAULT_PAGE_SIZE
        val users = userService.listUsers(principal, search, page, size)
        call.respond(users.map { it.toResponse() })
    }

    post("/users") {
        val principal = call.principal<UserPrincipal>()!!
        val req = call.receive<CreateUserRequest>()
        val role = runCatching { Role.valueOf(req.role.uppercase()) }.getOrElse {
            throw BusinessValidationException("Invalid role: ${req.role}")
        }
        val user = userService.createUser(principal, req.email, req.displayName, req.password, role)
        call.response.header(HttpHeaders.Location, "/api/v1/users/${user.userId}")
        call.respond(HttpStatusCode.Created, user.toResponse())
    }

    get("/users/{userId}") {
        val principal = call.principal<UserPrincipal>()!!
        val userId = parseUuid(call.parameters["userId"]!!)
        val user = userService.getUser(principal, userId)
        call.respond(user.toResponse())
    }

    patch("/users/{userId}") {
        val principal = call.principal<UserPrincipal>()!!
        val userId = parseUuid(call.parameters["userId"]!!)
        val req = call.receive<UpdateUserRequest>()
        val role = req.role?.let {
            runCatching { Role.valueOf(it.uppercase()) }.getOrElse {
                throw BusinessValidationException("Invalid role: $it")
            }
        }
        val user = userService.updateUser(principal, userId, req.displayName, role)
        call.respond(user.toResponse())
    }

    delete("/users/{userId}") {
        val principal = call.principal<UserPrincipal>()!!
        val userId = parseUuid(call.parameters["userId"]!!)
        userService.deleteUser(principal, userId)
        call.respond(HttpStatusCode.NoContent)
    }
}

internal fun parseUuid(value: String): Uuid =
    runCatching { Uuid.parse(value) }.getOrElse {
        throw BusinessValidationException("Invalid UUID format: $value")
    }

package org.javafreedom.kdiab.users.adapters.inbound.web

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.javafreedom.kdiab.users.application.service.RegistrationService

fun Route.registrationRoutes(registrationService: RegistrationService) {
    post("/register") {
        val req = call.receive<RegisterRequest>()
        val userId = registrationService.register(req.email, req.displayName, req.password)
        call.response.header(HttpHeaders.Location, "/api/v1/users/$userId")
        call.respond(
            HttpStatusCode.Created,
            RegisterResponse(
                userId = userId.toString(),
                message = "Registration successful. An admin will review your account if approval is required.",
            )
        )
    }
}

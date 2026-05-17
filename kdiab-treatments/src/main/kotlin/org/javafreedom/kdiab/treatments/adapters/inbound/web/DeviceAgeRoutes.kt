@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.adapters.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.treatments.application.service.TreatmentService

private val logger = KotlinLogging.logger {}

@Serializable
data class DeviceAgeResponse(
    val catheterChangedAt: String?,
    val reservoirChangedAt: String?,
    val sensorInsertedAt: String?,
    val batteryChangedAt: String?,
)

fun Route.deviceAgeRoutes(treatmentService: TreatmentService) {
    authenticate("auth-jwt") {
        getDeviceAge(treatmentService)
    }
}

private fun Route.getDeviceAge(treatmentService: TreatmentService) {
    get("/users/{userId}/device-age") {
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(call.parameters["userId"] ?: "")
        checkAccess(principal, targetUserId)

        val deviceAge = treatmentService.getDeviceAge(targetUserId)
        logger.debug { "Returning device age for user $targetUserId" }
        call.respond(HttpStatusCode.OK, DeviceAgeResponse(
            catheterChangedAt = deviceAge.catheterChangedAt?.toString(),
            reservoirChangedAt = deviceAge.reservoirChangedAt?.toString(),
            sensorInsertedAt = deviceAge.sensorInsertedAt?.toString(),
            batteryChangedAt = deviceAge.batteryChangedAt?.toString(),
        ))
    }
}

@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.adapters.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.treatments.application.service.DeviceStatusService
import org.javafreedom.kdiab.treatments.api.models.DeviceStatusResponse

private val logger = KotlinLogging.logger {}

fun Route.deviceStatusRoutes(deviceStatusService: DeviceStatusService) {
    authenticate("auth-jwt") {
        getLatestDeviceStatus(deviceStatusService)
    }
}

private fun Route.getLatestDeviceStatus(deviceStatusService: DeviceStatusService) {
    get("/users/{userId}/device-status/latest") {
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(call.parameters["userId"] ?: "")
        checkAccess(principal, targetUserId)

        val status = deviceStatusService.getLatestDeviceStatus(targetUserId)
            ?: throw ResourceNotFoundException("No device status found for user $targetUserId")

        logger.debug { "Returning latest device status for user $targetUserId recorded at ${status.recordedAt}" }
        call.respond(HttpStatusCode.OK, DeviceStatusResponse(
            id = status.id.toString(),
            userId = status.userId.toString(),
            recordedAt = status.recordedAt.toString(),
            device = status.device,
            pumpName = status.pumpName,
            reservoirUnits = status.reservoirUnits,
            batteryLevel = status.batteryLevel,
            pumpConnected = status.pumpConnected,
        ))
    }
}


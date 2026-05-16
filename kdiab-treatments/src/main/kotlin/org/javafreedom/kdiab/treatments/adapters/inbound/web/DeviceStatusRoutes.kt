@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.adapters.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
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
        val targetUserId = parseDeviceStatusUuid(call.parameters["userId"] ?: "")
        checkDeviceStatusAccess(principal, targetUserId)

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

private fun parseDeviceStatusUuid(value: String): Uuid =
    runCatching { Uuid.parse(value) }.getOrElse {
        throw BusinessValidationException("Invalid UUID format: $value")
    }

private fun checkDeviceStatusAccess(principal: UserPrincipal?, targetUserId: Uuid) {
    if (principal == null || !principal.canAccess(targetUserId)) {
        logger.warn {
            "Access denied: principalId=${principal?.userId} " +
            "roles=${principal?.roles} " +
            "targetUserId=$targetUserId"
        }
        throw AuthorizationException("Access Not Authorized")
    }
}

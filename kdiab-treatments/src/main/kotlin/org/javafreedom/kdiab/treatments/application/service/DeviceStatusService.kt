@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.application.service

import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.javafreedom.kdiab.treatments.domain.model.DeviceStatus
import org.javafreedom.kdiab.treatments.domain.repository.DeviceStatusRepository
import kotlin.time.Instant

class DeviceStatusService(
    private val deviceStatusRepository: DeviceStatusRepository
) {
    suspend fun saveDeviceStatus(userId: Uuid, recordedAt: Instant, data: JsonObject): DeviceStatus {
        val status = DeviceStatus(
            id = Uuid.random(),
            userId = userId,
            recordedAt = recordedAt,
            createdAt = Clock.System.now(),
            device = (data["device"] as? JsonPrimitive)?.content ?: "unknown",
            pumpName = (data["pumpName"] as? JsonPrimitive)?.content,
            reservoirUnits = (data["reservoirUnits"] as? JsonPrimitive)?.doubleOrNull,
            batteryLevel = (data["batteryLevel"] as? JsonPrimitive)?.intOrNull,
            pumpConnected = (data["pumpConnected"] as? JsonPrimitive)?.booleanOrNull,
        )
        return deviceStatusRepository.save(status)
    }

    suspend fun getLatestDeviceStatus(userId: Uuid): DeviceStatus? =
        deviceStatusRepository.findLatestByUserId(userId)
}

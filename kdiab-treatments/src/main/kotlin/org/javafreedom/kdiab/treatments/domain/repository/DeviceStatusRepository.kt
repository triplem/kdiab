@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.domain.repository

import kotlin.uuid.Uuid
import org.javafreedom.kdiab.treatments.domain.model.DeviceStatus

interface DeviceStatusRepository {
    suspend fun save(deviceStatus: DeviceStatus): DeviceStatus
    suspend fun findLatestByUserId(userId: Uuid): DeviceStatus?
}

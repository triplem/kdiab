@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class DeviceStatus(
    val id: Uuid,
    val userId: Uuid,
    val recordedAt: Instant,
    val createdAt: Instant,
    val device: String,
    val pumpName: String? = null,
    val reservoirUnits: Double? = null,
    val batteryLevel: Int? = null,
    val pumpConnected: Boolean? = null,
)

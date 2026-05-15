@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class UserSettings(
    val userId: Uuid,
    val timezone: String = "UTC",
    val language: String = "en",
    val timeFormat: Int = 24,
    val glucoseUnit: String = "mg/dL",
    val weightUnit: String = "kg",
    val alarmUrgentHigh: Int? = 260,
    val alarmHigh: Int? = 200,
    val alarmLow: Int? = 75,
    val alarmUrgentLow: Int? = 55,
    val createdAt: Instant,
    val updatedAt: Instant,
)

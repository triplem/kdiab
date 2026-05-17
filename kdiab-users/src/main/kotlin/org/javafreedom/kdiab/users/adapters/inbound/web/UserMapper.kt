@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.adapters.inbound.web

import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.users.application.service.SettingsPatch
import org.javafreedom.kdiab.users.domain.model.DoctorPatientRelation
import org.javafreedom.kdiab.users.domain.model.User
import org.javafreedom.kdiab.users.domain.model.UserSettings

@Serializable
data class UserResponse(
    val userId: String,
    val email: String,
    val displayName: String,
    val roles: List<String>,
    val settings: UserSettingsResponse?,
)

@Serializable
data class UserSettingsResponse(
    val timezone: String,
    val language: String,
    val timeFormat: Int,
    val glucoseUnit: String,
    val weightUnit: String,
    val alarmUrgentHigh: Int?,
    val alarmHigh: Int?,
    val alarmLow: Int?,
    val alarmUrgentLow: Int?,
    val sensorDurationHours: Int,
    val updatedAt: String,
    val jwtBackedNote: String? = null,
)

@Serializable
data class CreateUserRequest(
    val email: String,
    val displayName: String,
    val password: String,
    val role: String,
)

@Serializable
data class UpdateUserRequest(
    val displayName: String? = null,
    val role: String? = null,
)

@Serializable
data class PatchSettingsRequest(
    val timezone: String? = null,
    val language: String? = null,
    val timeFormat: Int? = null,
    val glucoseUnit: String? = null,
    val weightUnit: String? = null,
    val alarmUrgentHigh: Int? = null,
    val alarmHigh: Int? = null,
    val alarmLow: Int? = null,
    val alarmUrgentLow: Int? = null,
    val sensorDurationHours: Int? = null,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val displayName: String,
    val password: String,
)

@Serializable
data class RegisterResponse(
    val userId: String,
    val message: String,
)

@Serializable
data class DoctorPatientResponse(
    val doctorId: String,
    val patientId: String,
    val createdAt: String,
)

@Serializable
data class AssignPatientRequest(
    val patientId: String,
)

fun User.toResponse(): UserResponse = UserResponse(
    userId = userId.toString(),
    email = email,
    displayName = displayName,
    roles = roles.map { it.name },
    settings = settings?.toResponse(),
)

fun UserSettings.toResponse(jwtBackedNote: String? = null): UserSettingsResponse = UserSettingsResponse(
    timezone = timezone,
    language = language,
    timeFormat = timeFormat,
    glucoseUnit = glucoseUnit,
    weightUnit = weightUnit,
    alarmUrgentHigh = alarmUrgentHigh,
    alarmHigh = alarmHigh,
    alarmLow = alarmLow,
    alarmUrgentLow = alarmUrgentLow,
    sensorDurationHours = sensorDurationHours,
    updatedAt = updatedAt.toString(),
    jwtBackedNote = jwtBackedNote,
)

fun PatchSettingsRequest.toPatch() = SettingsPatch(
    timezone = timezone,
    language = language,
    timeFormat = timeFormat,
    glucoseUnit = glucoseUnit,
    weightUnit = weightUnit,
    alarmUrgentHigh = alarmUrgentHigh,
    alarmHigh = alarmHigh,
    alarmLow = alarmLow,
    alarmUrgentLow = alarmUrgentLow,
    sensorDurationHours = sensorDurationHours,
)

fun DoctorPatientRelation.toResponse() = DoctorPatientResponse(
    doctorId = doctorId.toString(),
    patientId = patientId.toString(),
    createdAt = createdAt.toString(),
)

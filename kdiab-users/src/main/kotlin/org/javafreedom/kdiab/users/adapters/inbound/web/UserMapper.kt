@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.adapters.inbound.web

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.users.application.service.SettingsPatch
import org.javafreedom.kdiab.users.domain.model.AlarmThresholds
import org.javafreedom.kdiab.users.domain.model.DiabetesProfile
import org.javafreedom.kdiab.users.domain.model.DoctorPatientRelation
import org.javafreedom.kdiab.users.domain.model.LocalePreferences
import org.javafreedom.kdiab.users.domain.model.UnitPreferences
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
data class LocalePreferencesResponse(
    val timezone: String,
    val language: String,
    val timeFormat: Int,
)

@Serializable
data class UnitPreferencesResponse(
    val glucoseUnit: String,
    val weightUnit: String,
)

@Serializable
data class AlarmThresholdsResponse(
    val urgentHigh: Int,
    val high: Int,
    val low: Int,
    val urgentLow: Int,
)

@Serializable
data class DiabetesProfileResponse(
    val sensorDurationHours: Int,
    val diabetesSince: Int?,
    val carbAbsorptionRateGPerHour: Double?,
)

@Serializable
data class UserSettingsResponse(
    val birthday: String?,
    val locale: LocalePreferencesResponse,
    val units: UnitPreferencesResponse,
    val alarms: AlarmThresholdsResponse?,
    val diabetes: DiabetesProfileResponse,
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
data class LocalePreferencesPatch(
    val timezone: String? = null,
    val language: String? = null,
    val timeFormat: Int? = null,
)

@Serializable
data class UnitPreferencesPatch(
    val glucoseUnit: String? = null,
    val weightUnit: String? = null,
)

@Serializable
data class AlarmThresholdsPatch(
    val urgentHigh: Int? = null,
    val high: Int? = null,
    val low: Int? = null,
    val urgentLow: Int? = null,
)

@Serializable
data class DiabetesProfilePatch(
    val sensorDurationHours: Int? = null,
    val diabetesSince: Int? = null,
    val carbAbsorptionRateGPerHour: Double? = null,
)

@Serializable
data class PatchSettingsRequest(
    val birthday: String? = null,
    val locale: LocalePreferencesPatch? = null,
    val units: UnitPreferencesPatch? = null,
    val alarms: AlarmThresholdsPatch? = null,
    val diabetes: DiabetesProfilePatch? = null,
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
    birthday = birthday?.toString(),
    locale = LocalePreferencesResponse(
        timezone = locale.timezone,
        language = locale.language,
        timeFormat = locale.timeFormat,
    ),
    units = UnitPreferencesResponse(
        glucoseUnit = units.glucoseUnit,
        weightUnit = units.weightUnit,
    ),
    alarms = alarms?.let { a ->
        AlarmThresholdsResponse(
            urgentHigh = a.urgentHigh,
            high = a.high,
            low = a.low,
            urgentLow = a.urgentLow,
        )
    },
    diabetes = DiabetesProfileResponse(
        sensorDurationHours = diabetes.sensorDurationHours,
        diabetesSince = diabetes.diabetesSince,
        carbAbsorptionRateGPerHour = diabetes.carbAbsorptionRateGPerHour,
    ),
    updatedAt = updatedAt.toString(),
    jwtBackedNote = jwtBackedNote,
)

fun PatchSettingsRequest.toPatch() = SettingsPatch(
    birthday = birthday?.let { LocalDate.parse(it) },
    timezone = locale?.timezone,
    language = locale?.language,
    timeFormat = locale?.timeFormat,
    glucoseUnit = units?.glucoseUnit,
    weightUnit = units?.weightUnit,
    alarmUrgentHigh = alarms?.urgentHigh,
    alarmHigh = alarms?.high,
    alarmLow = alarms?.low,
    alarmUrgentLow = alarms?.urgentLow,
    sensorDurationHours = diabetes?.sensorDurationHours,
    diabetesSince = diabetes?.diabetesSince,
    carbAbsorptionRateGPerHour = diabetes?.carbAbsorptionRateGPerHour,
)

fun DoctorPatientRelation.toResponse() = DoctorPatientResponse(
    doctorId = doctorId.toString(),
    patientId = patientId.toString(),
    createdAt = createdAt.toString(),
)

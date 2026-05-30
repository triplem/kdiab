@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate

data class LocalePreferences(
    val timezone: String = "UTC",
    val language: String = "en",
    val timeFormat: Int = 24,
)

data class UnitPreferences(
    val glucoseUnit: String = "mg/dL",
    val weightUnit: String = "kg",
)

data class AlarmThresholds(
    val urgentHigh: Int,
    val high: Int,
    val low: Int,
    val urgentLow: Int,
) {
    companion object {
        private const val THRESHOLD_COUNT = 4

        fun fromNullable(urgentHigh: Int?, high: Int?, low: Int?, urgentLow: Int?): AlarmThresholds? {
            val values = listOfNotNull(urgentHigh, high, low, urgentLow)
            if (values.size < THRESHOLD_COUNT) return null
            @Suppress("MagicNumber") // positional indices into the 4-element threshold list
            return AlarmThresholds(values[0], values[1], values[2], values[3])
        }
    }
}

data class DiabetesProfile(
    val sensorDurationHours: Int = 240,
    val diabetesSince: Int? = null,
    val carbAbsorptionRateGPerHour: Double? = null,
)

data class UserSettings(
    val userId: Uuid,
    val createdAt: Instant,
    val updatedAt: Instant,
    val birthday: LocalDate? = null,         // PII — never log this field
    val locale: LocalePreferences = LocalePreferences(),
    val units: UnitPreferences = UnitPreferences(),
    val alarms: AlarmThresholds? = null,
    val diabetes: DiabetesProfile = DiabetesProfile(),
)

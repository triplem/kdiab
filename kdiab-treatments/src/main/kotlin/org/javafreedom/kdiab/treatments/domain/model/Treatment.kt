@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject

enum class TreatmentType {
    BOLUS,
    BASAL,
    CARBS,
    CORRECTION_BOLUS,
    COMBO_BOLUS,
    TEMP_BASAL,
    EXERCISE,
    NOTE,
    PUMP_SUSPEND,
    SITE_CHANGE,
    SENSOR_INSERT,
    INSULIN_CHANGE,
    ACTIVITY,
    HYPO_TREATMENT,
}

enum class TreatmentStatus {
    ACTIVE,
    ARCHIVED
}

data class Treatment(
    val id: Uuid,
    val userId: Uuid,
    val treatedAt: Instant,
    val createdAt: Instant,
    val type: TreatmentType,
    val data: JsonObject,
    val notes: String? = null,
    val status: TreatmentStatus = TreatmentStatus.ACTIVE,
)

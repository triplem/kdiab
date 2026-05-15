package org.javafreedom.kdiab.nightscout.adapters.inbound.web

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.nightscout.domain.model.NightscoutEntry
import org.javafreedom.kdiab.nightscout.domain.model.NightscoutTreatment

private val CGM_TYPES = setOf("CGM", "BGM")

private val TREATMENT_TYPE_MAP = mapOf(
    "BOLUS" to "Bolus",
    "CORRECTION_BOLUS" to "Correction Bolus",
    "COMBO_BOLUS" to "Combo Bolus",
    "BASAL" to "Temp Basal",
    "TEMP_BASAL" to "Temp Basal",
    "CARBS" to "Carbs",
    "EXERCISE" to "Exercise",
    "NOTE" to "Note",
    "BG_CHECK" to "BG Check",
    "PUMP_SUSPEND" to "Suspend Pump",
    "SITE_CHANGE" to "Site Change",
    "SENSOR_INSERT" to "Sensor Start",
    "INSULIN_CHANGE" to "Insulin Change",
)

fun MeasureResponse.toNightscoutEntry(): NightscoutEntry? {
    val typeValue = type.value
    if (typeValue !in CGM_TYPES) return null
    val millis = runCatching { Instant.parse(measuredAt).toEpochMilliseconds() }.getOrNull() ?: return null
    val entryType = if (typeValue == "CGM") "sgv" else "mbg"
    val data = this.data as? JsonObject
    val sgv = data?.get("sgv")?.jsonPrimitive?.runCatching { int }?.getOrNull()
        ?: data?.get("value")?.jsonPrimitive?.runCatching { int }?.getOrNull()
    val trend = data?.get("trend")?.jsonPrimitive?.runCatching { int }?.getOrNull()
    val direction = data?.get("direction")?.jsonPrimitive?.content

    return NightscoutEntry(
        type = entryType,
        sgv = sgv,
        date = millis,
        dateString = measuredAt,
        trend = trend,
        direction = direction,
        id = id,
        mills = millis,
    )
}

fun TreatmentResponse.toNightscoutTreatment(): NightscoutTreatment? {
    val millis = runCatching { Instant.parse(treatedAt).toEpochMilliseconds() }.getOrNull() ?: return null
    val nsEventType = TREATMENT_TYPE_MAP[type.value] ?: return null
    val data = this.data as? JsonObject

    val insulin = data?.get("insulin")?.jsonPrimitive?.runCatching { double }?.getOrNull()
        ?: data?.get("units")?.jsonPrimitive?.runCatching { double }?.getOrNull()
    val carbs = data?.get("carbs")?.jsonPrimitive?.runCatching { double }?.getOrNull()

    return NightscoutTreatment(
        id = id,
        eventType = nsEventType,
        createdAt = treatedAt,
        timestamp = treatedAt,
        insulin = insulin,
        carbs = carbs,
        notes = this.notes,
        mills = millis,
    )
}

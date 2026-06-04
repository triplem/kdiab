package org.javafreedom.kdiab.nightscout.adapters.inbound.web

import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.CreateMeasureRequest
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureSource
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureType
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.CreateTreatmentRequest
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentType
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
    val data = this.data
    val sgv = data["sgv"]?.jsonPrimitive?.runCatching { int }?.getOrNull()
        ?: data["value"]?.jsonPrimitive?.runCatching { int }?.getOrNull()
    val trend = data["trend"]?.jsonPrimitive?.runCatching { int }?.getOrNull()
    val direction = data["direction"]?.jsonPrimitive?.content

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
    val data = this.data

    val insulin = data["insulin"]?.jsonPrimitive?.runCatching { double }?.getOrNull()
        ?: data["units"]?.jsonPrimitive?.runCatching { double }?.getOrNull()
    val carbs = data["carbs"]?.jsonPrimitive?.runCatching { double }?.getOrNull()

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

fun NightscoutEntry.toMeasureRequest(): CreateMeasureRequest? {
    val measureType = when (type) {
        "sgv" -> MeasureType.CGM
        "mbg" -> MeasureType.BGM
        else -> return null
    }
    val data = buildJsonObject {
        sgv?.let { put("sgv", it) }
        trend?.let { put("trend", it) }
        direction?.let { put("direction", it) }
    }
    return CreateMeasureRequest(
        measuredAt = dateString,
        type = measureType,
        source = MeasureSource.NIGHTSCOUT,
        data = data,
    )
}

fun NightscoutTreatment.toTreatmentRequest(): CreateTreatmentRequest? {
    val treatmentType = TREATMENT_TYPE_MAP.entries
        .firstOrNull { it.value == eventType }
        ?.key
        ?.let { runCatching { TreatmentType.valueOf(it) }.getOrNull() }
        ?: return null
    val data = buildJsonObject {
        insulin?.let { put("insulin", it) }
        carbs?.let { put("carbs", it) }
    }
    return CreateTreatmentRequest(
        treatedAt = createdAt,
        type = treatmentType,
        data = data,
        notes = notes,
    )
}

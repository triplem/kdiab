package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.CreateFoodEntryRequest
import org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.FoodEntryResponse
import org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.UpdateFoodEntryRequest
import org.javafreedom.kdiab.nightscout.api.upstream.profiles.models.CreateProfileRequest
import org.javafreedom.kdiab.nightscout.api.upstream.profiles.models.Profile
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.CreateMeasureRequest
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureSource
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureType
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.UpdateMeasureRequest
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.CreateTreatmentRequest
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentType
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.UpdateTreatmentRequest
import org.javafreedom.kdiab.nightscout.domain.model.Ns3BasalSegment
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Entry
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Food
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Profile
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Treatment

private const val DEFAULT_PORTION_GRAMS = 100.0

// Reverse map: kdiab TreatmentType key → Nightscout eventType string
internal val NS3_TREATMENT_TYPE_MAP = mapOf(
    "BOLUS" to "Bolus",
    "CORRECTION_BOLUS" to "Correction Bolus",
    "COMBO_BOLUS" to "Combo Bolus",
    "BASAL" to "Temp Basal",
    "TEMP_BASAL" to "Temp Basal",
    "CARBS" to "Carbs (and/or Bolus)",
    "EXERCISE" to "Exercise",
    "NOTE" to "Note",
    "BG_CHECK" to "BG Check",
    "PUMP_SUSPEND" to "Pump Suspend",
    "SITE_CHANGE" to "Site Change",
    "SENSOR_INSERT" to "Sensor Start",
    "INSULIN_CHANGE" to "Insulin Change",
)

private const val MMOL_TO_MGDL = 18.0
private const val MMOL_ROUND_FACTOR = 10.0

fun MeasureResponse.toNs3Entry(glucoseUnit: String): Ns3Entry {
    val millis = runCatching { Instant.parse(measuredAt).toEpochMilliseconds() }.getOrDefault(0L)
    val rawSgv = data["sgv"]?.jsonPrimitive?.runCatching { int }?.getOrNull()
        ?: data["value"]?.jsonPrimitive?.runCatching { int }?.getOrNull()
    val sgv: Double? = rawSgv?.let {
        if (glucoseUnit == "mmol/L") {
            Math.round(it / MMOL_TO_MGDL * MMOL_ROUND_FACTOR) / MMOL_ROUND_FACTOR
        } else {
            it.toDouble()
        }
    }
    val direction = data["direction"]?.jsonPrimitive?.content
    return Ns3Entry(
        identifier = id,
        date = millis,
        dateString = measuredAt,
        type = if (type.value == "CGM") "sgv" else "mbg",
        sgv = sgv,
        direction = direction,
        srvCreated = millis,
        srvModified = millis,
    )
}

fun Ns3Entry.toCreateMeasureRequest(glucoseUnit: String): CreateMeasureRequest? {
    val measureType = when (type) {
        "sgv" -> MeasureType.CGM
        "mbg" -> MeasureType.BGM
        else -> return null
    }
    val storedSgv = sgv?.let {
        if (glucoseUnit == "mmol/L") (it * MMOL_TO_MGDL).toInt() else it.toInt()
    }
    val data = buildJsonObject {
        storedSgv?.let { put("sgv", it) }
        direction?.let { put("direction", it) }
    }
    return CreateMeasureRequest(
        measuredAt = dateString,
        type = measureType,
        source = MeasureSource.NIGHTSCOUT,
        data = data,
    )
}

fun Ns3Entry.toUpdateMeasureRequest(glucoseUnit: String): UpdateMeasureRequest {
    val storedSgv = sgv?.let {
        if (glucoseUnit == "mmol/L") (it * MMOL_TO_MGDL).toInt() else it.toInt()
    }
    val data = buildJsonObject {
        storedSgv?.let { put("sgv", it) }
        direction?.let { put("direction", it) }
    }
    return UpdateMeasureRequest(measuredAt = dateString, data = data)
}

fun TreatmentResponse.toNs3Treatment(): Ns3Treatment {
    val millis = runCatching { Instant.parse(treatedAt).toEpochMilliseconds() }.getOrDefault(0L)
    val nsEventType = NS3_TREATMENT_TYPE_MAP[type.value] ?: type.value
    val insulin = data["insulin"]?.jsonPrimitive?.runCatching { double }?.getOrNull()
        ?: data["units"]?.jsonPrimitive?.runCatching { double }?.getOrNull()
    val carbs = data["carbs"]?.jsonPrimitive?.runCatching { double }?.getOrNull()
    return Ns3Treatment(
        identifier = id,
        date = millis,
        dateString = treatedAt,
        eventType = nsEventType,
        insulin = insulin,
        carbs = carbs,
        notes = notes,
        srvCreated = millis,
        srvModified = millis,
    )
}

fun Ns3Treatment.toCreateTreatmentRequest(): CreateTreatmentRequest? {
    val treatmentType = NS3_TREATMENT_TYPE_MAP.entries
        .firstOrNull { it.value == eventType }
        ?.key
        ?.let { runCatching { TreatmentType.valueOf(it) }.getOrNull() }
        ?: return null
    val data = buildJsonObject {
        insulin?.let { put("insulin", it) }
        carbs?.let { put("carbs", it) }
    }
    return CreateTreatmentRequest(treatedAt = dateString, type = treatmentType, data = data, notes = notes)
}

fun Ns3Treatment.toUpdateTreatmentRequest(): UpdateTreatmentRequest {
    val data = buildJsonObject {
        insulin?.let { put("insulin", it) }
        carbs?.let { put("carbs", it) }
    }
    return UpdateTreatmentRequest(treatedAt = dateString, data = data, notes = notes)
}

fun FoodEntryResponse.toNs3Food(): Ns3Food {
    val created = runCatching { Instant.parse(createdAt).toEpochMilliseconds() }.getOrNull()
    val modified = runCatching { Instant.parse(updatedAt).toEpochMilliseconds() }.getOrNull()
    return Ns3Food(
        identifier = id,
        name = name,
        carbs = carbsForPortion.toDouble(),
        portionSize = portionGrams.toDouble(),
        srvCreated = created,
        srvModified = modified,
    )
}

fun Ns3Food.toCreateFoodRequest(): CreateFoodEntryRequest {
    val portion = portionSize ?: DEFAULT_PORTION_GRAMS
    val carbsPer100g = carbs / portion * DEFAULT_PORTION_GRAMS
    return CreateFoodEntryRequest(
        name = name,
        portionGrams = java.math.BigDecimal.valueOf(portion),
        carbsPer100g = java.math.BigDecimal.valueOf(carbsPer100g),
    )
}

fun Ns3Food.toUpdateFoodRequest(): UpdateFoodEntryRequest {
    val portion = portionSize ?: DEFAULT_PORTION_GRAMS
    val carbsPer100g = carbs / portion * DEFAULT_PORTION_GRAMS
    return UpdateFoodEntryRequest(
        name = name,
        portionGrams = java.math.BigDecimal.valueOf(portion),
        carbsPer100g = java.math.BigDecimal.valueOf(carbsPer100g),
    )
}

private const val MINUTES_PER_HOUR = 60.0
private const val UNKNOWN_INSULIN_TYPE = "Unknown"

fun Profile.toNs3Profile(): Ns3Profile {
    val nowMs = System.currentTimeMillis()
    val nowIso = Instant.fromEpochMilliseconds(nowMs).toString()
    val startDate = createdAt ?: nowIso
    val createdMs = createdAt?.let {
        runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrDefault(nowMs)
    } ?: nowMs
    val basalSegments = basal?.map { segment ->
        Ns3BasalSegment(time = segment.startTime, value = segment.value)
    } ?: emptyList()
    return Ns3Profile(
        identifier = id,
        defaultProfile = name,
        startDate = startDate,
        units = "mg/dl",
        timeZone = timeZone ?: "UTC",
        dia = durationOfAction / MINUTES_PER_HOUR,
        basalSegments = basalSegments,
        carbratio = emptyList(),
        sens = emptyList(),
        srvCreated = createdMs,
        srvModified = createdMs,
    )
}

fun Ns3Profile.toCreateProfileRequest(): CreateProfileRequest = CreateProfileRequest(
    name = defaultProfile,
    insulinType = UNKNOWN_INSULIN_TYPE,
    durationOfAction = (dia * MINUTES_PER_HOUR).toInt(),
)
